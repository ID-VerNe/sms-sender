package com.yuuverne.smssender

import com.yuuverne.smssender.database.ForwardingRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsNotificationListenerTest {

    @Test
    fun `master switch blocks forwarding`() {
        assertFalse(shouldForwardNotification(isServiceEnabled = false, text = "123456"))
        assertTrue(shouldForwardNotification(isServiceEnabled = true, text = "123456"))
    }

    @Test
    fun `empty notification text is not forwarded`() {
        assertFalse(shouldForwardNotification(isServiceEnabled = true, text = ""))
    }

    @Test
    fun `recognizes common SMS package names`() {
        assertTrue(isSmsPackage("com.android.messaging"))
        assertTrue(isSmsPackage("com.google.android.apps.messaging"))
        assertTrue(isSmsPackage("com.samsung.android.mms"))
        assertFalse(isSmsPackage("com.example.mail"))
    }

    @Test
    fun `recipients are deduplicated and filtered by enabled matching rules`() {
        val rules = listOf(
            ForwardingRule(keyword = "code", targetEmail = "rule@example.com"),
            ForwardingRule(keyword = "CODE", targetEmail = "default@example.com"),
            ForwardingRule(keyword = "other", targetEmail = "other@example.com"),
            ForwardingRule(keyword = "code", targetEmail = "disabled@example.com", isEnabled = false),
            ForwardingRule(keyword = "code", targetEmail = "   "),
        )

        val recipients = resolveNotificationRecipients(
            receiverEmail = " default@example.com ",
            rules = rules,
            text = "Your code is 123456",
        )

        assertEquals(setOf("default@example.com", "rule@example.com"), recipients)
    }

    @Test
    fun `blank default recipient does not create an empty target`() {
        val recipients = resolveNotificationRecipients(
            receiverEmail = " ",
            rules = emptyList(),
            text = "hello",
        )

        assertTrue(recipients.isEmpty())
    }

    @Test
    fun `notification email contains sender and body`() {
        val content = buildNotificationEmailContent("12345", "您的验证码是123456")

        assertTrue(content.contains("来自：12345"))
        assertTrue(content.contains("内容：您的验证码是123456"))
        assertTrue(content.contains("——来自SmsSender"))
    }
}
