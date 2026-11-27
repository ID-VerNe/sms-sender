# Architecture

Architecture overview of the SmsSender app — module dependencies, data flow, tech stack, and key design decisions.

## Module Dependencies

Module dependency graph showing how the application components connect.

```
SmsNotificationListener ──→ ExtractionUtils ──→ PickupCodeExtractor
          │               │
          │               └→ ConfigManager
          ├→ database (ForwardingRuleDao)
          └→ NotificationForwarder ──→ WorkManager ──→ MailForwardWorker
                                                          │
                                                          ├→ MailSender ──→ ConfigManager
                                                          └→ database (SmsDao)

MainActivity ──→ ConfigManager, MailSender (test mail), database
ForwardingService ──→ 独立前台服务，无业务依赖
BootReceiver ──→ ConfigManager, ForwardingService
```

## Data Flow

The SMS capture and forwarding pipeline from notification to email delivery.

1. **通知捕获**：系统短信通知 → `SmsNotificationListener.onNotificationPosted()` → 总开关、短信包名、正文选择及通知类型过滤 → `NotificationSnapshot`。
2. **主题与收件人**：`ExtractionUtils.getSmartSubject()` 生成主题；默认收件邮箱与命中关键词的转发规则收件人合并去重。
3. **异步入队**：`NotificationForwarder` 为每个收件人生成稳定的 work name，并通过 `WorkManager.enqueueUniqueWork(..., KEEP, ...)` 入队。待发送的重复回调由 KEEP 合并；此阶段不写 `SmsRecord`。
4. **Worker 投递**：`MailForwardWorker` 从 input data 读取收件人、主题、正文、`dedupKey`、`dedupWindowMs` 和记录字段，先按“通知去重键 + 收件人”查询成功记录；已存在则直接 `Result.success()`，不再调用 SMTP。
5. **成功记账**：只有 `MailSender.sendEmailOrThrow()` 真正返回后，Worker 才写入 `SmsRecord`。临时失败返回 `Result.retry()`；认证/地址/SMTP 5xx 等永久失败返回 `Result.failure()` 且不写记录，后续同 key 回调可重新入队重试。
6. **SMTP 凭证**：Worker 在发送时从 `ConfigManager` 读取 SMTP 配置；密码不进入 WorkManager input data。

## Deduplication Semantics

- **有通知 key**：`dedupKey` 由包名和 `StatusBarNotification.key` 组成，`dedupWindowMs = 0`，按收件人永久去重。同一 key 的标题或正文更新仍视为同一通知。
- **无通知 key**：`dedupKey` 使用包名、标题和正文的稳定 SHA-256 内容哈希，不使用固定时间分桶；`dedupWindowMs = 60_000`，Worker 按 `forwardTime` 查询滚动 60 秒窗口。
- **并发保护**：同名 unique work 的 KEEP 策略处理 pending Work；Worker 启动查重处理已成功记录；`INSERT OR IGNORE` 处理并发写入；进程内按指纹使用有界 striped lock 关闭 check/send/insert 竞态。
- **收件人隔离**：work name 和 `SmsRecord.smsId` 都包含收件人指纹，不同收件人独立去重、独立重试。

## Tech Stack

Technology choices and rationale for each component.

| 组件 | 选型 | 理由 |
|------|------|------|
| UI | Jetpack Compose + Material 3 | 现代声明式 UI，减少模板代码 |
| 数据库 | Room (SQLite) | 官方推荐，编译时 SQL 校验 |
| 邮件 | Jakarta Mail (com.sun.mail) | Android 兼容，支持 SSL/STARTTLS |
| 异步投递 | WorkManager + Kotlin Coroutines | 持久化队列、网络约束、退避重试，进程重启后可恢复 |
| 提取 | 启发式正则 | 0 网络依赖，毫秒级响应，100% 隐私 |

## Key Design Decisions

Key architectural decisions that shaped the project.

1. **通知监听优先于广播接收**：Android 14+ 限制后台广播接收，`SmsNotificationListener` 通过 NotificationListenerService 捕获通知栏短信，绕过系统对验证码短信的读取限制。
2. **WorkManager 负责可靠投递**：通知主路径不直接同步发送邮件，而是为每个收件人持久化入队；网络约束和退避重试避免临时故障丢邮件。
3. **成功后去重而非入队时去重**：pending 状态由 unique work 的 KEEP 策略去重，成功状态由 Room 记录去重，永久失败不落记录，因此同 key 回调可以重试。
4. **启发式提取取代 LLM**：原方案依赖 LLM API 提取验证码，存在网络延迟、成本、隐私问题。现用 40+ 条编译时正则，0-2ms 完成提取。
5. **取件码独立于验证码**：`PickupCodeExtractor` 与 `ExtractionUtils` 完全独立，避免混淆。
6. **保留通知过滤与生命周期保护**：总开关、正文选择、包名过滤、群摘要/持续通知过滤、`SupervisorJob` 和 `onDestroy()` 取消逻辑均保留；WorkManager 数据库仍从备份中排除。
