package com.yuuverne.smssender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val config = ConfigManager(context)
            if (config.isServiceEnabled) {
                val serviceIntent = Intent(context, ForwardingService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
