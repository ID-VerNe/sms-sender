package com.yuuverne.smssender.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SmsDao {
    /**
     * Checks the successful-delivery record for one notification + recipient.
     * A zero window means permanent dedup; a positive window is rolling and
     * evaluated against the worker's current clock.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM forwarded_sms
            WHERE smsId = :smsId
              AND (:windowMs = 0 OR forwardTime > :now - :windowMs)
        )
        """
    )
    suspend fun isForwardedWithin(smsId: Long, windowMs: Long, now: Long): Boolean

    /** Removes an expired rolling-window row so INSERT OR IGNORE can record a new success. */
    @Query("DELETE FROM forwarded_sms WHERE smsId = :smsId AND forwardTime <= :cutoff")
    suspend fun deleteExpired(smsId: Long, cutoff: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: SmsRecord)

    @Query("SELECT * FROM forwarded_sms ORDER BY forwardTime DESC LIMIT 100")
    suspend fun getAllForwarded(): List<SmsRecord>
}
