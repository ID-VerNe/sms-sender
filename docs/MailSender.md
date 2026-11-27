# MailSender

SMTP 邮件发送模块，负责将短信内容通过配置的邮件服务器转发到目标邮箱。支持 SSL (465) 和 STARTTLS (587) 两种加密方式。

## SMTP Configuration

邮件发送依赖四组配置：SMTP 服务器地址（如 `smtp.qq.com`）、端口（默认 465）、发件邮箱地址和授权码。端口决定加密方式：465 端口启用 SSL，其他端口启用 STARTTLS。由 [[ConfigManager]] 提供。

## Session Management

`Session.getInstance()` 通过 `Authenticator` 回调提供发件邮箱凭证。`MimeMessage` 对象封装邮件头（发件人、收件人、主题、正文），通过 `Transport.send()` 发送。通知转发由 [[MailForwardWorker]] 调用同步的 `sendEmailOrThrow()`，由 Worker 负责重试和成功后记账；`sendEmail()` 回调封装保留给 [[MainActivity#SettingsPanel]] 的测试邮件路径。

## Dependencies

内部模块和外部库依赖。

- [[ConfigManager]] — 读取 SMTP 配置
- `com.sun.mail:android-mail` — Jakarta Mail Android 移植版
- `com.sun.mail:android-activation` — Java Activation Framework Android 移植版

## Consumed By

哪些模块使用本模块。

- [[MailForwardWorker]] — WorkManager 中执行通知邮件发送
- [[MainActivity#SettingsPanel]] — 测试邮件发送

## Error Conditions

该模块特有的错误条件。

- 未配置接收邮箱 → `IllegalArgumentException("未配置接收邮箱")`
- SMTP 认证失败 → 服务器返回 535 错误，堆栈包含 `AuthenticationFailedException`，Worker 按永久失败处理且不写去重记录
- 网络超时 → 连接超时 10s，读取超时 15s，Worker 按临时失败重试
- 端口配置错误 → 465 端口未启用 SSL 时服务器拒绝连接