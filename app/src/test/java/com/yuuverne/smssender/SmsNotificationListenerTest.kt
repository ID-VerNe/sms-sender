package com.yuuverne.smssender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsNotificationListenerTest {

    @Test
    fun selectsBigTextWhenOnlyBigTextIsPresent() {
        val result = selectNotificationText(
            bigText = "这是一条完整的长短信",
            textLines = null,
            text = null
        )

        assertEquals("这是一条完整的长短信", result)
    }

    @Test
    fun joinsTextLinesWhenOnlyTextLinesArePresent() {
        val result = selectNotificationText(
            bigText = null,
            textLines = arrayOf("第一行", "第二行", "第三行"),
            text = null
        )

        assertEquals("第一行\n第二行\n第三行", result)
    }

    @Test
    fun selectsTextWhenOnlyTextIsPresent() {
        val result = selectNotificationText(
            bigText = null,
            textLines = null,
            text = "普通短信"
        )

        assertEquals("普通短信", result)
    }

    @Test
    fun prefersBigTextWhenMultipleFieldsArePresent() {
        val result = selectNotificationText(
            bigText = "完整长短信",
            textLines = arrayOf("截断内容"),
            text = "更短的内容"
        )

        assertEquals("完整长短信", result)
    }

    @Test
    fun selectsUntruncatedLongBigText() {
        val longBody = buildString {
            repeat(200) { index ->
                append("第${index + 1}段：这是一条需要完整转发的长短信内容。")
            }
        }

        val result = selectNotificationText(
            bigText = longBody,
            textLines = arrayOf("通知栏截断摘要"),
            text = "更短的摘要"
        )

        assertEquals(longBody, result)
    }

    @Test
    fun fallsBackToTextLinesWhenBigTextIsBlank() {
        val result = selectNotificationText(
            bigText = "  \n",
            textLines = arrayOf("第一行", "第二行"),
            text = "普通短信"
        )

        assertEquals("第一行\n第二行", result)
    }

    @Test
    fun returnsNullWhenAllFieldsAreEmpty() {
        val result = selectNotificationText(
            bigText = "",
            textLines = emptyArray(),
            text = ""
        )

        assertNull(result)
    }

    @Test
    fun doesNotForwardWhenServiceIsDisabled() {
        assertFalse(shouldForwardNotification(isServiceEnabled = false))
    }

    @Test
    fun allowsForwardingWhenServiceIsEnabled() {
        assertTrue(shouldForwardNotification(isServiceEnabled = true))
    }
}
