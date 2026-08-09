# PickupCodeExtractor

快递取件码提取工具。从快递/驿站短信中提取取件码，识别快递公司名称，返回结构化结果。核心逻辑适配自 yimeifun/ExpressPickupHelper (MIT) 的 smsParser.js。

## Code Pattern Priority

40+ 条正则按优先级分为 10 级（P0-P10）：P0 匹配"请凭取件码"格式，P1 匹配"请凭xxx来取"格式，依次到 P10 的纯数字简短码（3-6 位）。命中即返回，不继续匹配低优先级。6 位纯数字兜底仅在短信包含快递上下文关键词时生效。

## Company Identification Strategy

三级策略识别快递公司/驿站：A) 前文中的 `【xxx】` 显式标记（最高优先级），B) 前文关键词匹配，C) 全文回退匹配。驿站名优先于快递公司名显示，同时命中时组合为 `驿站名（快递公司）` 格式。

## Keyword Tables

三张关键词表：`COMPANY_KEYWORDS`（30+ 驿站+快递公司，用于显示）、`STATION_KEYWORDS`（23 个驿站，用于物理地点识别）、`COURIER_KEYWORDS`（16 家快递公司，用于承运商识别）。`PRE_BLACKLIST` 过滤运营商/银行/通知类短信。

## Dependencies

内部模块和外部库依赖。

- 无内部模块依赖，完全独立
- `kotlin.text.Regex` (Kotlin 标准库)

## Consumed By

哪些模块使用本模块。

- [[ExtractionUtils]] — `getSmartSubject()` 在验证码提取失败后调用

## Error Conditions

该模块特有的错误条件。

- 短信正文为空 → 返回 `PickupCodeResult()`（code 为 null）
- 命中 PRE_BLACKLIST → 返回 `PickupCodeResult()`，不继续匹配
- 地址前缀中包含路/街/巷等关键词 → 跳过该关键词匹配，防止地址中的公司名干扰