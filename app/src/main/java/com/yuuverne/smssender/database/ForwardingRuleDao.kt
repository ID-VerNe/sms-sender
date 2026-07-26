package com.yuuverne.smssender.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardingRuleDao {
    @Query("SELECT * FROM forwarding_rules")
    fun getAllRulesFlow(): Flow<List<ForwardingRule>>

    @Query("SELECT * FROM forwarding_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<ForwardingRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ForwardingRule)

    @Update
    suspend fun updateRule(rule: ForwardingRule)

    @Delete
    suspend fun deleteRule(rule: ForwardingRule)
}
