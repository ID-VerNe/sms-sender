package com.yuuverne.smssender

/** 生成可安全写入 Logcat 的短信通知摘要。 */
internal object SensitiveLog {
    private const val REDACTED = "[已脱敏]"

    fun notificationLog(packageName: String, sender: String, body: String): String {
        return "【通知捕获】短信应用: $packageName, 发件人: ${redact(sender)}, 内容: ${redact(body)}"
    }

    private fun redact(value: String): String = if (value.isBlank()) "未知" else REDACTED
}
