package com.yuuverne.smssender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractionUtilsTest {

    @Test
    fun extractsEnglishVerificationCode() {
        assertEquals("123456", ExtractionUtils.extractCode("Your verification code is 123456"))
    }

    @Test
    fun extractsChineseVerificationCode() {
        assertEquals("8848", ExtractionUtils.extractCode("【淘宝】您的验证码为 8848，请勿泄露"))
    }

    @Test
    fun extractsAlphanumericCode() {
        assertEquals("A1B2C3", ExtractionUtils.extractCode("Your login code: A1B2C3"))
    }

    @Test
    fun normalizesArabicIndicDigits() {
        assertEquals("123456", ExtractionUtils.extractCode("验证码：١٢٣٤٥٦"))
    }

    @Test
    fun ignoresDiscountCodeMessages() {
        assertNull(ExtractionUtils.extractCode("Use discount code SAVE20 for your next order"))
    }

    @Test
    fun returnsNullForBlankBody() {
        assertNull(ExtractionUtils.extractCode(""))
        assertNull(ExtractionUtils.extractCode("   \n"))
    }
}
