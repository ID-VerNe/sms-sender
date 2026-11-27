package com.yuuverne.smssender

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.yuuverne.smssender.database.ForwardingRule

internal fun isSmsPackage(packageName: String): Boolean {
    return packageName.contains("mms", ignoreCase = true) ||
        packageName.contains("messaging", ignoreCase = true)
}

internal fun shouldForwardNotification(isServiceEnabled: Boolean, text: String): Boolean {
    return isServiceEnabled && text.isNotEmpty()
}

internal fun resolveNotificationRecipients(
    receiverEmail: String,
    rules: List<ForwardingRule>,
    text: String,
): Set<String> {
    val recipients = linkedSetOf<String>()
    receiverEmail.trim().takeIf { it.isNotEmpty() }?.let(recipients::add)
    rules.asSequence()
        .filter { it.isEnabled && it.keyword.isNotBlank() }
        .filter { text.contains(it.keyword, ignoreCase = true) }
        .map { it.targetEmail.trim() }
        .filter { it.isNotEmpty() }
        .forEach(recipients::add)
    return recipients
}

internal fun buildNotificationEmailContent(title: String, text: String): String {
    return "检测到系统通知栏短信：\n来自：$title\n内容：$text\n\n" +
        "(提示：这是通过监听通知栏获取的内容，可绕过系统验证码读取限制)\n\n——来自SmsSender"
}

class SmsNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // 过滤短信应用包名
        if (!isSmsPackage(packageName)) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "未知号码"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val config = ConfigManager(applicationContext)
        if (!shouldForwardNotification(config.isServiceEnabled, text)) {
            return
        }

        Log.i("SmsSender_Trace", "【通知捕获】来自: $title, 内容: $text")

        // 转发邮件逻辑
        scope.launch {
            val db = com.yuuverne.smssender.database.AppDatabase.getDatabase(applicationContext)
            val enabledRules = db.ruleDao().getEnabledRules()
            
            // 构建待发送邮箱列表 (去重)
            val targetEmails = resolveNotificationRecipients(config.receiverEmail, enabledRules, text)

            if (targetEmails.isEmpty()) {
                Log.w("SmsSender_Trace", "无有效收件人邮箱，取消转发")
                return@launch
            }

            val smartSubject = ExtractionUtils.getSmartSubject(config, title, text)
            
            targetEmails.forEach { email ->
                MailSender.sendEmail(
                    config = config,
                    subject = smartSubject,
                    content = buildNotificationEmailContent(title, text),
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
}