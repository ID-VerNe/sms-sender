# Architecture

Architecture overview of the SmsSender app — module dependencies, data flow, tech stack, and key design decisions.

## Module Dependencies

Module dependency graph showing how the application components connect.

```
SmsNotificationListener ──→ ExtractionUtils ──→ PickupCodeExtractor
                    │              │
                    ↓              ↓
              MailSender ←── ConfigManager
                    │
                    ↓
               database (Room)
                   
MainActivity ──→ ConfigManager, MailSender, database
ForwardingService ──→ 独立前台服务，无业务依赖
BootReceiver ──→ ConfigManager, ForwardingService
```

## Data Flow

The SMS capture and forwarding pipeline from notification to email delivery.

1. **SMS 捕获路径**：系统短信通知 → `SmsNotificationListener.onNotificationPosted()` → 解析 sender/body → `ExtractionUtils.getSmartSubject()` 提取主题 → `MailSender.sendEmail()` 发送
2. **配置路径**：`MainActivity.SettingsPanel()` → `ConfigManager` 属性设置 → SharedPreferences 持久化
3. **转发规则匹配**：`SmsNotificationListener` → `ForwardingRuleDao.getEnabledRules()` → 遍历匹配关键词 → 多目标邮箱发送

## Tech Stack

Technology choices and rationale for each component.

| 组件 | 选型 | 理由 |
|------|------|------|
| UI | Jetpack Compose + Material 3 | 现代声明式 UI，减少模板代码 |
| 数据库 | Room (SQLite) | 官方推荐，编译时 SQL 校验 |
| 邮件 | Jakarta Mail (com.sun.mail) | Android 兼容，支持 SSL/STARTTLS |
| 异步 | Kotlin Coroutines | 官方推荐，轻量线程 |
| 提取 | 启发式正则 | 0 网络依赖，毫秒级响应，100% 隐私 |

## Key Design Decisions

Key architectural decisions that shaped the project.

1. **通知监听优先于广播接收**：Android 14+ 限制后台广播接收，`SmsNotificationListener` 通过 NotificationListenerService 捕获通知栏短信，绕过系统对验证码短信的读取限制。
2. **启发式提取取代 LLM**：原方案依赖 LLM API 提取验证码，存在网络延迟、成本、隐私问题。现用 40+ 条编译时正则，0-2ms 完成提取，29/29 测试用例通过。
3. **取件码独立于验证码**：[[PickupCodeExtractor]] 与 [[ExtractionUtils]] 完全独立，避免混淆。取件码格式（带连字符、字母数字组合）与 OTP 格式（纯数字）有明显区别。
4. **SmsReceiver 已移除**：空广播接收器不再注册，所有短信捕获由 `SmsNotificationListener` 完成。