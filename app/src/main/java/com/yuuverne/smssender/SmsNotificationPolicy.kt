package com.yuuverne.smssender

import java.security.MessageDigest
import kotlin.math.abs

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

data class ForwardedNotification(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long
)

object SmsNotificationPolicy {

    private const val CONTENT_DEDUP_WINDOW_MS = 60_000L
    private val SMS_PACKAGE_MARKERS = listOf("mms", "messaging")

    fun shouldForward(snapshot: NotificationSnapshot): Boolean {
        val packageName = snapshot.packageName.lowercase()
        if (SMS_PACKAGE_MARKERS.none { packageName.contains(it) }) return false
        if (snapshot.text.isBlank()) return false
        if (snapshot.isGroupSummary || snapshot.isOngoing) return false
        if (snapshot.isNoClear || snapshot.isForegroundService) return false
        return true
    }

    fun notificationId(snapshot: NotificationSnapshot): Long {
        val stableKey = snapshot.key?.takeIf { it.isNotBlank() }
            ?: "content:${snapshot.postedAt / CONTENT_DEDUP_WINDOW_MS}"
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(snapshot.packageName.toByteArray())
        digest.update(0)
        digest.update(stableKey.toByteArray())
        digest.update(0)
        digest.update(snapshot.title.toByteArray())
        digest.update(0)
        digest.update(snapshot.text.toByteArray())
        return digest.digest().toLong()
    }

    fun isDuplicate(
        snapshot: NotificationSnapshot,
        forwarded: List<ForwardedNotification>
    ): Boolean {
        val id = notificationId(snapshot)
        return forwarded.any { it.id == id } || forwarded.any { record ->
            record.address == snapshot.title &&
                record.body == snapshot.text &&
                abs(record.timestamp - snapshot.postedAt) < CONTENT_DEDUP_WINDOW_MS
        }
    }

    private fun ByteArray.toLong(): Long =
        (0 until Long.SIZE_BYTES).fold(0L) { value, index ->
            (value shl 8) or (this[index].toLong() and 0xFF)
        }
}
