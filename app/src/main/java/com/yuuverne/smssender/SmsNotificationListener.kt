package com.yuuverne.smssender

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
        
        // 过滤短信应用包名
        if (!packageName.contains("mms") && !packageName.contains("messaging")) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "未知号码"
        val text = selectNotificationText(
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)
        ) ?: return

        Log.i("SmsSender_Trace", "【通知捕获】来自: $title, 内容: $text")

        // 转发邮件逻辑
        scope.launch {
            val db = com.yuuverne.smssender.database.AppDatabase.getDatabase(applicationContext)
            val enabledRules = db.ruleDao().getEnabledRules()
            
            // 构建待发送邮箱列表 (去重)
            val targetEmails = mutableSetOf<String>()
            if (config.receiverEmail.isNotEmpty()) {
                targetEmails.add(config.receiverEmail)
            }
            
            // 匹配关键词规则
            enabledRules.forEach { rule ->
                if (text.contains(rule.keyword, ignoreCase = true)) {
                    targetEmails.add(rule.targetEmail)
                }
            }

            if (targetEmails.isEmpty()) {
                Log.w("SmsSender_Trace", "无有效收件人邮箱，取消转发")
                return@launch
            }

            val smartSubject = ExtractionUtils.getSmartSubject(config, title, text)
            
            targetEmails.forEach { email ->
                MailSender.sendEmail(
                    config = config,
                    subject = smartSubject,
                    content = "检测到系统通知栏短信：\n来自：$title\n内容：$text\n\n(提示：这是通过监听通知栏获取的内容，可绕过系统验证码读取限制)\n\n——来自SmsSender",
                    targetOverride = email,
                    onSuccess = {
                        Log.i("SmsSender_Trace", "转发至 $email 成功")
                    },
                    onFailure = { e ->
                        Log.e("SmsSender_Trace", "转发至 $email 失败: ${e.message}")
                    }
                )
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
