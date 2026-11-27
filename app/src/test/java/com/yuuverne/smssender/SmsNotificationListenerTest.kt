package com.yuuverne.smssender

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsNotificationListenerTest {
    @Test
    fun doesNotForwardWhenServiceIsDisabled() {
        assertFalse(shouldForwardNotification(isServiceEnabled = false))
    }

    @Test
    fun allowsForwardingWhenServiceIsEnabled() {
        assertTrue(shouldForwardNotification(isServiceEnabled = true))
    }
}
