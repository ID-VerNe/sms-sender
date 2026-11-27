package com.yuuverne.smssender

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveLogTest {

    @Test
    fun `notification log redacts sender and complete sms body`() {
        val sender = "13800138000"
        val body = "您的验证码是 123456，请勿泄露"

        val log = SensitiveLog.notificationLog("com.android.messaging", sender, body)

        assertFalse(log.contains(sender))
        assertFalse(log.contains(body))
        assertFalse(log.contains("123456"))
        assertTrue(log.contains("com.android.messaging"))
        assertTrue(log.contains("[已脱敏]"))
    }
}
