package dev.quietly.data.db.dao

import androidx.room.*
import dev.quietly.data.db.entity.AppUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {

    @Upsert
    suspend fun upsert(entity: AppUsageEntity)

    @Upsert
    suspend fun upsertAll(entities: List<AppUsageEntity>)

    /** Single day — for today’s live dashboard. */
    @Query("SELECT * FROM app_usage WHERE dateEpochDay = :epochDay ORDER BY totalTimeMs DESC")
    fun observeDay(epochDay: Long): Flow<List<AppUsageEntity>>

    /** Date range — for weekly / monthly charts. */
    @Query("""
        SELECT packageName, appLabel, SUM(totalTimeMs) AS totalTimeMs, SUM(launchCount) AS launchCount,
               MAX(lastUsedMs) AS lastUsedMs, :fromDay AS dateEpochDay
        FROM app_usage
        WHERE dateEpochDay BETWEEN :fromDay AND :toDay
        GROUP BY packageName
        ORDER BY totalTimeMs DESC
    """)
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<AppUsageEntity>>

    /** Top N apps by total time in range — for insights. */
    @Query("""
        SELECT packageName, appLabel, SUM(totalTimeMs) AS totalTimeMs, SUM(launchCount) AS launchCount,
               MAX(lastUsedMs) AS lastUsedMs, :fromDay AS dateEpochDay
        FROM app_usage
        WHERE dateEpochDay BETWEEN :fromDay AND :toDay
        GROUP BY packageName
        ORDER BY totalTimeMs DESC
        LIMIT :limit
    """)
    suspend fun topApps(fromDay: Long, toDay: Long, limit: Int = 5): List<AppUsageEntity>

    @Query("DELETE FROM app_usage WHERE dateEpochDay < :beforeDay")
    suspend fun purgeOlderThan(beforeDay: Long)
}
