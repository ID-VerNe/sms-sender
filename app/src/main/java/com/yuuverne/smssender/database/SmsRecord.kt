package com.yuuverne.smssender.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forwarded_sms")
data class SmsRecord(
    @PrimaryKey val smsId: Long, // 对应系统短信数据库的 _id
    val address: String,
    val body: String,
    val timestamp: Long,
    val forwardTime: Long = System.currentTimeMillis()
)
