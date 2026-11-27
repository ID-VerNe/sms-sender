package com.yuuverne.smssender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Sha256Test {

    @Test
    fun `hash returns lowercase SHA-256 of raw UTF-8 value`() {
        assertEquals(
            "9646f275f10ae73f70fa297fef85e62b5accd3a38284eb0a64b8203e12dd1373",
            Sha256.hash("10086")
        )
        assertEquals(
            "3c3e0572ee786d27a4fbed586d5394ba8a615505ee02cb51469e3eff4f87886c",
            Sha256.hash("验证码 1234")
        )
    }

    @Test
    fun `hash parts length prefixes fields to avoid ambiguous concatenation`() {
        assertEquals(
            "16275ef0f5d0eb9dd9e0a53277549fda5c886358a6872df23c797b13e11455bc",
            Sha256.hash("a", "b")
        )
        assertNotEquals(Sha256.hash("ab"), Sha256.hash("a", "b"))
    }
}
