package com.yuuverne.smssender.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forwarded_sms")
data class SmsRecord(
    @PrimaryKey val smsId: Long, // 通知 key 与内容计算出的去重指纹（保留旧字段名以兼容数据库）
    val address: String,
    val body: String,
    val timestamp: Long,
    val forwardTime: Long = System.currentTimeMillis()
)
