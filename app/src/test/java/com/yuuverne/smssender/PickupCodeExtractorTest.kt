package com.yuuverne.smssender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickupCodeExtractorTest {

    @Test
    fun `extracts pickup code and station name`() {
        val result = PickupCodeExtractor.extractPickupCode(
            "【菜鸟驿站】您的取件码是1234，请及时取件。"
        )

        assertEquals("1234", result.code)
        assertEquals("菜鸟驿站", result.company)
    }

    @Test
    fun `extracts alphanumeric pickup code`() {
        val result = PickupCodeExtractor.extractPickupCode("请凭A12B34到丰巢柜取件")

        assertEquals("A12B34", result.code)
        assertEquals("丰巢", result.company)
    }

    @Test
    fun `uses six digit fallback when express context is present`() {
        val result = PickupCodeExtractor.extractPickupCode("包裹已到前台，凭证 123456")

        assertEquals("123456", result.code)
    }

    @Test
    fun `extracts generic pickup code`() {
        val result = PickupCodeExtractor.extractPickupCode("您的包裹已到店，取件码：987654")

        assertEquals("987654", result.code)
    }

    @Test
    fun `ignores verification messages`() {
        val result = PickupCodeExtractor.extractPickupCode("验证码：123456，请勿泄露")

        assertNull(result.code)
    }

    @Test
    fun `returns empty result for unrelated message`() {
        val result = PickupCodeExtractor.extractPickupCode("会议改到下午三点，请准时参加。")

        assertNull(result.code)
        assertEquals("其他快递", result.company)
    }
}
