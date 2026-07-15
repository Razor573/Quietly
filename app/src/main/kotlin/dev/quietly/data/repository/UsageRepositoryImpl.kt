package dev.quietly.data.repository

import dev.quietly.data.db.dao.AppOverrideDao
import dev.quietly.data.db.dao.AppUsageDao
import dev.quietly.data.db.dao.DayTotal
import dev.quietly.data.db.entity.AppOverrideEntity
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.source.UsageStatsSource
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val dao:         AppUsageDao,
    private val overrideDao: AppOverrideDao,
    private val source:      UsageStatsSource
) : UsageRepository {

    override fun observeDay(day: Int): Flow<List<AppUsageEntity>> = dao.observeDay(day)

    override suspend fun syncToday() {
        val today   = LocalDate.now().toEpochDay().toInt()
        val entries = source.queryToday()
        entries.forEach { dao.upsert(it.copy(dateEpochDay = today, lastSeenEpochDay = today)) }
    }

    override suspend fun queryRange(fromDay: Int, toDay: Int): List<AppUsageEntity> =
        dao.queryRange(fromDay, toDay)

    override suspend fun dailyTotals(fromDay: Int, toDay: Int): List<DayTotal> =
        dao.dailyTotals(fromDay, toDay)

    override suspend fun historyForApp(pkg: String, limit: Int): List<AppUsageEntity> =
        dao.historyForApp(pkg, limit)

    override suspend fun purgeOld(retentionDays: Int) {
        val cutoff = LocalDate.now().minusDays(retentionDays.toLong()).toEpochDay().toInt()
        dao.purgeOlderThan(cutoff)
    }

    // ── 90-day engine ──────────────────────────────────────────────────────────────

    override suspend fun query90DayAggregated(today: Int): List<AppUsageEntity> =
        dao.queryRangeAggregated(fromDay = today - 89, toDay = today)

    override suspend fun allPerDayRows90(today: Int): List<AppUsageEntity> =
        dao.allPerDayRows(fromDay = today - 89, toDay = today)

    // ── Overrides ────────────────────────────────────────────────────────────────────

    override suspend fun getOverrides(): List<AppOverrideEntity> = overrideDao.getAll()

    override suspend fun setOverride(entity: AppOverrideEntity) = overrideDao.upsert(entity)

    override suspend fun clearOverride(packageName: String) = overrideDao.delete(packageName)
}
