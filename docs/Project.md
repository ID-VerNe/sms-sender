# Project

**Tech Stack**: Kotlin + Jetpack Compose + Android SDK (minSdk 26) + Room (SQLite) + Jakarta Mail. SmsSender 是一个 Android 短信转发工具，通过通知栏监听捕获短信，经 SMTP 转发到指定邮箱。

## Modules

Modules 清单列出项目所有模块及其职责。

- [[MailSender]] — SMTP 邮件发送，支持 SSL/TLS
- [[ExtractionUtils]] — 验证码启发式提取，覆盖 20+ 语言
- [[PickupCodeExtractor]] — 快递取件码提取，40+ 正则，30+ 快递公司
- [[ConfigManager]] — SharedPreferences 配置管理
- [[SmsNotificationListener]] — 通知栏监听，捕获短信通知
- [[ForwardingService]] — 前台服务保活
- [[BootReceiver]] — 开机自启动
- [[MainActivity]] — Compose UI 主界面
- [[database]] — Room 数据库，转发记录与规则

## Dependency Graph

Module dependency graph showing relationships between all project modules.

依赖关系见 [[Architecture#Module Dependencies]]。