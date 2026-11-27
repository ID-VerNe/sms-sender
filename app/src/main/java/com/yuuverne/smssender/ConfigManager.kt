package com.yuuverne.smssender

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/** 敏感配置加密、提交或清理失败时抛出，调用方应保留界面输入并提示用户。 */
class SecretStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ConfigManager internal constructor(
    private val prefs: SharedPreferences,
    private val securePrefs: SharedPreferences,
    private val secretCodec: SecretCodec
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE),
        AndroidKeystoreSecretCodec
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
        get() = prefs.getBoolean("use_ssl", defaultUseSslForPort(smtpPort))
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

    internal fun migrateLegacySecrets() = synchronized(SECRET_LOCK) {
        migrateLegacySecret(SMTP_PASSWORD_KEY, LEGACY_SMTP_PASSWORD_KEY)
        migrateLegacySecret(LLM_API_KEY_KEY, LEGACY_LLM_API_KEY_KEY)
    }

    private fun readSecret(secureKey: String, legacyKey: String): String = synchronized(SECRET_LOCK) {
        val encrypted = securePrefs.getString(secureKey, null)
        if (encrypted != null) {
            return@synchronized try {
                secretCodec.decrypt(encrypted, secureKey)
            } catch (e: Exception) {
                Log.w(TAG, "无法解密敏感配置，尝试读取旧版配置: $legacyKey", e)
                prefs.getString(legacyKey, "") ?: ""
            }
        }
        prefs.getString(legacyKey, "") ?: ""
    }

    private fun writeSecret(secureKey: String, legacyKey: String, value: String) {
        synchronized(SECRET_LOCK) {
            if (value.isEmpty()) {
                removeSecret(secureKey, legacyKey)
                return
            }

            val previousEncrypted = securePrefs.getString(secureKey, null)
            try {
                val encrypted = secretCodec.encrypt(value, secureKey)
                if (!securePrefs.edit().putString(secureKey, encrypted).commit()) {
                    restoreSecureSecret(secureKey, previousEncrypted)
                    throw SecretStorageException("无法提交加密敏感配置: $secureKey")
                }
                if (!prefs.edit().remove(legacyKey).commit()) {
                    restoreSecureSecret(secureKey, previousEncrypted)
                    throw SecretStorageException("敏感配置已加密保存，但无法删除旧版明文: $legacyKey")
                }
            } catch (e: SecretStorageException) {
                Log.e(TAG, e.message ?: "无法加密保存敏感配置: $secureKey", e)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "无法加密保存敏感配置: $secureKey", e)
                throw SecretStorageException("无法加密保存敏感配置: $secureKey", e)
            }
        }
    }

    private fun removeSecret(secureKey: String, legacyKey: String) {
        synchronized(SECRET_LOCK) {
            val previousEncrypted = securePrefs.getString(secureKey, null)
            try {
                if (!securePrefs.edit().remove(secureKey).commit()) {
                    throw SecretStorageException("无法删除加密敏感配置: $secureKey")
                }
                if (!prefs.edit().remove(legacyKey).commit()) {
                    restoreSecureSecret(secureKey, previousEncrypted)
                    throw SecretStorageException("加密敏感配置已删除，但无法删除旧版明文: $legacyKey")
                }
            } catch (e: SecretStorageException) {
                Log.e(TAG, e.message ?: "无法删除敏感配置: $secureKey", e)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "无法删除敏感配置: $secureKey", e)
                throw SecretStorageException("无法删除敏感配置: $secureKey", e)
            }
        }
    }

    private fun restoreSecureSecret(secureKey: String, previousValue: String?) {
        val editor = securePrefs.edit()
        if (previousValue == null) {
            editor.remove(secureKey)
        } else {
            editor.putString(secureKey, previousValue)
        }
        if (!editor.commit()) {
            Log.w(TAG, "无法恢复先前的加密敏感配置: $secureKey")
        }
    }

    private fun migrateLegacySecret(secureKey: String, legacyKey: String) {
        val encrypted = securePrefs.getString(secureKey, null)
        if (encrypted != null) {
            try {
                secretCodec.decrypt(encrypted, secureKey)
                if (!prefs.edit().remove(legacyKey).commit()) {
                    Log.w(TAG, "已加密的敏感配置无法清理旧版明文: $legacyKey")
                }
            } catch (e: Exception) {
                Log.w(TAG, "已加密的敏感配置无法解密，保留旧版配置以便恢复: $legacyKey", e)
            }
            return
        }

        val plainText = prefs.getString(legacyKey, null) ?: return
        if (plainText.isEmpty()) {
            if (!prefs.edit().remove(legacyKey).commit()) {
                Log.w(TAG, "无法清理空的旧版敏感配置: $legacyKey")
            }
            return
        }

        try {
            val cipherText = secretCodec.encrypt(plainText, secureKey)
            if (!securePrefs.edit().putString(secureKey, cipherText).commit()) {
                Log.w(TAG, "无法迁移旧版敏感配置，加密数据提交失败: $legacyKey")
                return
            }
            if (!prefs.edit().remove(legacyKey).commit()) {
                Log.w(TAG, "敏感配置已迁移，但无法删除旧版明文: $legacyKey")
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法迁移旧版敏感配置: $legacyKey", e)
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

        /**
         * 进程级凭证事务锁，所有实例的迁移、读取、写入和删除都必须在此锁内完成。
         *
         * 锁顺序固定为 ConfigManager SECRET_LOCK -> AndroidKeystoreSecretCodec 的监视器；
         * codec 持有自身监视器时不会回调 ConfigManager，因此不存在反向获取顺序。
         */
        private val SECRET_LOCK = Any()
    }
}
