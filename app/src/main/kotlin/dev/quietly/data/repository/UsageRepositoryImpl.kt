package dev.quietly.data.repository

import dev.quietly.data.db.dao.AppUsageDao
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.source.UsageStatsSource
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val dao: AppUsageDao,
    private val source: UsageStatsSource
) : UsageRepository {

    override fun observeDay(epochDay: Long): Flow<List<AppUsageEntity>> =
        dao.observeDay(epochDay)

    override fun observeRange(fromDay: Long, toDay: Long): Flow<List<AppUsageEntity>> =
        dao.observeRange(fromDay, toDay)

    override suspend fun syncToday() {
        val today = LocalDate.now()
        val records = source.queryDay(today)
        dao.upsertAll(records)
    }

    override suspend fun topApps(fromDay: Long, toDay: Long, limit: Int): List<AppUsageEntity> =
        dao.topApps(fromDay, toDay, limit)
}
