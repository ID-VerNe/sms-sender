# database

Room 数据库模块。包含转发记录和转发规则两张表，提供 DAO 接口进行数据访问。

## AppDatabase

单例 Room 数据库，包含 `SmsRecord` 和 `ForwardingRule` 两个实体，版本号为 2。数据库不使用 `fallbackToDestructiveMigration()`；后续 schema 变更必须提供显式、非破坏性的 Room `Migration`，通过 `addMigrations()` 注册，避免清除用户数据。

## ForwardingRule

转发规则实体，包含 `keyword`（匹配关键词）、`targetEmail`（目标邮箱）、`isEnabled`（是否启用）三个字段。`ForwardingRuleDao` 提供 Flow 查询和 CRUD 操作。

## ForwardingRuleDao

DAO 接口，提供 `getAllRulesFlow()`（Flow 观察所有规则）、`getEnabledRules()`（挂起函数获取已启用规则）、`insertRule()`、`updateRule()`、`deleteRule()` 方法。

## SmsRecord

已成功投递的短信通知记录。`smsId` 保留原字段名，但语义是“通知 `dedupKey` + 收件人”的稳定 SHA-256 指纹，作为主键实现收件人级幂等。其他字段为：

- `address`：通知标题/发件人。
- `body`：通知正文。
- `timestamp`：通知发布时间。
- `forwardTime`：SMTP 真正成功的时间，也是滚动去重窗口的基准。

记录只由 `MailForwardWorker` 在 SMTP 发送成功后写入；入队、临时失败和永久失败均不写记录。

## SmsDao

DAO 接口提供：

- `isForwardedWithin(smsId, windowMs, now)`：查询某通知+收件人是否已成功。`windowMs = 0` 表示永久去重；正数表示 `forwardTime > now - windowMs` 的滚动窗口。
- `deleteExpired(smsId, cutoff)`：删除已滚出窗口的旧记录，使后续成功可以重新写入。
- `insert(record)`：使用 `OnConflictStrategy.IGNORE`（SQLite `INSERT OR IGNORE`），并发写入不会覆盖已有成功记录。
- `getAllForwarded()`：按成功时间倒序返回最近 100 条记录。

无通知 key 时，`dedupKey` 是包名、标题和正文的稳定内容哈希，时间窗为 60 秒；有 key 时时间窗为 0，永久去重。WorkManager 的 unique work 负责 pending 状态去重，Room 记录负责成功状态去重。

## Dependencies

内部模块和外部库依赖。

- `androidx.room:room-runtime` — Room 运行时
- `androidx.room:room-ktx` — Kotlin 协程扩展
- `androidx.room:room-compiler` — 编译时注解处理器

## Consumed By

哪些模块使用本模块。

- [[SmsNotificationListener]] — 查询转发规则
- [[MailForwardWorker]] — 启动查重、成功后写入去重记录
- [[MainActivity#RulesPanel]] — 管理转发规则

## Error Conditions

该模块特有的错误条件。

- 数据库迁移缺失或不兼容 → Room 抛出迁移异常，不会清除数据后重建
- SMTP 永久失败 → Worker 返回 `Result.failure()`，不写 `SmsRecord`，允许后续同 key 回调重试
- 并发成功写入 → `INSERT OR IGNORE` 保留第一条成功记录
