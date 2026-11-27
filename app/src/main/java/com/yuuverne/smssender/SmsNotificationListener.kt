package com.yuuverne.smssender

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

class SmsNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // 结合系统默认短信应用与已知 OEM 包名，避免误转发普通 messaging 应用
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        if (!isSmsPackage(packageName, defaultSmsPackage)) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "未知号码"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (text.isEmpty()) return

        Log.i("SmsSender_Trace", "【通知捕获】来自: $title, 内容: $text")

        // 转发邮件逻辑
        scope.launch {
            val config = ConfigManager(applicationContext)
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
}