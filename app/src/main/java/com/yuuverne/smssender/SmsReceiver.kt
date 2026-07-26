package com.yuuverne.smssender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 不再通过广播处理，全部由 SmsNotificationListener 处理
    }
}