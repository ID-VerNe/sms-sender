package com.yuuverne.smssender

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmCipherTest {

    @Test
    fun `ciphertext does not contain plaintext and decrypts correctly`() {
        val key = generateKey()
        val plainText = "smtp-password-or-api-key"

        val encrypted = AesGcmCipher.encrypt(key, plainText, SMTP_AAD)

        assertNotEquals(plainText, encrypted)
        assertEquals(plainText, AesGcmCipher.decrypt(key, encrypted, SMTP_AAD))
    }

    @Test
    fun `encrypting the same plaintext produces different ciphertext`() {
        val key = generateKey()
        val plainText = "same-secret"

        val first = AesGcmCipher.encrypt(key, plainText, SMTP_AAD)
        val second = AesGcmCipher.encrypt(key, plainText, SMTP_AAD)

        assertNotEquals(first, second)
        assertEquals(plainText, AesGcmCipher.decrypt(key, first, SMTP_AAD))
        assertEquals(plainText, AesGcmCipher.decrypt(key, second, SMTP_AAD))
    }

    @Test
    fun `decrypting with a different key fails`() {
        val encrypted = AesGcmCipher.encrypt(generateKey(), "secret", SMTP_AAD)
        val differentKey = generateKey()

        assertThrows(Exception::class.java) {
            AesGcmCipher.decrypt(differentKey, encrypted, SMTP_AAD)
        }
    }

    @Test
    fun `decrypting with a different associated data fails`() {
        val key = generateKey()
        val encrypted = AesGcmCipher.encrypt(key, "secret", SMTP_AAD)

        assertThrows(Exception::class.java) {
            AesGcmCipher.decrypt(key, encrypted, LLM_AAD)
        }
    }

    @Test
    fun `decrypting corrupted ciphertext fails`() {
        val key = generateKey()
        val encrypted = AesGcmCipher.encrypt(key, "secret", SMTP_AAD)
        val corrupted = encrypted.dropLast(4) + if (encrypted.last() == 'A') "BBB=" else "AAAA"

        assertThrows(Exception::class.java) {
            AesGcmCipher.decrypt(key, corrupted, SMTP_AAD)
        }
    }

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private companion object {
        const val SMTP_AAD = "smtp_password"
        const val LLM_AAD = "llm_api_key"
    }
}
