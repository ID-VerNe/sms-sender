package com.yuuverne.smssender

/**
 * 快递取件码提取工具。
 *
 * 从短信中提取取件码、识别快递公司/驿站名称。
 * 核心逻辑适配自 yimeifun/ExpressPickupHelper (MIT) 的 smsParser.js。
 *
 * 与 [extractCode] 完全独立，互不干扰。
 * - [extractCode] 提取验证码（OTP、验证码等）
 * - [extractPickupCode] 提取快递取件码（驿站取件码、货架号等）
 */
object PickupCodeExtractor {

    /**
     * 取件码解析结果。
     */
    data class PickupCodeResult(
        val company: String = "其他快递",
        val code: String? = null,
    )

    // ═══════════════════════════════════════════════════════════════════
    // 快递公司/驿站关键词表
    // ═══════════════════════════════════════════════════════════════════

    private data class KeywordEntry(val name: String, val keys: List<String>)

    /** 驿站 + 快递公司合集（用于 display） */
    private val COMPANY_KEYWORDS = listOf(
        // 驿站类
        KeywordEntry("心甜智能柜", listOf("心甜智能柜", "新田智能柜", "心甜", "新田")),
        KeywordEntry("菜鸟驿站", listOf("菜鸟驿站取件", "菜鸟驿站", "菜鸟")),
        KeywordEntry("妈妈驿站", listOf("妈妈驿站", "妈妈驿")),
        KeywordEntry("兔喜生活", listOf("兔喜生活", "兔喜超市", "兔喜")),
        KeywordEntry("快宝驿站", listOf("快宝驿站", "快宝")),
        KeywordEntry("驿收发", listOf("驿收发驿站", "驿收发")),
        KeywordEntry("熊猫快收", listOf("熊猫快收")),
        KeywordEntry("熊猫驿站", listOf("熊猫驿站")),
        KeywordEntry("小兵驿站", listOf("小兵驿站", "小兵")),
        KeywordEntry("蓝店", listOf("蓝店快递", "蓝店")),
        KeywordEntry("欢猫驿站", listOf("欢猫驿站", "欢猫")),
        KeywordEntry("小象驿站", listOf("小象驿站", "小象")),
        KeywordEntry("邻里驿站", listOf("邻里驿站", "邻里")),
        KeywordEntry("拼多多驿站", listOf("拼多多驿站", "拼多多代收", "多多买菜", "拼多多")),
        KeywordEntry("多多驿站", listOf("多多驿站")),
        KeywordEntry("申通喵站", listOf("申通快递", "申通喵站", "喵站", "申通")),
        KeywordEntry("喵柜", listOf("申通喵柜", "喵柜")),
        KeywordEntry("丰巢", listOf("丰巢快递柜", "丰巢智能柜", "丰巢")),
        KeywordEntry("速递易", listOf("中邮速递易", "速递易")),
        KeywordEntry("云柜", listOf("江苏云柜", "云柜")),
        KeywordEntry("富友", listOf("富友快递柜", "富友收件宝", "富友")),
        KeywordEntry("近邻宝", listOf("近邻宝")),
        KeywordEntry("韵达快递超市", listOf("韵达快递超市", "韵达超市", "韵达")),
        // 快递公司
        KeywordEntry("圆通速递", listOf("圆通快递", "圆通速递", "圆通")),
        KeywordEntry("中通快递", listOf("中通快递", "中通")),
        KeywordEntry("顺丰速运", listOf("顺丰速运", "顺丰快递", "顺丰", "SF")),
        KeywordEntry("极兔速递", listOf("极兔速递", "极兔快递", "极兔", "J&T")),
        KeywordEntry("中国邮政", listOf("邮政快递包裹", "中国邮政", "邮政速递", "邮政", "EMS")),
        KeywordEntry("百世快递", listOf("百世快递", "百世汇通", "百世")),
        KeywordEntry("德邦快递", listOf("德邦快递", "德邦物流", "德邦")),
        KeywordEntry("天天快递", listOf("天天快递", "天天")),
        KeywordEntry("优速快递", listOf("优速快递", "优速")),
        KeywordEntry("丹鸟", listOf("丹鸟物流", "菜鸟直送", "丹鸟")),
        KeywordEntry("安能快递", listOf("安能快递", "安能物流", "安能")),
        KeywordEntry("宅急送", listOf("宅急送")),
        KeywordEntry("苏宁快递", listOf("苏宁快递", "苏宁物流", "苏宁")),
        KeywordEntry("京东快递", listOf("京东快递", "京东")),
    )

    /** 驿站/代收点关键词（取件物理地点） */
    private val STATION_KEYWORDS = listOf(
        KeywordEntry("心甜智能柜", listOf("心甜智能柜", "新田智能柜", "心甜", "新田")),
        KeywordEntry("菜鸟驿站", listOf("菜鸟驿站", "菜鸟驿")),
        KeywordEntry("妈妈驿站", listOf("妈妈驿站", "妈妈驿")),
        KeywordEntry("兔喜生活", listOf("兔喜生活", "兔喜")),
        KeywordEntry("快宝驿站", listOf("快宝驿站", "快宝驿")),
        KeywordEntry("驿收发", listOf("驿收发驿站", "驿收发")),
        KeywordEntry("熊猫快收", listOf("熊猫快收")),
        KeywordEntry("熊猫驿站", listOf("熊猫驿站")),
        KeywordEntry("小兵驿站", listOf("小兵驿站", "小兵")),
        KeywordEntry("蓝店", listOf("蓝店")),
        KeywordEntry("欢猫驿站", listOf("欢猫驿站", "欢猫驿")),
        KeywordEntry("小象驿站", listOf("小象驿站", "小象")),
        KeywordEntry("邻里驿站", listOf("邻里驿站", "邻里")),
        KeywordEntry("多多驿站", listOf("多多驿站")),
        KeywordEntry("拼多多驿站", listOf("拼多多驿站", "拼多多")),
        KeywordEntry("申通喵站", listOf("申通喵站", "喵站")),
        KeywordEntry("喵柜", listOf("喵柜", "喵")),
        KeywordEntry("丰巢", listOf("丰巢柜", "丰巢")),
        KeywordEntry("速递易", listOf("速递易")),
        KeywordEntry("云柜", listOf("云柜")),
        KeywordEntry("富友", listOf("富友")),
        KeywordEntry("近邻宝", listOf("近邻宝")),
        KeywordEntry("韵达快递超市", listOf("韵达快递超市", "韵达超市")),
    )

    /** 快递公司关键词（运单承运商） */
    private val COURIER_KEYWORDS = listOf(
        KeywordEntry("顺丰速运", listOf("顺丰速运", "顺丰快递", "顺丰")),
        KeywordEntry("中通快递", listOf("中通快递", "中通")),
        KeywordEntry("圆通速递", listOf("圆通速递", "圆通")),
        KeywordEntry("韵达快递", listOf("韵达快递", "韵达")),
        KeywordEntry("申通快递", listOf("申通快递", "申通")),
        KeywordEntry("极兔速递", listOf("极兔速递", "极兔", "J&T")),
        KeywordEntry("中国邮政", listOf("中国邮政", "EMS", "邮政快递包裹", "邮政")),
        KeywordEntry("百世快递", listOf("百世快递", "百世")),
        KeywordEntry("德邦快递", listOf("德邦快递", "德邦")),
        KeywordEntry("天天快递", listOf("天天快递", "天天")),
        KeywordEntry("优速快递", listOf("优速快递", "优速")),
        KeywordEntry("丹鸟", listOf("丹鸟", "菜鸟直送")),
        KeywordEntry("安能快递", listOf("安能快递", "安能")),
        KeywordEntry("宅急送", listOf("宅急送")),
        KeywordEntry("苏宁快递", listOf("苏宁快递", "苏宁")),
        KeywordEntry("京东快递", listOf("京东快递", "京东")),
    )

    // ═══════════════════════════════════════════════════════════════════
    // 取件码正则（按优先级从高到低，共 40+ 条）
    // ═══════════════════════════════════════════════════════════════════

    private val CODE_PATTERNS = listOf(
        // P0: 请凭取件码 / 凭取件码 / 请凭码
        Regex("""请凭取件码\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""凭取件码\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""请凭码\s*([A-Za-z0-9-]{2,14})"""),

        // P1: 请凭xxx来取 / 请凭xxx取件 / 请凭xxx领取 / 请凭xxx到
        Regex("""请凭\s*([A-Za-z0-9-]{2,14})\s*来取"""),
        Regex("""请凭\s*([A-Za-z0-9-]{2,14})\s*取件"""),
        Regex("""请凭\s*([A-Za-z0-9-]{2,14})\s*领取"""),
        Regex("""请凭\s*([A-Za-z0-9-]{2,14})\s*到"""),
        Regex("""请凭\s*([A-Za-z0-9-]{2,14})"""),

        // P2: 凭xxx到 / 凭xxx来取 / 凭xxx领取 / 凭xxx取件
        Regex("""凭\s*([A-Za-z0-9-]{2,14})\s*到"""),
        Regex("""凭\s*([A-Za-z0-9-]{2,14})\s*来取"""),
        Regex("""凭\s*([A-Za-z0-9-]{2,14})\s*领取"""),
        Regex("""凭\s*([A-Za-z0-9-]{2,14})\s*取件"""),

        // P3: 取件码xxx / 取件码为xxx / 取件码是xxx / 取件编号
        Regex("""取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""取件码为\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""取件码是\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""取件编号\s*([A-Za-z0-9-]{2,14})"""),

        // P4: 提取码 / 取货码 / 提货码
        Regex("""提取码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""取货码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""提货码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),

        // P5: 各驿站特殊格式
        Regex("""妈妈驿站[^\n]*取货码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""妈妈驿站[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""妈妈驿站[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""驿收发[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""快宝驿站[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""快宝驿站[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""欢猫驿站[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""欢猫驿站[^\n]*取货码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""欢猫驿站[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""兔喜生活[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""兔喜生活[^\n]*取货码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""邻里驿站[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""邻里驿站[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""喵柜[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""喵柜[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),

        // P6: 各快递公司特殊格式
        Regex("""申通快递[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""申通快递[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""中通快递[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""中通快递[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""圆通速递[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""圆通速递[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""韵达快递[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""韵达快递[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""顺丰[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""顺丰速运[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""丰巢[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""丰巢[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""邮政快递包裹[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""邮政快递包裹[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""中国邮政[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""极兔速递[^\n]*取件码[：:：]?\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""极兔速递[^\n]*码[：:：]\s*([A-Za-z0-9-]{2,14})"""),

        // P7: 通用简化：码xxx / 凭xxx
        Regex("""码[：:：]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""码\s+([A-Za-z0-9-]{2,14})"""),
        Regex("""凭\s*([A-Za-z0-9-]{2,14})"""),

        // P8: 货架/柜子/箱号格式
        Regex("""货架号[：:]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""货架[：:]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""柜号[：:]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""柜子号[：:]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""箱号[：:]\s*([A-Za-z0-9-]{2,14})"""),
        Regex("""编号[：:]\s*([A-Za-z0-9-]{2,14})"""),

        // P9: 带括号格式
        Regex("""取件码\s*\(\s*([A-Za-z0-9-]{2,14})\s*\)"""),
        Regex("""码\s*\(\s*([A-Za-z0-9-]{2,14})\s*\)"""),

        // 英文格式
        Regex("""code[：:]\s*([A-Za-z0-9-]{2,14})""", RegexOption.IGNORE_CASE),
        Regex("""pickup[：:]\s*([A-Za-z0-9-]{2,14})""", RegexOption.IGNORE_CASE),

        // P10: 纯数字简短码（3-6位，丰巢柜子常见）
        Regex("""[柜柜子][^\d\n]*(\d{3,6})"""),
    )

    /** 独立 6 位纯数字兜底 */
    private val FALLBACK_SIX_DIGIT = Regex("""(?:^|[^\d])(\d{6})(?=[^\d]|$)""")

    /** 快递相关上下文关键词（用于 6 位纯数字兜底判断） */
    private val SMS_CONTEXT_KEYWORDS = listOf(
        "取件", "快递", "包裹", "驿站", "丰巢", "菜鸟",
        "取货", "提货", "派件", "入库", "到货",
        "货架", "柜子", "取货码", "取件码", "自提", "提件",
        "收发室", "物管", "物业", "前台", "门卫",
        "快递柜", "自提点", "代收点", "存放点", "取货点",
        "服务站", "网点", "门店", "超市", "便利店",
        "单号", "运单", "快递员", "配送", "揽收",
        "签收", "已送达", "待取", "待签收", "已投递",
        "速递易", "云柜", "富友", "近邻宝", "熊猫快收", "蓝店",
        "喵柜", "邻里驿站", "快宝驿站", "妈妈驿站", "欢猫驿站",
        "兔喜生活", "菜鸟驿站", "申通喵站", "驿收发",
        "天猫", "淘宝", "京东", "拼多多", "苏宁",
        "顺丰", "申通", "中通", "圆通", "韵达", "极兔", "邮政",
        "百世", "德邦", "天天", "EMS", "J&T",
        "送达", "投递", "派送", "到达", "放置", "存放", "前往",
        "保管", "暂时保管", "已到", "已存", "已放",
    )

    /** 前置黑名单（非快递短信直接排除） */
    private val PRE_BLACKLIST = listOf(
        "订购提醒", "成功订购", "已订购", "已成功订购", "订购成功",
        "流量日包", "流量包", "话费", "话费余额", "话费充值",
        "充值成功", "已充值", "已续费", "续费成功",
        "资费", "套餐", "流量不可结转", "流量不可共享", "流量不可转赠",
        "剩余流量", "国内通用流量", "流量分钟",
        "10086", "10010", "10000", "10011",
        "中国移动", "中国联通", "中国电信", "中国广电",
        "心级服务", "让爱连接",
        "银行", "信用卡", "还款", "转账成功", "消费提醒", "账单",
        "验证码", "您的验证码", "验证码为",
        "退订", "回T退", "回复TD", "回T", "请勿回复",
        "积分兑换", "抽奖", "中奖", "获奖",
    )

    /** 地址关键词（用于过滤地址中的公司名干扰） */
    private val ADDRESS_KEYWORDS = listOf(
        "路", "街", "巷", "弄", "栋", "幢", "楼", "单元", "室",
        "村", "镇", "乡", "县", "区", "市", "省",
        "对面", "旁边", "下行", "上行", "前行", "进来", "进去", "往前走",
        "花园", "广场", "公寓", "小区", "大厦", "商业", "步行街",
        "大道", "大街", "胡同", "里", "坊", "园", "苑", "阁",
        "快递柜", "暂存",
    )

    // ═══════════════════════════════════════════════════════════════════
    // 核心 API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 从短信中提取取件码。
     *
     * @param body 短信正文
     * @return 取件码解析结果，如果不是取件短信则返回 [PickupCodeResult] 且 code 为 null
     */
    fun extractPickupCode(body: String): PickupCodeResult {
        if (body.isBlank()) return PickupCodeResult()

        // 0. 前置黑名单检查
        if (PRE_BLACKLIST.any { body.contains(it) }) {
            return PickupCodeResult()
        }

        // 1. 预处理文本
        val normalized = body
            .replace("：", ":")
            .replace(Regex("""[「」『"'`]"""), "")
            .replace(Regex("""[ \t]{2,}"""), " ")

        // 2. 匹配取件码
        var code: String? = null
        for (pattern in CODE_PATTERNS) {
            val m = pattern.find(normalized)
            if (m != null && m.groupValues.size >= 2) {
                val captured = m.groupValues[1].trim()
                if (captured.isNotEmpty()) {
                    code = captured
                    break
                }
            }
        }

        // 兜底：独立 6 位数字（且短信与快递相关）
        if (code == null) {
            val m = FALLBACK_SIX_DIGIT.find(normalized)
            if (m != null && m.groupValues.size >= 2) {
                val hasCtx = SMS_CONTEXT_KEYWORDS.any { normalized.contains(it) }
                if (hasCtx) {
                    code = m.groupValues[1]
                }
            }
        }

        if (code == null) return PickupCodeResult()

        // 3. 以取件码位置为界，分割前文和后文
        val codeIdx = normalized.indexOf(code)
        val textForCompany = if (codeIdx >= 0) normalized.substring(0, codeIdx) else normalized

        // 4. 识别快递公司
        val company = identifyCompany(textForCompany, normalized)

        return PickupCodeResult(company = company, code = code)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 公司识别
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 识别快递公司/驿站名称。
     *
     * 策略：
     *   A) 前文中的【xxx】显式标记（最高优先级）
     *   B) 前文关键词匹配
     *   C) 全文回退匹配
     */
    private fun identifyCompany(beforeCode: String, fullText: String): String {
        // 策略 A：查找【xxx驿站 / 【xxx快递 / 【xxx邮政】等显式标记
        val markerRegex = Regex("""【([^】\n]{1,20}?(?:驿站|快递|速递|邮政|物流|服务站|快递柜|代收点|快收|生活))】""")
        val markerMatch = markerRegex.find(beforeCode)
        if (markerMatch != null) {
            val markerName = markerMatch.groupValues[1]
            val hit = findCompanyByMarker(markerName)
            if (hit != null) return hit
        }

        // 策略 B：前文关键词匹配
        val beforeHit = findCompanyInText(beforeCode, filterAddress = true)
        if (beforeHit != null) return beforeHit

        // 策略 C：全文回退匹配
        val fullHit = findCompanyInText(fullText, filterAddress = false)
        if (fullHit != null) return fullHit

        return "其他快递"
    }

    private fun findCompanyByMarker(marker: String): String? {
        return findStationAndCourier(marker, filterAddress = false)
    }

    private fun findCompanyInText(text: String, filterAddress: Boolean): String? {
        if (text.isBlank()) return null
        return findStationAndCourier(text, filterAddress)
    }

    /**
     * 统一的「驿站 + 快递公司」识别。
     * 驿站作为公司名，快递公司作为括号补充。
     */
    private fun findStationAndCourier(text: String, filterAddress: Boolean): String? {
        if (text.isBlank()) return null

        fun looksLikeAddress(prefix: String): Boolean {
            if (prefix.isBlank()) return false
            if (ADDRESS_KEYWORDS.any { prefix.contains(it) }) return true
            if (Regex("""[路街巷弄道]\S{0,8}[号栋楼室]$""").containsMatchIn(prefix)) return true
            if (Regex("""\d{1,5}[号栋单元]$""").containsMatchIn(prefix)) return true
            return false
        }

        var station: KeywordEntry? = null
        var courier: KeywordEntry? = null

        // 第一步：搜索驿站关键词
        for (entry in STATION_KEYWORDS) {
            for (key in entry.keys) {
                if (!text.contains(key)) continue
                if (filterAddress) {
                    val idx = text.indexOf(key)
                    val before = text.substring(maxOf(0, idx - 15), idx)
                    if (looksLikeAddress(before)) continue
                }
                station = entry
                break
            }
            if (station != null) break
        }

        // 第二步：搜索快递公司关键词
        for (entry in COURIER_KEYWORDS) {
            for (key in entry.keys) {
                if (!text.contains(key)) continue
                val idx = text.indexOf(key)
                val before = text.substring(maxOf(0, idx - 15), idx)
                if (looksLikeAddress(before)) continue
                courier = entry
                break
            }
            if (courier != null) break
        }

        // 第三步：组合返回
        return when {
            station != null && courier != null -> "${station.name}（${courier.name}）"
            station != null -> station.name
            courier != null -> courier.name
            else -> null
        }
    }
}