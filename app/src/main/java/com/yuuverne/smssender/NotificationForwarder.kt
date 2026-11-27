package com.yuuverne.smssender

import com.yuuverne.smssender.database.SmsDao
import com.yuuverne.smssender.database.SmsRecord

internal suspend fun enqueueNotificationIfNew(
    smsDao: SmsDao,
    record: SmsRecord,
    enqueue: suspend () -> Unit
): Boolean {
    if (smsDao.isForwarded(record.smsId)) return false
    enqueue()
    smsDao.insert(record)
    return true
}
