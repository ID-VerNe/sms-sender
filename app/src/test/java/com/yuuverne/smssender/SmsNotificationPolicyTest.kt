package com.yuuverne.smssender

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsNotificationPolicyTest {

    @Test
    fun normalSmsNotificationIsForwarded() {
        assertTrue(SmsNotificationPolicy.shouldForward(snapshot()))
    }

    @Test
    fun groupSummaryIsFiltered() {
        assertFalse(SmsNotificationPolicy.shouldForward(snapshot(isGroupSummary = true)))
    }

    @Test
    fun ongoingNotificationIsFiltered() {
        assertFalse(SmsNotificationPolicy.shouldForward(snapshot(isOngoing = true)))
    }

    @Test
    fun noClearNotificationIsFiltered() {
        assertFalse(SmsNotificationPolicy.shouldForward(snapshot(isNoClear = true)))
    }

    @Test
    fun foregroundServiceNotificationIsFiltered() {
        assertFalse(SmsNotificationPolicy.shouldForward(snapshot(isForegroundService = true)))
    }

    @Test
    fun nonSmsPackageIsFiltered() {
        assertFalse(
            SmsNotificationPolicy.shouldForward(
                snapshot().copy(packageName = "com.example.other")
            )
        )
    }

    @Test
    fun blankTextIsFiltered() {
        assertFalse(SmsNotificationPolicy.shouldForward(snapshot(text = "   ")))
    }

    @Test
    fun sameNotificationAndContentHasStableId() {
        val first = snapshot()
        val updated = first.copy(postedAt = first.postedAt + 5_000L)

        assertNotEquals(first.postedAt, updated.postedAt)
        assertTrue(
            SmsNotificationPolicy.notificationId(first) ==
                SmsNotificationPolicy.notificationId(updated)
        )
    }

    @Test
    fun sameNotificationKeyWithChangedContentGetsNewId() {
        val first = snapshot()
        val changed = first.copy(text = "验证码 5678")

        assertNotEquals(
            SmsNotificationPolicy.notificationId(first),
            SmsNotificationPolicy.notificationId(changed)
        )
    }

    @Test
    fun sameNotificationUpdateIsDuplicate() {
        val first = snapshot()
        val updated = first.copy(postedAt = first.postedAt + 5_000L)

        assertTrue(SmsNotificationPolicy.isDuplicate(updated, listOf(recordFor(first))))
    }

    @Test
    fun changedContentForSameNotificationKeyIsNotDuplicate() {
        val first = snapshot()
        val changed = first.copy(text = "验证码 5678")

        assertFalse(SmsNotificationPolicy.isDuplicate(changed, listOf(recordFor(first))))
    }

    @Test
    fun sameContentWithoutStableKeyIsDuplicateWithinWindow() {
        val first = snapshot(key = null)
        val updated = first.copy(postedAt = first.postedAt + 30_000L)

        assertTrue(SmsNotificationPolicy.isDuplicate(updated, listOf(recordFor(first))))
    }

    @Test
    fun sameContentOutsideWindowIsNotDuplicate() {
        val first = snapshot(key = null)
        val later = first.copy(postedAt = first.postedAt + 60_000L)

        assertFalse(SmsNotificationPolicy.isDuplicate(later, listOf(recordFor(first))))
    }

    @Test
    fun differentSenderWithSameBodyIsNotDuplicate() {
        val first = snapshot()
        val otherSender = first.copy(title = "10010")

        assertFalse(SmsNotificationPolicy.isDuplicate(otherSender, listOf(recordFor(first))))
    }

    private fun snapshot(
        key: String? = "com.android.messaging|42|null|0",
        title: String = "10086",
        text: String = "验证码 1234",
        postedAt: Long = 1_000L,
        isGroupSummary: Boolean = false,
        isOngoing: Boolean = false,
        isNoClear: Boolean = false,
        isForegroundService: Boolean = false
    ) = NotificationSnapshot(
        packageName = "com.android.messaging",
        key = key,
        title = title,
        text = text,
        postedAt = postedAt,
        isGroupSummary = isGroupSummary,
        isOngoing = isOngoing,
        isNoClear = isNoClear,
        isForegroundService = isForegroundService
    )

    private fun recordFor(snapshot: NotificationSnapshot) = ForwardedNotification(
        id = SmsNotificationPolicy.notificationId(snapshot),
        address = snapshot.title,
        body = snapshot.text,
        timestamp = snapshot.postedAt
    )
}
