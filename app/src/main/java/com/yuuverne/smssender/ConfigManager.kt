package com.yuuverne.smssender

import android.content.Context
import android.content.SharedPreferences

class ConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sms_sender_prefs", Context.MODE_PRIVATE)

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
        get() = prefs.getString("smtp_password", "") ?: ""
        set(value) = prefs.edit().putString("smtp_password", value).apply()

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
        get() = prefs.getString("llm_api_key", "") ?: ""
        set(value) = prefs.edit().putString("llm_api_key", value).apply()

    var llmModelId: String
        get() = prefs.getString("llm_model_id", "GLM-4.6V-Flash") ?: "GLM-4.6V-Flash"
        set(value) = prefs.edit().putString("llm_model_id", value).apply()
}
