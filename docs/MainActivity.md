# MainActivity

Compose UI 主界面。提供三 Tab 导航（状态/规则/设置），包含 Compose 组件和辅助函数。

## Navigation

三 Tab 底部导航栏：状态页（`StatusPanel`）显示运行状态和转发目标，规则页（`RulesPanel`）管理关键词转发规则，设置页（`SettingsPanel`）配置 SMTP 和 LLM 参数。

## Notification Permission

`LaunchedEffect(Unit)` 在 Android 13+ (API 33) 动态请求 `POST_NOTIFICATIONS` 权限，确保应用可以创建前台服务通知。

## SettingsPanel

`SettingsPanel` 双向绑定 `ConfigManager` 属性：每个 `OutlinedTextField` 的 `onValueChange` 同时更新本地 state 和 SharedPreferences。SMTP 设置、接收邮箱、LLM 配置、系统保活开关均在此页面。

## RulesPanel

`RulesPanel` 通过 `AppDatabase.ruleDao().getAllRulesFlow()` 的 Flow 观察规则列表，支持添加（`AlertDialog` 弹窗输入关键词和目标邮箱）、启用/禁用（`Switch`）、删除（`IconButton`）操作。

## Helper Functions

顶层函数：`toggleService()` 启动/停止 [[ForwardingService]]，`isNotificationListenerEnabled()` 检查通知监听权限，`isIgnoringBatteryOptimizations()` 和 `requestIgnoreBatteryOptimizations()` 管理电池优化豁免。

## Dependencies

内部模块和外部库依赖。

- [[ConfigManager]] — 读写所有配置项
- [[MailSender]] — 测试邮件发送
- [[ForwardingService]] — 服务启停
- [[database#AppDatabase]] — 转发规则数据访问
- Jetpack Compose + Material 3 — UI 框架

## Consumed By

哪些模块使用本模块。

- 用户通过桌面图标启动

## Error Conditions

该模块特有的错误条件。

- 无特殊错误条件。`requestIgnoreBatteryOptimizations()` 在 `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 不可用时回退到 `Settings.ACTION_SETTINGS`。