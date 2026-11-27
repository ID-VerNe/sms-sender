package com.yuuverne.smssender

import java.util.Properties

/**
 * 用户未显式配置加密方式时，沿用旧的端口约定：465 使用 SSL，其他端口使用 STARTTLS。
 */
internal fun defaultUseSslForPort(port: String): Boolean =
    port.trim().toIntOrNull() == 465

/**
 * 构建 SMTP 属性。显式的 useSsl 选择优先于端口，且 SSL 与 STARTTLS 必须互斥。
 */
internal fun buildSmtpProperties(
    host: String,
    port: String,
    useSsl: Boolean
): Properties = Properties().apply {
    setProperty("mail.smtp.host", host)
    setProperty("mail.smtp.port", port)
    setProperty("mail.smtp.auth", "true")
    setProperty("mail.smtp.ssl.enable", useSsl.toString())
    setProperty("mail.smtp.starttls.enable", (!useSsl).toString())
    setProperty("mail.smtp.connectiontimeout", "10000")
    setProperty("mail.smtp.timeout", "15000")
}
