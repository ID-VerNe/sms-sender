# Glossary

Glossary of key terms used in the SmsSender project, their definitions and relationships.

## 验证码 / OTP (One-Time Password)

短信中用于身份验证的一次性数字或字母数字码。格式通常为 4-8 位纯数字或字母数字组合。[[ExtractionUtils]] 从短信正文中提取。

## 取件码 (Pickup Code)

快递短信中的取件凭证，用于在驿站或快递柜取包裹。格式多样：纯数字、字母数字组合、带连字符的编号。由 [[PickupCodeExtractor]] 独立提取。

## 驿站 (Station)

快递代收点，如菜鸟驿站、妈妈驿站、丰巢等。取件码通常关联到特定的驿站，[[PickupCodeExtractor]] 会识别驿站名称。

## NotificationListenerService

Android 系统服务，允许应用监听系统通知栏的推送。[[SmsNotificationListener]] 通过此服务捕获短信应用的通知。

## Forwarding Rule

用户自定义的转发规则，包含关键词和目标邮箱。当短信正文匹配关键词时，转发到指定邮箱。由 [[database]] 存储。

## SMTP (Simple Mail Transfer Protocol)

邮件发送协议。[[MailSender]] 使用 SMTP 通过发件邮箱服务器发送邮件，支持 SSL (465) 和 STARTTLS (587) 两种加密方式。