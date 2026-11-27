package com.yuuverne.smssender

import androidx.work.ExistingWorkPolicy

internal object NotificationWorkPolicy {
    val existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
}
