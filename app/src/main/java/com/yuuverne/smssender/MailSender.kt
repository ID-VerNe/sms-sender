package com.yuuverne.smssender

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object MailSender {

    fun sendEmail(
        config: ConfigManager,
        subject: String,
        content: String,
        targetOverride: String? = null,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val finalTarget = targetOverride ?: config.receiverEmail
        if (finalTarget.isEmpty()) {
            onFailure(Exception("未配置接收邮箱"))
            return
        }
        android.util.Log.d("MailSender", "开始构建邮件会话... Host: ${config.smtpHost}, Target: $finalTarget")
        
        val props = Properties()
        props["mail.smtp.host"] = config.smtpHost
        props["mail.smtp.port"] = config.smtpPort
        props["mail.smtp.auth"] = "true"
        // 465 端口走 SSL，587 端口走 STARTTLS
        if (config.smtpPort.toIntOrNull() == 465) {
            props["mail.smtp.ssl.enable"] = "true"
        } else {
            props["mail.smtp.starttls.enable"] = "true"
        }
        props["mail.smtp.connectiontimeout"] = "10000"
        props["mail.smtp.timeout"] = "15000"

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(config.smtpEmail, config.smtpPassword)
            }
        })

        try {
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(config.smtpEmail))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(finalTarget))
            message.subject = subject
            message.setText(content)

            android.util.Log.d("MailSender", "正在发送至 $finalTarget...")
            Transport.send(message)
            android.util.Log.i("MailSender", "发送成功至 $finalTarget！")
            onSuccess()
        } catch (e: Exception) {
            android.util.Log.e("MailSender", "邮件发送失败详情: $finalTarget", e)
            onFailure(e)
        }
    }
}
