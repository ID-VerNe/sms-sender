package com.yuuverne.smssender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickupCodeExtractorTest {

    @Test
    fun extractsPickupCodeWithUnsubscribeSuffix() {
        val body = "【菜鸟驿站】您的取件码为 123456，请及时取件，回T退订"

        val result = PickupCodeExtractor.extractPickupCode(body)

        assertEquals("123456", result.code)
        assertEquals("菜鸟驿站", result.company)
    }

    @Test
    fun extractsPickupCodeFromRegularCourierMessage() {
        val body = "【中通快递】您的包裹已到网点，取件码 2468，请及时取件"

        val result = PickupCodeExtractor.extractPickupCode(body)

        assertEquals("2468", result.code)
        assertEquals("中通快递", result.company)
    }

    @Test
    fun excludesVerificationCodeMessages() {
        val body = "【淘宝】您的验证码为 123456，请勿泄露"

        val result = PickupCodeExtractor.extractPickupCode(body)

        assertNull(result.code)
    }

    @Test
    fun excludesCarrierMessages() {
        val body = "【中国移动】您本月剩余流量为 2GB，回复 CXLL 查询"

        val result = PickupCodeExtractor.extractPickupCode(body)

        assertNull(result.code)
    }

    @Test
    fun excludesBankMessages() {
        val body = "【招商银行】您的信用卡账单已出，请及时还款"

        val result = PickupCodeExtractor.extractPickupCode(body)

        assertNull(result.code)
    }

    @Test
    fun ignoresCommonMarketingSuffixVariants() {
        val suffixes = listOf("退订", "回T退订", "回T", "回复TD", "请勿回复")

        for (suffix in suffixes) {
            val body = "【菜鸟驿站】您的取件码为 123456，请及时取件，$suffix"

            val result = PickupCodeExtractor.extractPickupCode(body)

            assertEquals("suffix=$suffix", "123456", result.code)
            assertEquals("suffix=$suffix", "菜鸟驿站", result.company)
        }
    }
}
