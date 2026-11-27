package com.yuuverne.smssender

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yuuverne.smssender.database.AppDatabase
import com.yuuverne.smssender.database.SmsRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SmsNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingMutex = Mutex()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val flags = sbn.notification.flags
        val extras = sbn.notification.extras
        val snapshot = NotificationSnapshot(
            packageName = sbn.packageName,
            key = sbn.key,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "未知号码",
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postedAt = sbn.postTime,
            isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = flags and Notification.FLAG_ONGOING_EVENT != 0,
            isNoClear = flags and Notification.FLAG_NO_CLEAR != 0,
            isForegroundService = flags and Notification.FLAG_FOREGROUND_SERVICE != 0
        )

        if (!SmsNotificationPolicy.shouldForward(snapshot)) return

        Log.i("SmsSender_Trace", "【通知捕获】来自: ${snapshot.title}, 内容: ${snapshot.text}")

        scope.launch {
            processingMutex.withLock {
                forwardIfNew(snapshot)
            }
        }
    }

    private suspend fun forwardIfNew(snapshot: NotificationSnapshot) {
        val config = ConfigManager(applicationContext)
        val db = AppDatabase.getDatabase(applicationContext)
        val smsDao = db.smsDao()
        val notificationId = SmsNotificationPolicy.notificationId(snapshot)
        val duplicates = smsDao.findPotentialDuplicates(
            id = notificationId,
            address = snapshot.title,
            body = snapshot.text,
            timestamp = snapshot.postedAt
        ).map { record ->
            ForwardedNotification(
                id = record.smsId,
                address = record.address,
                body = record.body,
                timestamp = record.timestamp
            )
        }

        if (SmsNotificationPolicy.isDuplicate(snapshot, duplicates)) {
            Log.i("SmsSender_Trace", "通知已转发，跳过重复回调")
            return
        }

        val enabledRules = db.ruleDao().getEnabledRules()

        // 构建待发送邮箱列表 (去重)
        val targetEmails = mutableSetOf<String>()
        if (config.receiverEmail.isNotEmpty()) {
            targetEmails.add(config.receiverEmail)
        }

        // 匹配关键词规则
        enabledRules.forEach { rule ->
            if (snapshot.text.contains(rule.keyword, ignoreCase = true)) {
                targetEmails.add(rule.targetEmail)
            }
        }

        if (targetEmails.isEmpty()) {
            Log.w("SmsSender_Trace", "无有效收件人邮箱，取消转发")
            return
        }

        val smartSubject = ExtractionUtils.getSmartSubject(config, snapshot.title, snapshot.text)
        var allSendsSucceeded = true

        targetEmails.forEach { email ->
            MailSender.sendEmail(
                config = config,
                subject = smartSubject,
                content = "检测到系统通知栏短信：\n来自：${snapshot.title}\n内容：${snapshot.text}\n\n(提示：这是通过监听通知栏获取的内容，可绕过系统验证码读取限制)\n\n——来自SmsSender",
                targetOverride = email,
                onSuccess = {
                    Log.i("SmsSender_Trace", "转发至 $email 成功")
                },
                onFailure = { e ->
                    allSendsSucceeded = false
                    Log.e("SmsSender_Trace", "转发至 $email 失败: ${e.message}")
                }
            )
        }

        if (!allSendsSucceeded) {
            Log.w("SmsSender_Trace", "存在发送失败，未记录转发标记，允许后续重试")
            return
        }

        smsDao.insert(
            SmsRecord(
                smsId = notificationId,
                address = snapshot.title,
                body = snapshot.text,
                timestamp = snapshot.postedAt
            )
        )
        Log.i("SmsSender_Trace", "转发完成，已记录通知去重指纹")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
