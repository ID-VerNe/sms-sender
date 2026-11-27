package com.yuuverne.smssender

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Input contract shared by the producer and [MailForwardWorker]. */
internal object NotificationWorkData {
    const val KEY_RECIPIENT = "recipient"
    const val KEY_SUBJECT = "subject"
    const val KEY_CONTENT = "content"
    const val KEY_DEDUP_KEY = "dedupKey"
    const val KEY_DEDUP_WINDOW_MS = "dedupWindowMs"
    const val KEY_ADDRESS = "address"
    const val KEY_BODY = "body"
    const val KEY_TIMESTAMP = "timestamp"

    fun create(
        recipient: String,
        subject: String,
        content: String,
        dedupKey: String,
        dedupWindowMs: Long,
        address: String,
        body: String,
        timestamp: Long
    ): Data = workDataOf(
        KEY_RECIPIENT to recipient,
        KEY_SUBJECT to subject,
        KEY_CONTENT to content,
        KEY_DEDUP_KEY to dedupKey,
        KEY_DEDUP_WINDOW_MS to dedupWindowMs,
        KEY_ADDRESS to address,
        KEY_BODY to body,
        KEY_TIMESTAMP to timestamp
    )
}

/**
 * Enqueues one recipient's notification mail. Pending work is deduplicated by
 * WorkManager's unique-work KEEP policy; this layer never writes SmsRecord.
 */
internal suspend fun enqueueUniqueNotificationWork(
    context: Context,
    workName: String,
    recipient: String,
    subject: String,
    content: String,
    dedupKey: String,
    dedupWindowMs: Long,
    address: String,
    body: String,
    timestamp: Long
) {
    val request = OneTimeWorkRequestBuilder<MailForwardWorker>()
        .setInputData(
            NotificationWorkData.create(
                recipient = recipient,
                subject = subject,
                content = content,
                dedupKey = dedupKey,
                dedupWindowMs = dedupWindowMs,
                address = address,
                body = body,
                timestamp = timestamp
            )
        )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .addTag(MailForwardWorker.TAG)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            workName,
            NotificationWorkPolicy.existingWorkPolicy,
            request
        )
        .await()
}
