package com.yuuverne.smssender.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forwarded_sms")
data class SmsRecord(
    @PrimaryKey val smsId: Long, // 通知去重键与收件人计算出的稳定指纹（保留旧字段名以兼容数据库）
    val address: String, // 原始通知标题/发件人的 SHA-256 十六进制哈希
    val body: String, // 原始通知正文的 SHA-256 十六进制哈希
    val timestamp: Long,
    val forwardTime: Long = System.currentTimeMillis()
)
