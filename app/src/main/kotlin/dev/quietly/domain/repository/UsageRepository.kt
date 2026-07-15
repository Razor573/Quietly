package dev.quietly.domain.repository

import dev.quietly.data.db.entity.AppUsageEntity
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    /** Stream of usage records for a single day (epoch day). */
    fun observeDay(epochDay: Long): Flow<List<AppUsageEntity>>

    /** Aggregated usage over a date range. */
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<AppUsageEntity>>

    /** Pull fresh data from UsageStatsManager and persist it. */
    suspend fun syncToday()

    /** Top-N apps for the given range. */
    suspend fun topApps(fromDay: Long, toDay: Long, limit: Int = 5): List<AppUsageEntity>
}
