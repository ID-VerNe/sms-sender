package com.yuuverne.smssender

data class SmsInfo(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int // 1 是接收，2 是发送
)
