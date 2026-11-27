package com.yuuverne.smssender

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
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
    fun blankTextIsFiltered() {
        assertFalse(SmsNotificationPolicy.shouldForward(snapshot(text = "   ")))
    }

    @Test
    fun sameKeyWithChangedBodyStillHasSameRecipientIdentity() {
        val first = snapshot()
        val updated = first.copy(
            title = "10086（新排版）",
            text = "验证码 5678",
            postedAt = first.postedAt + 5_000L
        )

        assertEquals(
            SmsNotificationPolicy.notificationId(first, EMAIL_A),
            SmsNotificationPolicy.notificationId(updated, EMAIL_A)
        )
        assertEquals(
            SmsNotificationPolicy.uniqueWorkName(first, EMAIL_A),
            SmsNotificationPolicy.uniqueWorkName(updated, EMAIL_A)
        )
    }

    @Test
    fun differentKeysWithSameBodyHaveDifferentIdentities() {
        val first = snapshot()
        val second = first.copy(key = "com.android.messaging|43|null|0")

        assertNotEquals(
            SmsNotificationPolicy.notificationId(first, EMAIL_A),
            SmsNotificationPolicy.notificationId(second, EMAIL_A)
        )
        assertNotEquals(
            SmsNotificationPolicy.uniqueWorkName(first, EMAIL_A),
            SmsNotificationPolicy.uniqueWorkName(second, EMAIL_A)
        )
    }

    @Test
    fun differentRecipientsHaveIndependentIdentitiesAndWorkNames() {
        val notification = snapshot()

        assertNotEquals(
            SmsNotificationPolicy.notificationId(notification, EMAIL_A),
            SmsNotificationPolicy.notificationId(notification, EMAIL_B)
        )
        assertNotEquals(
            SmsNotificationPolicy.uniqueWorkName(notification, EMAIL_A),
            SmsNotificationPolicy.uniqueWorkName(notification, EMAIL_B)
        )
    }

    @Test
    fun sameContentWithoutStableKeyIsDuplicateWithinWindow() {
        val first = snapshot(key = null, postedAt = 1_000L)
        val updated = first.copy(postedAt = 31_000L)

        assertEquals(
            SmsNotificationPolicy.notificationId(first, EMAIL_A),
            SmsNotificationPolicy.notificationId(updated, EMAIL_A)
        )
    }

    @Test
    fun sameContentWithoutStableKeyIsNewOutsideWindow() {
        val first = snapshot(key = null, postedAt = 1_000L)
        val later = first.copy(postedAt = 61_000L)

        assertNotEquals(
            SmsNotificationPolicy.notificationId(first, EMAIL_A),
            SmsNotificationPolicy.notificationId(later, EMAIL_A)
        )
    }

    @Test
    fun noKeyFallbackIncludesTitleAndBody() {
        val first = snapshot(key = null)

        assertNotEquals(
            SmsNotificationPolicy.notificationId(first, EMAIL_A),
            SmsNotificationPolicy.notificationId(first.copy(title = "10010"), EMAIL_A)
        )
        assertNotEquals(
            SmsNotificationPolicy.notificationId(first, EMAIL_A),
            SmsNotificationPolicy.notificationId(first.copy(text = "验证码 5678"), EMAIL_A)
        )
    }

    @Test
    fun blankKeyUsesContentFallback() {
        val blankKey = snapshot(key = "   ")
        val noKey = blankKey.copy(key = null)

        assertEquals(
            SmsNotificationPolicy.notificationId(blankKey, EMAIL_A),
            SmsNotificationPolicy.notificationId(noKey, EMAIL_A)
        )
    }

    @Test
    fun notificationWorkUsesKeepPolicy() {
        assertEquals(ExistingWorkPolicy.KEEP, NotificationWorkPolicy.existingWorkPolicy)
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

    private companion object {
        const val EMAIL_A = "a@example.com"
        const val EMAIL_B = "b@example.com"
    }
}
