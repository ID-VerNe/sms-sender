package com.yuuverne.smssender

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ExtractionUtils {

    // 废弃正则，完全由 LLM 处理
    suspend fun extractWithLlm(config: ConfigManager, body: String): String? {
        if (!config.useLlm || config.llmApiKey.isEmpty()) return null
        
        var retryCount = 0
        val maxRetries = 3
        
        while (retryCount < maxRetries) {
            val result = callLlmApi(config, body)
            if (result != null) return result
            retryCount++
            Log.w("ExtractionUtils", "LLM 解析失败，正在进行第 $retryCount 次重试...")
            if (retryCount < maxRetries) kotlinx.coroutines.delay(1000)
        }
        return null
    }

    private fun callLlmApi(config: ConfigManager, body: String): String? {
        return try {
            val url = URL(config.llmApiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${config.llmApiKey}")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            val payload = JSONObject().apply {
                put("model", config.llmModelId)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", """你是一个短信解析助手。请从短信中提取出验证码或关键数字。
                            如果是验证码，请提取。如果是付款通知，请提取金额。
                            必须以 JSON 格式输出，格式如下：{"code": "提取到的内容"}。
                            如果没有值得提取的数字，请输出 {"code": "NONE"}。
                            只输出 JSON，不要有任何其他文字。""".trimIndent())
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", body)
                    })
                }
                put("messages", messages)
                put("temperature", 0.1)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)
                val rawContent = jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                
                parseJsonCode(rawContent)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ExtractionUtils", "API Call Exception", e)
            null
        }
    }

    private fun parseJsonCode(raw: String): String? {
        return try {
            val cleanJson = raw.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(cleanJson)
            val code = json.optString("code", "")
            if (code.isEmpty() || code.uppercase() == "NONE") null else code
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSmartSubject(config: ConfigManager, sender: String, body: String): String {
        // 直接尝试 LLM
        if (config.useLlm) {
            val llmCode = extractWithLlm(config, body)
            if (llmCode != null) {
                return "【提取：$llmCode】来自：$sender"
            }
        }
        return "转发短信：$sender"
    }
}