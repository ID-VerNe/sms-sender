package com.yuuverne.smssender

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MailForwardWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val recipient = inputData.getString(KEY_RECIPIENT)
        val subject = inputData.getString(KEY_SUBJECT)
        val content = inputData.getString(KEY_CONTENT)

        if (recipient.isNullOrBlank() || subject == null || content == null) {
            Log.e(TAG, "邮件队列数据不完整，无法发送")
            return Result.failure()
        }

        return try {
            withContext(Dispatchers.IO) {
                val config = ConfigManager(applicationContext)
                MailSender.sendEmailOrThrow(
                    config = config,
                    subject = subject,
                    content = content,
                    targetOverride = recipient
                )
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "邮件发送失败，尝试次数: ${runAttemptCount + 1}, 原因: ${e.message}", e)
            if (MailRetryPolicy.shouldRetry(e)) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "MailForwardWorker"
        private const val KEY_RECIPIENT = "recipient"
        private const val KEY_SUBJECT = "subject"
        private const val KEY_CONTENT = "content"

        suspend fun enqueueUnique(
            context: Context,
            workName: String,
            recipient: String,
            subject: String,
            content: String
        ) {
            val request = OneTimeWorkRequestBuilder<MailForwardWorker>()
                .setInputData(
                    workDataOf(
                        KEY_RECIPIENT to recipient,
                        KEY_SUBJECT to subject,
                        KEY_CONTENT to content
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    workName,
                    NotificationWorkPolicy.existingWorkPolicy,
                    request
                )
                .await()
        }
    }
}
