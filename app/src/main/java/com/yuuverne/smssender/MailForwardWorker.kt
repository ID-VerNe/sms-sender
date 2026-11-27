package com.yuuverne.smssender

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuuverne.smssender.database.AppDatabase
import com.yuuverne.smssender.database.SmsDao
import com.yuuverne.smssender.database.SmsRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MailForwardWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val recipient = inputData.getString(NotificationWorkData.KEY_RECIPIENT)
        val subject = inputData.getString(NotificationWorkData.KEY_SUBJECT)
        val content = inputData.getString(NotificationWorkData.KEY_CONTENT)
        val dedupKey = inputData.getString(NotificationWorkData.KEY_DEDUP_KEY)
        val dedupWindowMs = inputData.getLong(NotificationWorkData.KEY_DEDUP_WINDOW_MS, -1L)
        val address = inputData.getString(NotificationWorkData.KEY_ADDRESS)
        val body = inputData.getString(NotificationWorkData.KEY_BODY)
        val timestamp = inputData.getLong(NotificationWorkData.KEY_TIMESTAMP, -1L)

        if (recipient.isNullOrBlank() ||
            subject == null ||
            content == null ||
            dedupKey.isNullOrBlank() ||
            dedupWindowMs < 0L ||
            address == null ||
            body == null ||
            timestamp < 0L
        ) {
            Log.e(TAG, "邮件队列数据不完整，无法发送")
            return Result.failure()
        }

        return try {
            val outcome = withContext(Dispatchers.IO) {
                val delivery = NotificationDelivery(
                    smsDao = AppDatabase.getDatabase(applicationContext).smsDao(),
                    sendEmail = { target, mailSubject, mailContent ->
                        val config = ConfigManager(applicationContext)
                        MailSender.sendEmailOrThrow(
                            config = config,
                            subject = mailSubject,
                            content = mailContent,
                            targetOverride = target
                        )
                    }
                )
                delivery.deliver(
                    dedupKey = dedupKey,
                    dedupWindowMs = dedupWindowMs,
                    recipient = recipient,
                    subject = subject,
                    content = content,
                    address = address,
                    body = body,
                    timestamp = timestamp
                )
            }

            when (outcome) {
                DeliveryResult.DELIVERED -> Log.i(TAG, "邮件发送成功并记录去重标记: $recipient")
                DeliveryResult.SKIPPED -> Log.i(TAG, "检测到已成功发送，跳过重复邮件: $recipient")
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

    internal companion object {
        const val TAG = "MailForwardWorker"
    }
}

internal enum class DeliveryResult {
    DELIVERED,
    SKIPPED
}

/**
 * Testable delivery boundary for [MailForwardWorker]. The SMTP call and Room
 * DAO are injected so success-only persistence and retry semantics can be
 * verified without Android runtime dependencies.
 */
internal class NotificationDelivery(
    private val smsDao: SmsDao,
    private val sendEmail: suspend (recipient: String, subject: String, content: String) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    suspend fun deliver(
        dedupKey: String,
        dedupWindowMs: Long,
        recipient: String,
        subject: String,
        content: String,
        address: String,
        body: String,
        timestamp: Long
    ): DeliveryResult {
        require(dedupKey.isNotBlank()) { "dedupKey must not be blank" }
        require(dedupWindowMs >= 0L) { "dedupWindowMs must not be negative" }

        val smsId = SmsNotificationPolicy.notificationId(dedupKey, recipient)
        val deliveryLock = lockFor(smsId)

        return deliveryLock.withLock {
            if (smsDao.isForwardedWithin(smsId, dedupWindowMs, nowMillis())) {
                DeliveryResult.SKIPPED
            } else {
                sendEmail(recipient, subject, content)
                val forwardedAt = nowMillis()
                if (dedupWindowMs > 0L) {
                    smsDao.deleteExpired(smsId, forwardedAt - dedupWindowMs)
                }
                smsDao.insert(
                    SmsRecord(
                        smsId = smsId,
                        address = address,
                        body = body,
                        timestamp = timestamp,
                        forwardTime = forwardedAt
                    )
                )
                DeliveryResult.DELIVERED
            }
        }
    }

    private companion object {
        // Bounded process-level striped locks close the check/send/insert race
        // without serializing unrelated notifications or growing a lock map.
        private val DELIVERY_LOCKS = Array(64) { Mutex() }

        fun lockFor(smsId: Long): Mutex {
            val index = (smsId.hashCode() and Int.MAX_VALUE) % DELIVERY_LOCKS.size
            return DELIVERY_LOCKS[index]
        }
    }
}
