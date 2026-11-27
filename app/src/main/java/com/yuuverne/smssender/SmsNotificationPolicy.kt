package com.yuuverne.smssender

import java.security.MessageDigest

data class NotificationSnapshot(
    val packageName: String,
    val key: String?,
    val title: String,
    val text: String,
    val postedAt: Long,
    val isGroupSummary: Boolean = false,
    val isOngoing: Boolean = false,
    val isNoClear: Boolean = false,
    val isForegroundService: Boolean = false
)

object SmsNotificationPolicy {

    internal const val CONTENT_DEDUP_WINDOW_MS = 60_000L

    fun shouldForward(snapshot: NotificationSnapshot): Boolean {
        if (snapshot.text.isBlank()) return false
        if (snapshot.isGroupSummary || snapshot.isOngoing) return false
        if (snapshot.isNoClear || snapshot.isForegroundService) return false
        return true
    }

    /**
     * Stable identity for one notification callback series.
     *
     * When Android provides a notification key we use it so updates to the same
     * notification keep the same identity. Without a key we hash only stable
     * content fields; the rolling time window is applied by the DAO at delivery
     * time instead of being baked into the key.
     */
    fun notificationKey(snapshot: NotificationSnapshot): String {
        val stableKey = snapshot.key?.takeIf { it.isNotBlank() }
        return if (stableKey != null) {
            // StatusBarNotification.key already contains package/user/id/tag.
            // Include the package explicitly to avoid cross-app key collisions.
            "key:${snapshot.packageName}\u0000$stableKey"
        } else {
            val contentHash = sha256Hex(snapshot.packageName, snapshot.title, snapshot.text)
            "content:$contentHash"
        }
    }

    /** 0 means permanent dedup; a positive value is a rolling success window. */
    fun dedupWindowMs(snapshot: NotificationSnapshot): Long =
        if (snapshot.key.isNullOrBlank()) CONTENT_DEDUP_WINDOW_MS else 0L

    fun notificationId(snapshot: NotificationSnapshot, recipient: String): Long =
        notificationId(notificationKey(snapshot), recipient)

    fun notificationId(dedupKey: String, recipient: String): Long =
        sha256Bytes(dedupKey, recipient).toLong()

    fun uniqueWorkName(snapshot: NotificationSnapshot, recipient: String): String =
        "sms-notification:${recipientFingerprint(snapshot, recipient).toHex()}"

    private fun recipientFingerprint(snapshot: NotificationSnapshot, recipient: String): ByteArray =
        sha256Bytes(notificationKey(snapshot), recipient)

    private fun sha256Hex(vararg parts: String): String = sha256Bytes(*parts).toHex()

    private fun sha256Bytes(vararg parts: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toBytes())
            digest.update(bytes)
        }
        return digest.digest()
    }

    private fun Int.toBytes(): ByteArray = byteArrayOf(
        (this ushr 24).toByte(),
        (this ushr 16).toByte(),
        (this ushr 8).toByte(),
        this.toByte()
    )

    private fun ByteArray.toLong(): Long {
        var value = 0L
        for (index in 0 until Long.SIZE_BYTES) {
            value = (value shl 8) or (this[index].toLong() and 0xFF)
        }
        return value
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xFF
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}
