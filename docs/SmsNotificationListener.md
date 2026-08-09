# SmsNotificationListener

通知栏监听服务。通过 `NotificationListenerService` 捕获系统通知栏中的短信通知，解析后触发邮件转发流程。这是短信捕获的主要入口。

## SMS Capture Flow

`onNotificationPosted()` 被触发时：检查包名是否包含 `mms` 或 `messaging` → 从 `Notification.extras` 提取标题（发件人）和正文 → 调用 [[ExtractionUtils]] 的 `getSmartSubject()` 生成主题 → 查询 [[database]] 中的转发规则 → 遍历目标邮箱列表发送邮件。

## Rule Matching

通过 `ForwardingRuleDao.getEnabledRules()` 获取所有已启用的规则，逐条匹配短信正文中的关键词。匹配成功后，将该规则的目标邮箱加入收件人列表（去重）。默认接收邮箱始终包含在列表中。

## Package Filter

当前仅过滤包名包含 `mms` 或 `messaging` 的应用。这是简化的系统短信应用识别策略，可能无法覆盖所有定制 ROM（如 Flyme 的短信应用包名不同）。

## Dependencies

内部模块和外部库依赖。

- [[ExtractionUtils]] — 生成邮件主题
- [[MailSender]] — 发送邮件
- [[ConfigManager]] — 读取 SMTP 配置和默认接收邮箱
- [[database#ForwardingRuleDao]] — 查询转发规则

## Consumed By

哪些模块使用本模块。

- 系统 `NotificationListenerService` 框架调用

## Error Conditions

该模块特有的错误条件。

- 通知正文为空 → 跳过，不转发
- 无有效收件人邮箱 → 日志警告"无有效收件人邮箱，取消转发"
- 个别邮箱发送失败 → 不影响其他邮箱的发送，仅记录错误日志