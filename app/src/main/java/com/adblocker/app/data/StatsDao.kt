package com.adblocker.app.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface StatsDao {

    @Query("SELECT * FROM stats WHERE date = :date LIMIT 1")
    suspend fun getStats(date: String): StatsEntity?

    @Query("SELECT COALESCE(SUM(blockedCount), 0) FROM stats")
    fun totalBlocked(): LiveData<Long>

    @Query("SELECT COALESCE(SUM(totalQueries), 0) FROM stats")
    fun totalQueries(): LiveData<Long>

    @Query("SELECT * FROM stats WHERE date = :date LIMIT 1")
    fun getStatsLive(date: String): LiveData<StatsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: StatsEntity)

    @Transaction
    suspend fun incrementBlocked(date: String) {
        val existing = getStats(date)
        if (existing != null) {
            upsert(existing.copy(
                blockedCount = existing.blockedCount + 1,
                totalQueries = existing.totalQueries + 1,
                lastUpdated = System.currentTimeMillis()
            ))
        } else {
            upsert(StatsEntity(
                date = date,
                blockedCount = 1,
                totalQueries = 1
            ))
        }
    }

    @Transaction
    suspend fun incrementQuery(date: String) {
        val existing = getStats(date)
        if (existing != null) {
            upsert(existing.copy(
                totalQueries = existing.totalQueries + 1,
                lastUpdated = System.currentTimeMillis()
            ))
        } else {
            upsert(StatsEntity(
                date = date,
                blockedCount = 0,
                totalQueries = 1
            ))
        }
    }
}
