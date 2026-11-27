package com.yuuverne.smssender

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
            val contentHash = Sha256.hash(snapshot.packageName, snapshot.title, snapshot.text)
            "content:$contentHash"
        }
    }

    /** 0 means permanent dedup; a positive value is a rolling success window. */
    fun dedupWindowMs(snapshot: NotificationSnapshot): Long =
        if (snapshot.key.isNullOrBlank()) CONTENT_DEDUP_WINDOW_MS else 0L

    fun notificationId(snapshot: NotificationSnapshot, recipient: String): Long =
        notificationId(notificationKey(snapshot), recipient)

    fun notificationId(dedupKey: String, recipient: String): Long =
        Sha256.hashToLong(dedupKey, recipient)

    fun uniqueWorkName(snapshot: NotificationSnapshot, recipient: String): String =
        "sms-notification:${Sha256.hash(notificationKey(snapshot), recipient)}"
}
