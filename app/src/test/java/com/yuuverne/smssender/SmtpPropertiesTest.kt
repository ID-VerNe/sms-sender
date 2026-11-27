package com.yuuverne.smssender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmtpPropertiesTest {

    @Test
    fun port465WithSslUsesOnlySsl() {
        val props = buildSmtpProperties("smtp.example.com", "465", useSsl = true)

        assertEquals("true", props.getProperty("mail.smtp.ssl.enable"))
        assertEquals("false", props.getProperty("mail.smtp.starttls.enable"))
    }

    @Test
    fun port465WithoutSslUsesOnlyStartTls() {
        val props = buildSmtpProperties("smtp.example.com", "465", useSsl = false)

        assertEquals("false", props.getProperty("mail.smtp.ssl.enable"))
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"))
    }

    @Test
    fun port587WithSslUsesOnlySsl() {
        val props = buildSmtpProperties("smtp.example.com", "587", useSsl = true)

        assertEquals("true", props.getProperty("mail.smtp.ssl.enable"))
        assertEquals("false", props.getProperty("mail.smtp.starttls.enable"))
    }

    @Test
    fun port587WithoutSslUsesOnlyStartTls() {
        val props = buildSmtpProperties("smtp.example.com", "587", useSsl = false)

        assertEquals("false", props.getProperty("mail.smtp.ssl.enable"))
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"))
    }

    @Test
    fun defaultModeIsSslOnlyForPort465() {
        assertTrue(defaultUseSslForPort("465"))
        assertTrue(defaultUseSslForPort(" 465 "))
        assertFalse(defaultUseSslForPort("587"))
        assertFalse(defaultUseSslForPort("25"))
        assertFalse(defaultUseSslForPort("invalid"))
    }

    @Test
    fun commonSmtpPropertiesArePreserved() {
        val props = buildSmtpProperties("smtp.example.com", "587", useSsl = false)

        assertEquals("smtp.example.com", props.getProperty("mail.smtp.host"))
        assertEquals("587", props.getProperty("mail.smtp.port"))
        assertEquals("true", props.getProperty("mail.smtp.auth"))
        assertEquals("10000", props.getProperty("mail.smtp.connectiontimeout"))
        assertEquals("15000", props.getProperty("mail.smtp.timeout"))
    }
}
