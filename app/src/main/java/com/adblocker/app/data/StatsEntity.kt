package com.adblocker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stats")
data class StatsEntity(
    @PrimaryKey val date: String,
    val blockedCount: Long = 0,
    val totalQueries: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
