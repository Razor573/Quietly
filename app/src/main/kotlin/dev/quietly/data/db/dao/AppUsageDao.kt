package dev.quietly.data.db.dao

import androidx.room.*
import dev.quietly.data.db.entity.AppUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AppUsageEntity>)

    /** Live stream for a single day (Dashboard). */
    @Query("SELECT * FROM app_usage WHERE dateEpochDay = :day ORDER BY totalTimeMs DESC")
    fun observeDay(day: Int): Flow<List<AppUsageEntity>>

    /** Aggregate across a date range (weekly/monthly charts). */
    @Query("""
        SELECT packageName, MIN(dateEpochDay) AS dateEpochDay, appLabel,
               SUM(totalTimeMs) AS totalTimeMs, SUM(launchCount) AS launchCount, category
        FROM app_usage
        WHERE dateEpochDay BETWEEN :fromDay AND :toDay
        GROUP BY packageName
        ORDER BY totalTimeMs DESC
    """)
    suspend fun queryRange(fromDay: Int, toDay: Int): List<AppUsageEntity>

    /** Per-day totals for chart bar series (last N days). */
    @Query("""
        SELECT dateEpochDay, SUM(totalTimeMs) AS totalTimeMs
        FROM app_usage
        WHERE dateEpochDay BETWEEN :fromDay AND :toDay
        GROUP BY dateEpochDay
        ORDER BY dateEpochDay ASC
    """)
    suspend fun dailyTotals(fromDay: Int, toDay: Int): List<DayTotal>

    @Query("SELECT * FROM app_usage WHERE packageName = :pkg ORDER BY dateEpochDay DESC LIMIT :limit")
    suspend fun historyForApp(pkg: String, limit: Int = 30): List<AppUsageEntity>

    @Query("DELETE FROM app_usage WHERE dateEpochDay < :beforeDay")
    suspend fun purgeOlderThan(beforeDay: Int)
}

data class DayTotal(val dateEpochDay: Int, val totalTimeMs: Long)
