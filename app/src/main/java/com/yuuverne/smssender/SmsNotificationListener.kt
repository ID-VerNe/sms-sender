package com.yuuverne.smssender

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yuuverne.smssender.database.AppDatabase
import com.yuuverne.smssender.database.SmsRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private val knownSmsPackages = setOf(
    "com.android.mms", // AOSP / Xiaomi / Redmi and other OEMs
    "com.android.messaging",
    "com.google.android.apps.messaging", // Google Messages
    "com.samsung.android.messaging", // Samsung Messages
    "com.bbk.message", // Vivo / iQOO
    "com.coloros.mms", // OPPO / Realme
    "com.oneplus.mms" // OnePlus
)

internal fun isSmsPackage(packageName: String, defaultSmsPackage: String?): Boolean {
    if (packageName.isBlank()) return false
    return packageName == defaultSmsPackage || packageName in knownSmsPackages
}

internal fun shouldForwardNotification(isServiceEnabled: Boolean): Boolean = isServiceEnabled

class SmsNotificationListener : NotificationListenerService() {

    private val scope = createNotificationScope(
        dispatcher = Dispatchers.IO,
        exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e("SmsSender_Trace", "短信通知处理发生未捕获异常", throwable)
        }
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val config = ConfigManager(applicationContext)
        if (!shouldForwardNotification(config.isServiceEnabled)) {
            Log.i("SmsSender_Trace", "转发总开关已关闭，忽略通知")
            return
        }

        val packageName = sbn.packageName

        // 结合系统默认短信应用与已知 OEM 包名，避免误转发普通 messaging 应用。
        val defaultSmsPackage = try {
            Telephony.Sms.getDefaultSmsPackage(this)
        } catch (e: Exception) {
            Log.w("SmsSender_Trace", "无法读取默认短信包名，回退到已知 OEM 包名匹配", e)
            null
        }
        if (!isSmsPackage(packageName, defaultSmsPackage)) {
            return
        }

        val notification = sbn.notification
        val flags = notification.flags
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "未知号码"
        val text = selectNotificationText(
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)
        ) ?: return

        val snapshot = NotificationSnapshot(
            packageName = packageName,
            key = sbn.key,
            title = title,
            text = text,
            postedAt = sbn.postTime,
            isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = flags and Notification.FLAG_ONGOING_EVENT != 0,
            isNoClear = flags and Notification.FLAG_NO_CLEAR != 0,
            isForegroundService = flags and Notification.FLAG_FOREGROUND_SERVICE != 0
        )

        if (!SmsNotificationPolicy.shouldForward(snapshot)) return

        Log.i("SmsSender_Trace", "【通知捕获】来自: $title, 内容: $text")

        scope.launch {
            forwardIfNew(snapshot)
        }
    }

    private suspend fun forwardIfNew(snapshot: NotificationSnapshot) {
        val config = ConfigManager(applicationContext)
        val db = AppDatabase.getDatabase(applicationContext)
        val smsDao = db.smsDao()
        val enabledRules = db.ruleDao().getEnabledRules()

        val targetEmails = linkedSetOf<String>()
        config.receiverEmail.trim().takeIf { it.isNotEmpty() }?.let(targetEmails::add)
        enabledRules.forEach { rule ->
            if (snapshot.text.contains(rule.keyword, ignoreCase = true)) {
                rule.targetEmail.trim().takeIf { it.isNotEmpty() }?.let(targetEmails::add)
            }
        }

        if (targetEmails.isEmpty()) {
            Log.w("SmsSender_Trace", "无有效收件人邮箱，取消转发")
            return
        }

        val smartSubject = ExtractionUtils.getSmartSubject(config, snapshot.title, snapshot.text)
        val mailContent = "检测到系统通知栏短信：\n来自：${snapshot.title}\n内容：${snapshot.text}\n\n(提示：这是通过监听通知栏获取的内容，可绕过系统验证码读取限制)\n\n——来自SmsSender"

        targetEmails.forEach { email ->
            val record = SmsRecord(
                smsId = SmsNotificationPolicy.notificationId(snapshot, email),
                address = snapshot.title,
                body = snapshot.text,
                timestamp = snapshot.postedAt
            )
            val workName = SmsNotificationPolicy.uniqueWorkName(snapshot, email)

            try {
                val enqueued = enqueueNotificationIfNew(smsDao, record) {
                    MailForwardWorker.enqueueUnique(
                        context = applicationContext,
                        workName = workName,
                        recipient = email,
                        subject = smartSubject,
                        content = mailContent
                    )
                }
                if (enqueued) {
                    Log.i("SmsSender_Trace", "转发至 $email 已加入发送队列")
                } else {
                    Log.i("SmsSender_Trace", "通知已转发至 $email，跳过重复回调")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SmsSender_Trace", "转发至 $email 入队失败，未记录去重标记", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

internal fun selectNotificationText(
    bigText: CharSequence?,
    textLines: Array<out CharSequence>?,
    text: CharSequence?
): String? {
    bigText?.toString()?.takeIf { it.isNotBlank() }?.let { return it }

    textLines
        ?.joinToString("\n") { it.toString() }
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return text?.toString()?.takeIf { it.isNotBlank() }
}

internal fun createNotificationScope(
    dispatcher: CoroutineDispatcher,
    exceptionHandler: CoroutineExceptionHandler
): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
