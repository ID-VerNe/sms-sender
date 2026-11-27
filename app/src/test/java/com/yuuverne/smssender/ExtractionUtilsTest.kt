package com.yuuverne.smssender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractionUtilsTest {

    @Test
    fun `extracts Chinese verification code`() {
        assertEquals("123456", ExtractionUtils.extractCode("您的验证码是123456，请勿泄露。"))
    }

    @Test
    fun `extracts English OTP with punctuation`() {
        assertEquals("482913", ExtractionUtils.extractCode("Your OTP is 482913. Do not share it."))
    }

    @Test
    fun `extracts alphanumeric verification code`() {
        assertEquals("A1B2C3", ExtractionUtils.extractCode("Your verification code: A1B2C3"))
    }

    @Test
    fun `normalizes Arabic Indic digits`() {
        assertEquals("123456", ExtractionUtils.extractCode("验证码：١٢٣٤٥٦"))
    }

    @Test
    fun `extracts code that appears before the keyword`() {
        assertEquals("123456", ExtractionUtils.extractCode("123456 is your OTP."))
    }

    @Test
    fun `ignores discount code messages`() {
        assertNull(ExtractionUtils.extractCode("Use discount code SAVE20 for your next order."))
    }

    @Test
    fun `returns null for blank or code free text`() {
        assertNull(ExtractionUtils.extractCode(""))
        assertNull(ExtractionUtils.extractCode("Your package will arrive tomorrow."))
    }
}
