package com.yuuverne.smssender.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forwarding_rules")
data class ForwardingRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val keyword: String,
    val targetEmail: String,
    val isEnabled: Boolean = true
)
