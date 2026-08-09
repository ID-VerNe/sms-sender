package com.yuuverne.smssender

import android.util.Log
import kotlin.math.min

/**
 * 短信验证码/关键信息提取工具。
 *
 * 基于启发式正则匹配，不依赖任何外部 API。
 * 核心逻辑适配自 jd1378/otphelper (Apache-2.0) 的 CodeExtractor。
 *
 * 覆盖中英文及 20+ 语言的常见验证码格式。
 */
object ExtractionUtils {

    // ── 敏感词（关键词后面跟的数字很可能是验证码） ──────────────────────────
    private val sensitivePhrases = listOf(
        "code",
        "One[-\\s]Time[-\\s]Password",
        "\\bOTP\\W",
        "\\b2FA\\W",
        "Einmalkennwort",
        "contraseña",
        "c[oó]digo",
        "clave",
        "\\bel siguiente PIN\\W",
        // 中文
        "验证码",
        "校验码",
        "識別碼",
        "認證",
        "驗證",
        // 日语
        "コード",
        "パスワード",
        "認証番号",
        "ワンタイム",
        // 韩语
        "인증번호",
        // 俄语
        "код",
        "\\bпароль\\W",
        "Parol\\s+dlya\\s+podtverzhdeniya",
        // 土耳其语
        "\\bKodu\\W",
        "\\bKodunuz\\W",
        "\\b[sş]ifre:\\W",
        // 芬兰语
        "\\bvahvistuskoodi",
        "\\bkertakäyttökoodisi\\W",
        // 波兰语
        "\\bkod\\W",
        "\\bautoryzacji\\W",
        // 意大利语
        "\\bcodice\\W",
        // 其他
        "\\b(?:m|sms)?TAN\\W",
        "کد",
        "رمز",
        "סיסמ",
        "\\bהקוד\\W",
        "\\bקוד\\W",
        "\\bKodi\\W",
        "\\bKods\\W",
    )

    // ── 跳过短语（"amount" 后面的数字不是验证码） ──────────────────────────
    private val skipPhrases = listOf(
        "amount",
        "مقدار",
        "مبلغ",
        "برای",
        "-ارز",
        "[a-zA-Z0-9] [a-zA-Z0-9] [a-zA-Z0-9] [a-zA-Z0-9] ?",
    )

    // ── 货币指示符（金额后面的数字不是验证码） ────────────────────────────
    private val currencyIndicators = listOf(
        "USD", "EUR", "GBP", "[$€£]",
    )

    // ── 忽略短语（含"code"但不是验证码的常见词） ──────────────────────────
    private val ignoredPhrases = listOf(
        "discount code",
        "vscode",
        "versionCode",
        "unicode",
        "fancode",
        "encode",
        "decode",
        "barcode",
        "codex",
        "off",
        "تخفیف",
        "takhfif",
        "اشتباه وارد شده",
        "RatingCode",
    )

    // ── 清理模式（提取前去掉的噪音） ──────────────────────────────────────
    private val cleanupPhrases = listOf(
        "[a-zA-Z0-9][a-zA-Z0-9-]{0,61}\\.[a-zA-Z]{2,}(?:[.a-zA-Z]{0,3}(?=\\s+)|)", // 域名
        "['\"]",
        "Endziffer-\\d+",
        "Ending \\d+",
        "<#>",
        "share OTP",
    )

    // ── 编译正则（只初始化一次） ──────────────────────────────────────────
    private val generalCodeMatcher: Regex by lazy {
        val sensitive = sensitivePhrases.joinToString("|")
        val skip = skipPhrases.joinToString("|")
        val currency = currencyIndicators.joinToString("|")
        Regex(
            """($sensitive)(?:\s*(?!$skip)(?:[^\s:：܃︓﹕.'"\d٠-٩۰-۹]|[\d٠-٩۰-۹,\s]+(?:$currency)|[\d٠-٩۰-۹][^\d٠-٩۰-۹]))*\s*[:：܃︓﹕]?\s*(["'「]?)([\d٠-٩۰-۹a-zA-Z\-]{4,}|(?: [\d٠-٩۰-۹a-zA-Z]){4,}|)\1?(?:[^\d٠-٩۰-۹a-zA-Z]|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
    }

    private val specialCodeMatcher: Regex by lazy {
        val sensitive = sensitivePhrases.joinToString("|")
        Regex(
            """((?:[\d٠-٩۰-۹]-?){4,}(?=\s)|[\d٠-٩۰-۹ ]{4,}(?=\s)|[\d٠-٩۰-۹]{4,})[^:]*($sensitive)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
    }

    private val ignoredPhrasesRegex: Regex by lazy {
        val ignored = ignoredPhrases.joinToString("|")
        Regex("""\b($ignored)\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    }

    private val cleanupPhrasesRegex: Regex by lazy {
        val cleanup = cleanupPhrases.joinToString("|")
        Regex("""($cleanup)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    }

    /**
     * 从短信内容中提取验证码。
     *
     * @param body 短信正文
     * @return 提取到的验证码，如果没有则返回 null
     */
    fun extractCode(body: String): String? {
        if (body.isBlank()) return null

        // 1. 预过滤：忽略明显不是验证码的短语
        if (shouldIgnore(body)) return null

        // 2. 清理噪音
        val cleanStr = cleanup(body)

        // 3. 正向匹配：关键词 → 后面的数字
        val results = generalCodeMatcher.findAll(cleanStr)
            .filter { it.groups[3]?.value != null }
            .toList()

        if (results.isNotEmpty()) {
            var foundCode = results
                .find { it.groups[3]!!.value.isNotEmpty() }
                ?.groups?.get(3)?.value
                ?.replace(" ", "")
                ?.replace("-", "")

            if (foundCode.isNullOrEmpty()) {
                // 4. 反向匹配：数字 → 前面的关键词（兜底）
                foundCode = specialCodeMatcher.find(cleanStr)
                    ?.groups?.get(1)?.value
                    ?.replace(" ", "")
                    ?.replace("-", "")
            }

            if (!foundCode.isNullOrEmpty()) {
                return toEnglishNumbers(foundCode)
            }
        }

        return null
    }

    /**
     * 判断短信是否应该被忽略（不是验证码类短信）。
     */
    private fun shouldIgnore(str: String): Boolean {
        if (ignoredPhrases.isEmpty()) return false
        return ignoredPhrasesRegex.containsMatchIn(str)
    }

    /**
     * 清理短信中的噪音文本。
     */
    private fun cleanup(str: String): String {
        if (cleanupPhrases.isEmpty()) return str
        return str.replace(cleanupPhrasesRegex, "")
    }

    /**
     * 将波斯/阿拉伯数字转换为英文字符。
     */
    private fun toEnglishNumbers(number: String?): String? {
        if (number.isNullOrEmpty()) return null
        val chars = CharArray(number.length)
        for (i in number.indices) {
            var ch = number[i]
            when {
                ch.code in 0x0660..0x0669 -> ch = (ch.code - (0x0660 - '0'.code)).toChar()
                ch.code in 0x06F0..0x06F9 -> ch = (ch.code - (0x06F0 - '0'.code)).toChar()
            }
            chars[i] = ch
        }
        return String(chars)
    }

    /**
     * 生成邮件主题。
     *
     * 优先使用启发式提取验证码，提取失败时保留 LLM 作为降级方案（如果配置了）。
     *
     * @param config 配置管理器
     * @param sender 短信发送者
     * @param body 短信正文
     * @return 邮件主题
     */
    suspend fun getSmartSubject(config: ConfigManager, sender: String, body: String): String {
        // 1. 启发式提取验证码（0-2ms，本地，无网络，无成本）
        val heuristicCode = extractCode(body)
        if (heuristicCode != null) {
            Log.d("ExtractionUtils", "启发式提取成功: $heuristicCode")
            return "【提取：$heuristicCode】来自：$sender"
        }

        // 2. 尝试提取快递取件码（独立于验证码，避免混淆）
        val pickupResult = PickupCodeExtractor.extractPickupCode(body)
        if (pickupResult.code != null) {
            Log.d("ExtractionUtils", "取件码提取成功: ${pickupResult.code} (${pickupResult.company})")
            if (pickupResult.company != "其他快递") {
                return "【取件码：${pickupResult.code}】${pickupResult.company} - 来自：$sender"
            }
            return "【取件码：${pickupResult.code}】来自：$sender"
        }

        // 3. 降级：LLM 提取（仅在配置了 LLM 时）
        if (config.useLlm) {
            Log.d("ExtractionUtils", "启发式提取失败，降级到 LLM")
            val llmCode = extractWithLlm(config, body)
            if (llmCode != null) {
                return "【提取：$llmCode】来自：$sender"
            }
        }

        // 4. 兜底
        return "转发短信：$sender"
    }

    // ══════════════════════════════════════════════════════════════════════
    // 以下为 LLM 降级方案（保留原有逻辑，仅在用户配置了 LLM 时使用）
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun extractWithLlm(config: ConfigManager, body: String): String? {
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
            val url = java.net.URL(config.llmApiUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${config.llmApiKey}")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            val payload = org.json.JSONObject().apply {
                put("model", config.llmModelId)
                val messages = org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("role", "system")
                        put("content", """你是一个短信解析助手。请从短信中提取出验证码或关键数字。
                            如果是验证码，请提取。如果是付款通知，请提取金额。
                            必须以 JSON 格式输出，格式如下：{"code": "提取到的内容"}。
                            如果没有值得提取的数字，请输出 {"code": "NONE"}。
                            只输出 JSON，不要有任何其他文字。""".trimIndent())
                    })
                    put(org.json.JSONObject().apply {
                        put("role", "user")
                        put("content", body)
                    })
                }
                put("messages", messages)
                put("temperature", 0.1)
            }

            val writer = java.io.OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val jsonResponse = org.json.JSONObject(response)
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
            Log.e("ExtractionUtils", "LLM API Call Exception", e)
            null
        }
    }

    private fun parseJsonCode(raw: String): String? {
        return try {
            val cleanJson = raw.replace("```json", "").replace("```", "").trim()
            val json = org.json.JSONObject(cleanJson)
            val code = json.optString("code", "")
            if (code.isEmpty() || code.uppercase() == "NONE") null else code
        } catch (e: Exception) {
            null
        }
    }
}