# BootReceiver

开机自启动广播接收器。监听系统 `BOOT_COMPLETED` 广播，在设备重启后自动启动 [[ForwardingService]]。

## Auto-Start on Boot

`onReceive()` 检查 `ConfigManager.isServiceEnabled` 的值，如果转发服务在重启前是开启状态，则调用 `startForegroundService()` 重新启动服务。

## Dependencies

内部模块和外部库依赖。

- [[ConfigManager]] — 检查服务是否已启用
- [[ForwardingService]] — 启动前台服务

## Consumed By

哪些模块使用本模块。

- 系统 `BOOT_COMPLETED` 广播触发

## Error Conditions

该模块特有的错误条件。

- 无特殊错误条件。`BroadcastReceiver.onReceive()` 有 10 秒执行限制，当前实现仅为条件判断和启动服务，在限制内完成。