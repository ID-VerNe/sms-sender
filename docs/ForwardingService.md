# ForwardingService

前台服务保活模块。通过 `Service` 运行在前台，显示持久化通知以保持进程存活，防止系统在后台回收应用。

## Foreground Service

`onStartCommand()` 创建并显示前台通知（标题"SmsSender 运行中"，内容"正在通过通知栏监听短信..."），设置 `FOREGROUND_SERVICE_TYPE_DATA_SYNC` 类型以符合 Android 14+ 要求。返回 `START_STICKY` 确保服务被杀死后自动重启。

## Notification Channel

`createNotificationChannel()` 创建 `SmsForwarderChannel` 通道，优先级 `IMPORTANCE_LOW`，确保通知不产生声音和振动干扰用户。

## Dependencies

内部模块和外部库依赖。

- 无业务依赖，仅为进程保活提供系统级支持

## Consumed By

哪些模块使用本模块。

- [[MainActivity#Helper Functions]] — 开启/关闭转发时启动/停止服务
- [[BootReceiver]] — 开机自启时启动服务

## Error Conditions

该模块特有的错误条件。

- Android 8+ 要求 `startForegroundService()` 必须在 5 秒内调用 `startForeground()`，超时触发 `RemoteServiceException`。当前实现中 `startForeground()` 在 `onStartCommand()` 方法体内立即调用，满足时间要求。