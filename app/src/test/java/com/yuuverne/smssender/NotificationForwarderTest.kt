package com.yuuverne.smssender

import com.yuuverne.smssender.database.SmsDao
import com.yuuverne.smssender.database.SmsRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NotificationForwarderTest {

    @Test
    fun sameKeyBodyUpdateIsNotEnqueuedAgainForSameRecipient() = runBlocking {
        val dao = FakeSmsDao()
        var enqueueCount = 0
        val first = snapshot()

        assertTrue(
            enqueueNotificationIfNew(dao, recordFor(first, EMAIL_A)) {
                enqueueCount++
            }
        )

        val updated = first.copy(
            text = "验证码 5678",
            postedAt = first.postedAt + 5_000L
        )
        assertFalse(
            enqueueNotificationIfNew(dao, recordFor(updated, EMAIL_A)) {
                enqueueCount++
            }
        )

        assertEquals(1, enqueueCount)
        assertEquals(1, dao.records.size)
    }

    @Test
    fun differentKeysWithSameBodyAreEnqueuedIndependently() = runBlocking {
        val dao = FakeSmsDao()
        var enqueueCount = 0
        val first = snapshot()
        val second = first.copy(key = "com.android.messaging|43|null|0")

        assertTrue(
            enqueueNotificationIfNew(dao, recordFor(first, EMAIL_A)) {
                enqueueCount++
            }
        )
        assertTrue(
            enqueueNotificationIfNew(dao, recordFor(second, EMAIL_A)) {
                enqueueCount++
            }
        )

        assertEquals(2, enqueueCount)
        assertEquals(2, dao.records.size)
    }

    @Test
    fun recipientFailureDoesNotRepeatOrBlockSuccessfulRecipient() = runBlocking {
        val dao = FakeSmsDao()
        val notification = snapshot()
        var enqueueA = 0
        var enqueueB = 0

        assertTrue(
            enqueueNotificationIfNew(dao, recordFor(notification, EMAIL_A)) {
                enqueueA++
            }
        )
        try {
            enqueueNotificationIfNew(dao, recordFor(notification, EMAIL_B)) {
                enqueueB++
                throw IllegalStateException("enqueue failed")
            }
            fail("Expected enqueue failure")
        } catch (e: IllegalStateException) {
            assertEquals("enqueue failed", e.message)
        }

        assertFalse(
            enqueueNotificationIfNew(dao, recordFor(notification, EMAIL_A)) {
                enqueueA++
            }
        )
        assertTrue(
            enqueueNotificationIfNew(dao, recordFor(notification, EMAIL_B)) {
                enqueueB++
            }
        )

        assertEquals(1, enqueueA)
        assertEquals(2, enqueueB)
        assertEquals(2, dao.records.size)
        assertTrue(dao.isForwarded(recordFor(notification, EMAIL_A).smsId))
        assertTrue(dao.isForwarded(recordFor(notification, EMAIL_B).smsId))
    }

    @Test
    fun noKeyFallbackDeduplicatesOnlyWithinWindow() = runBlocking {
        val dao = FakeSmsDao()
        val first = snapshot(key = null, postedAt = 1_000L)
        var enqueueCount = 0

        assertTrue(
            enqueueNotificationIfNew(dao, recordFor(first, EMAIL_A)) {
                enqueueCount++
            }
        )
        assertFalse(
            enqueueNotificationIfNew(dao, recordFor(first.copy(postedAt = 31_000L), EMAIL_A)) {
                enqueueCount++
            }
        )
        assertTrue(
            enqueueNotificationIfNew(dao, recordFor(first.copy(postedAt = 61_000L), EMAIL_A)) {
                enqueueCount++
            }
        )

        assertEquals(2, enqueueCount)
        assertEquals(2, dao.records.size)
    }

    @Test
    fun enqueueFailureDoesNotWriteDedupRecord() = runBlocking {
        val dao = FakeSmsDao()
        val record = recordFor(snapshot(), EMAIL_A)
        var enqueueCalled = false

        try {
            enqueueNotificationIfNew(dao, record) {
                enqueueCalled = true
                throw IllegalStateException("enqueue failed")
            }
            fail("Expected enqueue failure")
        } catch (e: IllegalStateException) {
            assertEquals("enqueue failed", e.message)
        }

        assertTrue(enqueueCalled)
        assertFalse(dao.isForwarded(record.smsId))
        assertTrue(dao.records.isEmpty())
    }

    private fun snapshot(
        key: String? = "com.android.messaging|42|null|0",
        postedAt: Long = 1_000L
    ) = NotificationSnapshot(
        packageName = "com.android.messaging",
        key = key,
        title = "10086",
        text = "验证码 1234",
        postedAt = postedAt
    )

    private fun recordFor(snapshot: NotificationSnapshot, recipient: String) = SmsRecord(
        smsId = SmsNotificationPolicy.notificationId(snapshot, recipient),
        address = snapshot.title,
        body = snapshot.text,
        timestamp = snapshot.postedAt
    )

    private class FakeSmsDao : SmsDao {
        val records = linkedMapOf<Long, SmsRecord>()

        override suspend fun isForwarded(id: Long): Boolean = records.containsKey(id)

        override suspend fun isDuplicate(address: String, body: String, timestamp: Long): Boolean =
            records.values.any {
                it.address == address &&
                    it.body == body &&
                    kotlin.math.abs(it.timestamp - timestamp) < 60_000L
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
    }
}
