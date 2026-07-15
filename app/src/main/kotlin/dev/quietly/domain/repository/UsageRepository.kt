package dev.quietly.domain.repository

import dev.quietly.data.db.dao.DayTotal
import dev.quietly.data.db.entity.AppUsageEntity
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun observeDay(day: Int): Flow<List<AppUsageEntity>>
    suspend fun syncToday()
    suspend fun queryRange(fromDay: Int, toDay: Int): List<AppUsageEntity>
    suspend fun dailyTotals(fromDay: Int, toDay: Int): List<DayTotal>
    suspend fun historyForApp(pkg: String, limit: Int = 30): List<AppUsageEntity>
    suspend fun purgeOld(retentionDays: Int)
}
