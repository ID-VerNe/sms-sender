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

        val encrypted = AesGcmCipher.encrypt(key, plainText)

        assertNotEquals(plainText, encrypted)
        assertEquals(plainText, AesGcmCipher.decrypt(key, encrypted))
    }

    @Test
    fun `encrypting the same plaintext produces different ciphertext`() {
        val key = generateKey()
        val plainText = "same-secret"

        val first = AesGcmCipher.encrypt(key, plainText)
        val second = AesGcmCipher.encrypt(key, plainText)

        assertNotEquals(first, second)
        assertEquals(plainText, AesGcmCipher.decrypt(key, first))
        assertEquals(plainText, AesGcmCipher.decrypt(key, second))
    }

    @Test
    fun `decrypting with a different key fails`() {
        val encrypted = AesGcmCipher.encrypt(generateKey(), "secret")
        val differentKey = generateKey()

        assertThrows(Exception::class.java) {
            AesGcmCipher.decrypt(differentKey, encrypted)
        }
    }

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
