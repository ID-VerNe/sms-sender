# database

Room 数据库模块。包含转发记录和转发规则两张表，提供 DAO 接口进行数据访问。

## AppDatabase

单例 Room 数据库，包含 `SmsRecord` 和 `ForwardingRule` 两个实体。版本号为 2，使用 `fallbackToDestructiveMigration()` 策略处理迁移不兼容。

## ForwardingRule

转发规则实体，包含 `keyword`（匹配关键词）、`targetEmail`（目标邮箱）、`isEnabled`（是否启用）三个字段。`ForwardingRuleDao` 提供 Flow 查询和 CRUD 操作。

## ForwardingRuleDao

DAO 接口，提供 `getAllRulesFlow()`（Flow 观察所有规则）、`getEnabledRules()`（挂起函数获取已启用规则）、`insertRule()`、`updateRule()`、`deleteRule()` 方法。

## SmsRecord

已转发短信记录，以系统短信 `_id` 为主键，记录地址、正文、时间戳和转发时间。

## SmsDao

DAO 接口，提供 `isDuplicate()`（检查 60 秒内相同地址+正文的记录）、`insert()`、`getAllForwarded()` 方法。

## Dependencies

内部模块和外部库依赖。

- `androidx.room:room-runtime` — Room 运行时
- `androidx.room:room-ktx` — Kotlin 协程扩展
- `androidx.room:room-compiler` — 编译时注解处理器

## Consumed By

哪些模块使用本模块。

- [[SmsNotificationListener]] — 查询转发规则
- [[MainActivity#RulesPanel]] — 管理转发规则

## Error Conditions

该模块特有的错误条件。

- 数据库迁移不兼容 → `fallbackToDestructiveMigration()` 清除所有数据后重建表