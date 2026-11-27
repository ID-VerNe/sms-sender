package com.yuuverne.smssender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailSenderTest {

    @Test
    fun `port 465 enables SSL without STARTTLS`() {
        val properties = smtpSecurityProperties("465")

        assertEquals(mapOf("mail.smtp.ssl.enable" to "true"), properties)
        assertFalse(properties.containsKey("mail.smtp.starttls.enable"))
    }

    @Test
    fun `port 587 enables STARTTLS without SSL`() {
        val properties = smtpSecurityProperties("587")

        assertEquals(mapOf("mail.smtp.starttls.enable" to "true"), properties)
        assertFalse(properties.containsKey("mail.smtp.ssl.enable"))
    }

    @Test
    fun `invalid port falls back to STARTTLS`() {
        val properties = smtpSecurityProperties("not-a-port")

        assertTrue(properties["mail.smtp.starttls.enable"] == "true")
        assertFalse(properties.containsKey("mail.smtp.ssl.enable"))
    }
}
