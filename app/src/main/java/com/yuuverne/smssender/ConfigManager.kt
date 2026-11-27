package com.yuuverne.smssender

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class ConfigManager internal constructor(
    private val prefs: SharedPreferences,
    private val securePrefs: SharedPreferences,
    private val secretCodec: SecretCodec
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE),
        AndroidKeystoreSecretCodec()
    )

    init {
        migrateLegacySecrets()
    }

    var smtpHost: String
        get() = prefs.getString("smtp_host", "smtp.qq.com") ?: "smtp.qq.com"
        set(value) = prefs.edit().putString("smtp_host", value).apply()

    var smtpPort: String
        get() = prefs.getString("smtp_port", "465") ?: "465"
        set(value) = prefs.edit().putString("smtp_port", value).apply()

    var smtpEmail: String
        get() = prefs.getString("smtp_email", "") ?: ""
        set(value) = prefs.edit().putString("smtp_email", value).apply()

    var smtpPassword: String
        get() = readSecret(SMTP_PASSWORD_KEY, LEGACY_SMTP_PASSWORD_KEY)
        set(value) = writeSecret(SMTP_PASSWORD_KEY, LEGACY_SMTP_PASSWORD_KEY, value)

    var receiverEmail: String
        get() = prefs.getString("receiver_email", "") ?: ""
        set(value) = prefs.edit().putString("receiver_email", value).apply()

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", false)
        set(value) = prefs.edit().putBoolean("service_enabled", value).apply()

    var useSsl: Boolean
        get() = prefs.getBoolean("use_ssl", true)
        set(value) = prefs.edit().putBoolean("use_ssl", value).apply()

    // LLM 相关配置
    var useLlm: Boolean
        get() = prefs.getBoolean("use_llm", false)
        set(value) = prefs.edit().putBoolean("use_llm", value).apply()

    var llmApiUrl: String
        get() = prefs.getString("llm_api_url", "https://open.bigmodel.cn/api/paas/v4/chat/completions") ?: ""
        set(value) = prefs.edit().putString("llm_api_url", value).apply()

    var llmApiKey: String
        get() = readSecret(LLM_API_KEY_KEY, LEGACY_LLM_API_KEY_KEY)
        set(value) = writeSecret(LLM_API_KEY_KEY, LEGACY_LLM_API_KEY_KEY, value)

    var llmModelId: String
        get() = prefs.getString("llm_model_id", "GLM-4.6V-Flash") ?: "GLM-4.6V-Flash"
        set(value) = prefs.edit().putString("llm_model_id", value).apply()

    internal fun migrateLegacySecrets() {
        migrateLegacySecret(SMTP_PASSWORD_KEY, LEGACY_SMTP_PASSWORD_KEY)
        migrateLegacySecret(LLM_API_KEY_KEY, LEGACY_LLM_API_KEY_KEY)
    }

    private fun readSecret(secureKey: String, legacyKey: String): String {
        val encrypted = securePrefs.getString(secureKey, null)
        if (encrypted != null) {
            return try {
                secretCodec.decrypt(encrypted)
            } catch (e: Exception) {
                Log.w(TAG, "无法解密敏感配置，尝试读取旧版配置: $legacyKey")
                prefs.getString(legacyKey, "") ?: ""
            }
        }
        return prefs.getString(legacyKey, "") ?: ""
    }

    private fun writeSecret(secureKey: String, legacyKey: String, value: String) {
        if (value.isEmpty()) {
            securePrefs.edit().remove(secureKey).apply()
            prefs.edit().remove(legacyKey).apply()
            return
        }

        try {
            val encrypted = secretCodec.encrypt(value)
            securePrefs.edit().putString(secureKey, encrypted).apply()
            prefs.edit().remove(legacyKey).apply()
        } catch (e: Exception) {
            Log.e(TAG, "无法加密保存敏感配置: $legacyKey")
        }
    }

    private fun migrateLegacySecret(secureKey: String, legacyKey: String) {
        if (securePrefs.contains(secureKey)) {
            val encrypted = securePrefs.getString(secureKey, null)
            if (encrypted != null) {
                try {
                    secretCodec.decrypt(encrypted)
                    prefs.edit().remove(legacyKey).apply()
                } catch (e: Exception) {
                    Log.w(TAG, "已加密的敏感配置无法解密，保留旧版配置以便恢复: $legacyKey")
                }
                return
            }
        }

        val plainText = prefs.getString(legacyKey, null) ?: return
        if (plainText.isEmpty()) {
            prefs.edit().remove(legacyKey).apply()
            return
        }

        try {
            val encrypted = secretCodec.encrypt(plainText)
            val committed = securePrefs.edit().putString(secureKey, encrypted).commit()
            if (committed) {
                prefs.edit().remove(legacyKey).apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法迁移旧版敏感配置: $legacyKey")
        }
    }

    internal companion object {
        const val PREFS_NAME = "sms_sender_prefs"
        const val SECURE_PREFS_NAME = "sms_sender_secure_prefs"

        const val SMTP_PASSWORD_KEY = "smtp_password"
        const val LLM_API_KEY_KEY = "llm_api_key"
        const val LEGACY_SMTP_PASSWORD_KEY = "smtp_password"
        const val LEGACY_LLM_API_KEY_KEY = "llm_api_key"

        private const val TAG = "ConfigManager"
    }
}
