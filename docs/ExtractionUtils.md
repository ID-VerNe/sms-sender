# ExtractionUtils

短信验证码启发式提取工具。基于编译时正则匹配，不依赖任何外部 API，覆盖中英文及 20+ 语言的常见验证码格式。核心逻辑适配自 jd1378/otphelper (Apache-2.0) 的 CodeExtractor。

## Heuristic Extraction Pipeline

`extractCode()` 的 4 步流程：预过滤（`shouldIgnore` 排除 discount code 等噪音）→ 清理（`cleanup` 移除域名、引号等）→ 正向匹配（关键词后的数字，`generalCodeMatcher`）→ 反向兜底（数字前的关键词，`specialCodeMatcher`）。命中后通过 `toEnglishNumbers` 转换波斯/阿拉伯数字。

## Smart Subject Generation

`getSmartSubject()` 的 4 级优先级：启发式验证码 → 快递取件码 → LLM 降级（可选）→ 默认标题。每一级命中即返回，不继续尝试。取件码提取由独立的 [[PickupCodeExtractor]] 处理，互不干扰。

## Language Coverage

正则表达式覆盖 12 个语族的验证码关键词：中文（验证码/校验码）、英语（code/OTP/2FA）、日语（コード/認証番号）、韩语（인증번호）、俄语（код/пароль）、土耳其语、芬兰语、波兰语、意大利语、阿拉伯语、波斯语、希伯来语。

## LLM Fallback

当启发式提取失败且用户配置了 LLM 时，可选的降级方案。`extractWithLlm()` 调用外部 API 进行提取，最多重试 3 次。启用后需配置 API URL、Key 和 Model ID，由 [[ConfigManager]] 管理。

## Dependencies

内部模块和外部库依赖。

- [[PickupCodeExtractor]] — 取件码提取（`getSmartSubject` 第二步调用）
- [[ConfigManager]] — LLM 降级方案配置检测
- `org.json` (Android SDK) — LLM API 响应解析

## Consumed By

哪些模块使用本模块。

- [[SmsNotificationListener]] — `onNotificationPosted()` 中调用 `getSmartSubject()` 生成邮件主题

## Error Conditions

该模块特有的错误条件。

- 短信正文为空 → 返回 `null`（无提取结果）
- LLM API 调用失败 → 静默忽略，返回 `null`，不阻塞转发流程
- LLM 返回格式异常 → 解析失败后返回 `null`