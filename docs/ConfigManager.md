# ConfigManager

应用配置管理模块。基于 SharedPreferences 的键值存储，封装所有用户可配置项。每个属性对应一个 SharedPreferences 键，读写同步。

## SMTP Config

四项邮件服务器配置：`smtpHost`（默认 `smtp.qq.com`）、`smtpPort`（默认 `465`）、`smtpEmail`、`smtpPassword`。由 [[MailSender]] 在发送时读取。

## LLM Config

四项 AI 提取配置：`useLlm`（默认关闭）、`llmApiUrl`（默认智谱 API）、`llmApiKey`、`llmModelId`。仅在 [[ExtractionUtils]] 中启发式提取失败后使用。

## Service State

`isServiceEnabled` 控制转发总开关。[[BootReceiver]] 在开机自启时检查此值，决定是否启动 [[ForwardingService]]。

## Dependencies

内部模块和外部库依赖。

- `android.content.SharedPreferences` (Android SDK)

## Consumed By

哪些模块使用本模块。

- [[MainActivity#SettingsPanel]] — 读写所有配置项
- [[SmsNotificationListener]] — 读取 SMTP 配置和转发规则
- [[BootReceiver]] — 读取 `isServiceEnabled`
- [[MailSender]] — 读取 SMTP 配置
- [[ExtractionUtils]] — 读取 LLM 配置

## Error Conditions

该模块特有的错误条件。

- 无特殊错误条件。SharedPreferences 读写不会抛出异常，getter 在键不存在时返回默认值。