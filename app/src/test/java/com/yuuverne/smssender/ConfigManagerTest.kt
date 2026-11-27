package com.yuuverne.smssender

import android.content.SharedPreferences
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ConfigManagerTest {

    private lateinit var legacyPrefs: FakeSharedPreferences
    private lateinit var securePrefs: FakeSharedPreferences
    private lateinit var codec: TestAesGcmCodec

    @Before
    fun setUp() {
        legacyPrefs = FakeSharedPreferences()
        securePrefs = FakeSharedPreferences()
        codec = TestAesGcmCodec()
    }

    @Test
    fun `secret writes are encrypted at rest and round trip`() {
        val config = ConfigManager(legacyPrefs, securePrefs, codec)

        config.smtpPassword = SMTP_SECRET
        config.llmApiKey = API_SECRET

        assertFalse(legacyPrefs.contains(ConfigManager.LEGACY_SMTP_PASSWORD_KEY))
        assertFalse(legacyPrefs.contains(ConfigManager.LEGACY_LLM_API_KEY_KEY))

        val storedSmtpPassword = securePrefs.getString(ConfigManager.SMTP_PASSWORD_KEY, null)
        val storedApiKey = securePrefs.getString(ConfigManager.LLM_API_KEY_KEY, null)
        assertNotEquals(SMTP_SECRET, storedSmtpPassword)
        assertNotEquals(API_SECRET, storedApiKey)
        assertEquals(SMTP_SECRET, codec.decrypt(storedSmtpPassword!!, ConfigManager.SMTP_PASSWORD_KEY))
        assertEquals(API_SECRET, codec.decrypt(storedApiKey!!, ConfigManager.LLM_API_KEY_KEY))

        assertEquals(SMTP_SECRET, config.smtpPassword)
        assertEquals(API_SECRET, config.llmApiKey)
    }

    @Test
    fun `preference key is used as associated data`() {
        val config = ConfigManager(legacyPrefs, securePrefs, codec)

        config.smtpPassword = SMTP_SECRET
        config.llmApiKey = API_SECRET

        assertEquals(
            listOf(ConfigManager.SMTP_PASSWORD_KEY, ConfigManager.LLM_API_KEY_KEY),
            codec.encryptAssociatedData
        )
    }

    @Test
    fun `legacy plaintext secrets are migrated and removed`() {
        legacyPrefs.edit()
            .putString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, SMTP_SECRET)
            .putString(ConfigManager.LEGACY_LLM_API_KEY_KEY, API_SECRET)
            .commit()

        val config = ConfigManager(legacyPrefs, securePrefs, codec)

        assertFalse(legacyPrefs.contains(ConfigManager.LEGACY_SMTP_PASSWORD_KEY))
        assertFalse(legacyPrefs.contains(ConfigManager.LEGACY_LLM_API_KEY_KEY))
        assertEquals(SMTP_SECRET, config.smtpPassword)
        assertEquals(API_SECRET, config.llmApiKey)

        val storedSmtpPassword = securePrefs.getString(ConfigManager.SMTP_PASSWORD_KEY, null)!!
        val storedApiKey = securePrefs.getString(ConfigManager.LLM_API_KEY_KEY, null)!!
        assertNotEquals(SMTP_SECRET, storedSmtpPassword)
        assertNotEquals(API_SECRET, storedApiKey)
        assertEquals(SMTP_SECRET, codec.decrypt(storedSmtpPassword, ConfigManager.SMTP_PASSWORD_KEY))
        assertEquals(API_SECRET, codec.decrypt(storedApiKey, ConfigManager.LLM_API_KEY_KEY))
    }

    @Test
    fun `existing encrypted secret wins and stale plaintext is removed`() {
        val encrypted = codec.encrypt(SMTP_SECRET, ConfigManager.SMTP_PASSWORD_KEY)
        securePrefs.edit().putString(ConfigManager.SMTP_PASSWORD_KEY, encrypted).commit()
        legacyPrefs.edit().putString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, "stale-plaintext").commit()

        val config = ConfigManager(legacyPrefs, securePrefs, codec)

        assertEquals(SMTP_SECRET, config.smtpPassword)
        assertFalse(legacyPrefs.contains(ConfigManager.LEGACY_SMTP_PASSWORD_KEY))
        assertEquals(encrypted, securePrefs.getString(ConfigManager.SMTP_PASSWORD_KEY, null))
    }

    @Test
    fun `ordinary configuration keeps existing storage behavior`() {
        val config = ConfigManager(legacyPrefs, securePrefs, codec)

        config.smtpHost = "smtp.example.com"
        config.smtpPort = "587"
        config.smtpEmail = "sender@example.com"
        config.receiverEmail = "receiver@example.com"
        config.useLlm = true
        config.llmApiUrl = "https://example.com/llm"
        config.llmModelId = "test-model"

        assertEquals("smtp.example.com", legacyPrefs.getString("smtp_host", null))
        assertEquals("587", legacyPrefs.getString("smtp_port", null))
        assertEquals("sender@example.com", legacyPrefs.getString("smtp_email", null))
        assertEquals("receiver@example.com", legacyPrefs.getString("receiver_email", null))
        assertEquals(true, legacyPrefs.getBoolean("use_llm", false))
        assertEquals("https://example.com/llm", legacyPrefs.getString("llm_api_url", null))
        assertEquals("test-model", legacyPrefs.getString("llm_model_id", null))
        assertFalse(securePrefs.contains("smtp_host"))
        assertFalse(securePrefs.contains("llm_api_url"))
    }

    @Test
    fun `empty secret removes encrypted and legacy values`() {
        val config = ConfigManager(legacyPrefs, securePrefs, codec)
        config.smtpPassword = SMTP_SECRET
        config.llmApiKey = API_SECRET

        config.smtpPassword = ""
        config.llmApiKey = ""

        assertFalse(securePrefs.contains(ConfigManager.SMTP_PASSWORD_KEY))
        assertFalse(securePrefs.contains(ConfigManager.LLM_API_KEY_KEY))
        assertFalse(legacyPrefs.contains(ConfigManager.LEGACY_SMTP_PASSWORD_KEY))
        assertFalse(legacyPrefs.contains(ConfigManager.LEGACY_LLM_API_KEY_KEY))
        assertEquals("", config.smtpPassword)
        assertEquals("", config.llmApiKey)
    }

    @Test
    fun `empty legacy secret is removed without creating ciphertext`() {
        legacyPrefs.edit()
            .putString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, "")
            .putString(ConfigManager.LEGACY_LLM_API_KEY_KEY, "")
            .commit()

        ConfigManager(legacyPrefs, securePrefs, codec)

        assertFalse(legacyPrefs.contains(ConfigManager.LEGACY_SMTP_PASSWORD_KEY))
        assertFalse(legacyPrefs.contains(ConfigManager.LEGACY_LLM_API_KEY_KEY))
        assertFalse(securePrefs.contains(ConfigManager.SMTP_PASSWORD_KEY))
        assertFalse(securePrefs.contains(ConfigManager.LLM_API_KEY_KEY))
    }

    @Test
    fun `failed encryption preserves previous encrypted secret and legacy plaintext`() {
        val config = ConfigManager(legacyPrefs, securePrefs, codec)
        config.smtpPassword = "previous-secret"
        val previousCiphertext = securePrefs.getString(ConfigManager.SMTP_PASSWORD_KEY, null)
        legacyPrefs.edit().putString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, "legacy-secret").commit()
        codec.encryptFailure = IllegalStateException("encryption failed")

        assertThrows(SecretStorageException::class.java) {
            config.smtpPassword = "new-secret"
        }

        assertEquals(previousCiphertext, securePrefs.getString(ConfigManager.SMTP_PASSWORD_KEY, null))
        assertEquals("legacy-secret", legacyPrefs.getString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, null))
        assertEquals("previous-secret", config.smtpPassword)
    }

    @Test
    fun `failed encrypted commit preserves previous secret and legacy plaintext`() {
        val config = ConfigManager(legacyPrefs, securePrefs, codec)
        config.smtpPassword = "previous-secret"
        val previousCiphertext = securePrefs.getString(ConfigManager.SMTP_PASSWORD_KEY, null)
        legacyPrefs.edit().putString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, "legacy-secret").commit()
        securePrefs.failNextCommit = true

        assertThrows(SecretStorageException::class.java) {
            config.smtpPassword = "new-secret"
        }

        assertEquals(previousCiphertext, securePrefs.getString(ConfigManager.SMTP_PASSWORD_KEY, null))
        assertEquals("legacy-secret", legacyPrefs.getString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, null))
        assertEquals("previous-secret", config.smtpPassword)
    }

    @Test
    fun `failed legacy cleanup after secure write is surfaced and previous value restored`() {
        val config = ConfigManager(legacyPrefs, securePrefs, codec)
        config.smtpPassword = "previous-secret"
        val previousCiphertext = securePrefs.getString(ConfigManager.SMTP_PASSWORD_KEY, null)
        legacyPrefs.failNextCommit = true

        assertThrows(SecretStorageException::class.java) {
            config.smtpPassword = "new-secret"
        }

        assertEquals(previousCiphertext, securePrefs.getString(ConfigManager.SMTP_PASSWORD_KEY, null))
        assertEquals("previous-secret", config.smtpPassword)
    }

    @Test
    fun `failed migration commit keeps legacy plaintext`() {
        legacyPrefs.edit().putString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, SMTP_SECRET).commit()
        securePrefs.failNextCommit = true

        val config = ConfigManager(legacyPrefs, securePrefs, codec)

        assertEquals(SMTP_SECRET, legacyPrefs.getString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, null))
        assertFalse(securePrefs.contains(ConfigManager.SMTP_PASSWORD_KEY))
        assertEquals(SMTP_SECRET, config.smtpPassword)
    }

    @Test
    fun `failed migration encryption keeps legacy plaintext`() {
        legacyPrefs.edit().putString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, SMTP_SECRET).commit()
        codec.encryptFailure = IllegalStateException("encryption failed")

        val config = ConfigManager(legacyPrefs, securePrefs, codec)

        assertEquals(SMTP_SECRET, legacyPrefs.getString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, null))
        assertFalse(securePrefs.contains(ConfigManager.SMTP_PASSWORD_KEY))
        assertEquals(SMTP_SECRET, config.smtpPassword)
    }

    @Test
    fun `corrupted ciphertext falls back to legacy plaintext`() {
        securePrefs.edit().putString(ConfigManager.SMTP_PASSWORD_KEY, "not-valid-base64").commit()
        legacyPrefs.edit().putString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, SMTP_SECRET).commit()

        val config = ConfigManager(legacyPrefs, securePrefs, codec)

        assertEquals(SMTP_SECRET, legacyPrefs.getString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, null))
        assertEquals(SMTP_SECRET, config.smtpPassword)
    }

    @Test
    fun `corrupted ciphertext without legacy value returns empty`() {
        securePrefs.edit().putString(ConfigManager.SMTP_PASSWORD_KEY, "not-valid-base64").commit()

        val config = ConfigManager(legacyPrefs, securePrefs, codec)

        assertEquals("", config.smtpPassword)
    }

    @Test
    fun `wrong key falls back to legacy plaintext`() {
        val encrypted = codec.encrypt(SMTP_SECRET, ConfigManager.SMTP_PASSWORD_KEY)
        securePrefs.edit().putString(ConfigManager.SMTP_PASSWORD_KEY, encrypted).commit()
        legacyPrefs.edit().putString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, "legacy-secret").commit()
        val differentCodec = TestAesGcmCodec()

        val config = ConfigManager(legacyPrefs, securePrefs, differentCodec)

        assertEquals("legacy-secret", legacyPrefs.getString(ConfigManager.LEGACY_SMTP_PASSWORD_KEY, null))
        assertEquals("legacy-secret", config.smtpPassword)
    }

    @Test
    fun `wrong key without legacy value returns empty`() {
        val encrypted = codec.encrypt(SMTP_SECRET, ConfigManager.SMTP_PASSWORD_KEY)
        securePrefs.edit().putString(ConfigManager.SMTP_PASSWORD_KEY, encrypted).commit()
        val differentCodec = TestAesGcmCodec()

        val config = ConfigManager(legacyPrefs, securePrefs, differentCodec)

        assertEquals("", config.smtpPassword)
    }

    private class TestAesGcmCodec : SecretCodec {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val encryptAssociatedData = mutableListOf<String>()
        var encryptFailure: Exception? = null

        override fun encrypt(plainText: String, associatedData: String): String {
            encryptFailure?.let { throw it }
            encryptAssociatedData += associatedData
            return AesGcmCipher.encrypt(key, plainText, associatedData)
        }

        override fun decrypt(cipherText: String, associatedData: String): String =
            AesGcmCipher.decrypt(key, cipherText, associatedData)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any>()
        var failNextCommit = false

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
            key?.let { values[it] as? String } ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            val stored = key?.let { values[it] as? Set<String> }
            return stored?.toMutableSet() ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int =
            key?.let { values[it] as? Int } ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            key?.let { values[it] as? Long } ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            key?.let { values[it] as? Float } ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            key?.let { values[it] as? Boolean } ?: defValue

        override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?
            ): SharedPreferences.Editor = put(key, values?.toSet())

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)

            override fun remove(key: String?): SharedPreferences.Editor = put(key, null)

            override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

            override fun commit(): Boolean {
                if (failNextCommit) {
                    failNextCommit = false
                    return false
                }
                if (clearRequested) values.clear()
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                pending.clear()
                clearRequested = false
                return true
            }

            override fun apply() {
                commit()
            }

            private fun put(key: String?, value: Any?): SharedPreferences.Editor {
                requireNotNull(key)
                pending[key] = value
                return this
            }
        }
    }

    private companion object {
        const val SMTP_SECRET = "smtp-secret-123456"
        const val API_SECRET = "llm-api-key-abcdef"
    }
}
