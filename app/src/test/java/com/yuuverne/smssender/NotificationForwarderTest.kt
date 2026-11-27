package com.yuuverne.smssender

import com.yuuverne.smssender.database.SmsDao
import com.yuuverne.smssender.database.SmsRecord
import java.util.concurrent.ConcurrentHashMap
import javax.mail.AuthenticationFailedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NotificationForwarderTest {

    @Test
    fun workDataContainsDedupMetadataButNotSmtpPassword() {
        val data = NotificationWorkData.create(
            recipient = EMAIL_A,
            subject = "subject",
            content = "content",
            dedupKey = DEDUP_KEY,
            dedupWindowMs = 0L,
            address = "10086",
            body = "验证码 1234",
            timestamp = 1_000L
        )

        assertEquals(EMAIL_A, data.getString(NotificationWorkData.KEY_RECIPIENT))
        assertEquals(DEDUP_KEY, data.getString(NotificationWorkData.KEY_DEDUP_KEY))
        assertEquals(0L, data.getLong(NotificationWorkData.KEY_DEDUP_WINDOW_MS, -1L))
        assertEquals("10086", data.getString(NotificationWorkData.KEY_ADDRESS))
        assertEquals("验证码 1234", data.getString(NotificationWorkData.KEY_BODY))
        assertEquals(1_000L, data.getLong(NotificationWorkData.KEY_TIMESTAMP, -1L))
        assertFalse(data.keyValueMap.containsKey("smtpPassword"))
        assertFalse(data.keyValueMap.containsKey("password"))
    }

    @Test
    fun workerRecordsDedupOnlyAfterSuccessfulSmtpSend() = runBlocking {
        val dao = FakeSmsDao()
        var sendCount = 0
        var recordsSeenDuringSend = -1
        val delivery = NotificationDelivery(
            smsDao = dao,
            sendEmail = { _, _, _ ->
                sendCount++
                recordsSeenDuringSend = dao.records.size
            },
            nowMillis = { 10_000L }
        )

        val result = delivery.deliver()

        assertEquals(DeliveryResult.DELIVERED, result)
        assertEquals(1, sendCount)
        assertEquals(0, recordsSeenDuringSend)
        assertEquals(1, dao.records.size)
        val record = dao.records.values.single()
        assertEquals(SmsNotificationPolicy.notificationId(DEDUP_KEY, EMAIL_A), record.smsId)
        assertEquals(10_000L, record.forwardTime)
    }

    @Test
    fun permanentFailureDoesNotRecordAndAllowsLaterRetry() = runBlocking {
        val dao = FakeSmsDao()
        var shouldFail = true
        val delivery = NotificationDelivery(
            smsDao = dao,
            sendEmail = { _, _, _ ->
                if (shouldFail) throw AuthenticationFailedException("bad credentials")
            },
            nowMillis = { 10_000L }
        )

        try {
            delivery.deliver()
            fail("Expected permanent SMTP failure")
        } catch (e: AuthenticationFailedException) {
            assertEquals("bad credentials", e.message)
        }
        assertTrue(dao.records.isEmpty())

        shouldFail = false
        assertEquals(DeliveryResult.DELIVERED, delivery.deliver())
        assertEquals(1, dao.records.size)
    }

    @Test
    fun recordedDeliveryIsSkippedWithoutSending() = runBlocking {
        val dao = FakeSmsDao()
        dao.insert(record(EMAIL_A, forwardTime = 1_000L))
        var sendCount = 0
        val delivery = NotificationDelivery(
            smsDao = dao,
            sendEmail = { _, _, _ -> sendCount++ },
            nowMillis = { 999_999L }
        )

        val result = delivery.deliver(dedupWindowMs = 0L)

        assertEquals(DeliveryResult.SKIPPED, result)
        assertEquals(0, sendCount)
        assertEquals(1, dao.records.size)
    }

    @Test
    fun keyedDedupIsPermanent() = runBlocking {
        val dao = FakeSmsDao()
        dao.insert(record(EMAIL_A, forwardTime = 0L))
        var sendCount = 0
        val delivery = NotificationDelivery(
            smsDao = dao,
            sendEmail = { _, _, _ -> sendCount++ },
            nowMillis = { Long.MAX_VALUE / 4 }
        )

        assertEquals(DeliveryResult.SKIPPED, delivery.deliver(dedupWindowMs = 0L))
        assertEquals(0, sendCount)
    }

    @Test
    fun rollingWindowUsesSuccessTimeAndBoundaryIsExclusive() = runBlocking {
        val dao = FakeSmsDao()
        dao.insert(record(EMAIL_A, forwardTime = 1_000L))
        var now = 60_999L
        var sendCount = 0
        val delivery = NotificationDelivery(
            smsDao = dao,
            sendEmail = { _, _, _ -> sendCount++ },
            nowMillis = { now }
        )

        assertEquals(DeliveryResult.SKIPPED, delivery.deliver(dedupWindowMs = WINDOW_MS))
        assertEquals(0, sendCount)

        now = 61_000L
        assertEquals(DeliveryResult.DELIVERED, delivery.deliver(dedupWindowMs = WINDOW_MS))
        assertEquals(1, sendCount)
        assertEquals(61_000L, dao.records.values.single().forwardTime)
    }

    @Test
    fun differentRecipientsAreIndependent() = runBlocking {
        val dao = FakeSmsDao()
        var sendCount = 0
        val delivery = NotificationDelivery(
            smsDao = dao,
            sendEmail = { _, _, _ -> sendCount++ },
            nowMillis = { 10_000L }
        )

        assertEquals(DeliveryResult.DELIVERED, delivery.deliver(recipient = EMAIL_A))
        assertEquals(DeliveryResult.DELIVERED, delivery.deliver(recipient = EMAIL_B))

        assertEquals(2, sendCount)
        assertEquals(2, dao.records.size)
        assertTrue(
            SmsNotificationPolicy.notificationId(DEDUP_KEY, EMAIL_A) !=
                SmsNotificationPolicy.notificationId(DEDUP_KEY, EMAIL_B)
        )
    }

    @Test
    fun sameKeyWithChangedBodyIsSkipped() = runBlocking {
        val dao = FakeSmsDao()
        var sendCount = 0
        val delivery = NotificationDelivery(
            smsDao = dao,
            sendEmail = { _, _, _ -> sendCount++ },
            nowMillis = { 10_000L }
        )

        assertEquals(DeliveryResult.DELIVERED, delivery.deliver(body = "验证码 1234"))
        assertEquals(
            DeliveryResult.SKIPPED,
            delivery.deliver(
                subject = "updated subject",
                content = "updated content",
                body = "验证码 5678"
            )
        )

        assertEquals(1, sendCount)
        assertEquals("验证码 1234", dao.records.values.single().body)
    }

    @Test
    fun concurrentDeliveryForSameIdentitySendsOnlyOnce() = runBlocking {
        val dao = FakeSmsDao()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var sendCount = 0
        val delivery = NotificationDelivery(
            smsDao = dao,
            sendEmail = { _, _, _ ->
                sendCount++
                firstEntered.complete(Unit)
                releaseFirst.await()
            },
            nowMillis = { 10_000L }
        )

        val first = async { delivery.deliver(dedupWindowMs = 0L) }
        firstEntered.await()
        val second = async { delivery.deliver(dedupWindowMs = 0L) }
        releaseFirst.complete(Unit)

        val results = listOf(first.await(), second.await())
        assertEquals(1, sendCount)
        assertEquals(setOf(DeliveryResult.DELIVERED, DeliveryResult.SKIPPED), results.toSet())
        assertEquals(1, dao.records.size)
    }

    private suspend fun NotificationDelivery.deliver(
        dedupWindowMs: Long = 0L,
        recipient: String = EMAIL_A,
        subject: String = "subject",
        content: String = "content",
        body: String = "验证码 1234"
    ): DeliveryResult = deliver(
        dedupKey = DEDUP_KEY,
        dedupWindowMs = dedupWindowMs,
        recipient = recipient,
        subject = subject,
        content = content,
        address = "10086",
        body = body,
        timestamp = 1_000L
    )

    private fun record(recipient: String, forwardTime: Long) = SmsRecord(
        smsId = SmsNotificationPolicy.notificationId(DEDUP_KEY, recipient),
        address = "10086",
        body = "验证码 1234",
        timestamp = 1_000L,
        forwardTime = forwardTime
    )

    private class FakeSmsDao : SmsDao {
        val records = ConcurrentHashMap<Long, SmsRecord>()

        override suspend fun isForwardedWithin(smsId: Long, windowMs: Long, now: Long): Boolean =
            records[smsId]?.let { record ->
                windowMs == 0L || record.forwardTime > now - windowMs
            } ?: false

        override suspend fun deleteExpired(smsId: Long, cutoff: Long) {
            val record = records[smsId]
            if (record != null && record.forwardTime <= cutoff) {
                records.remove(smsId)
            }
        }

        override suspend fun insert(record: SmsRecord) {
            if (!records.containsKey(record.smsId)) {
                records[record.smsId] = record
            }
        }

        override suspend fun getAllForwarded(): List<SmsRecord> = records.values.toList()
    }

    private companion object {
        const val EMAIL_A = "a@example.com"
        const val EMAIL_B = "b@example.com"
        const val DEDUP_KEY = "key:com.android.messaging\u0000com.android.messaging|42|null|0"
        const val WINDOW_MS = SmsNotificationPolicy.CONTENT_DEDUP_WINDOW_MS
    }
}
