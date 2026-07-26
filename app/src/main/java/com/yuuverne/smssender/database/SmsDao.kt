package com.yuuverne.smssender.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SmsDao {
    @Query("SELECT EXISTS(SELECT 1 FROM forwarded_sms WHERE smsId = :id)")
    suspend fun isForwarded(id: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM forwarded_sms WHERE address = :address AND body = :body AND ABS(timestamp - :timestamp) < 60000)")
    suspend fun isDuplicate(address: String, body: String, timestamp: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SmsRecord)

    @Query("SELECT * FROM forwarded_sms ORDER BY forwardTime DESC LIMIT 100")
    suspend fun getAllForwarded(): List<SmsRecord>
}
