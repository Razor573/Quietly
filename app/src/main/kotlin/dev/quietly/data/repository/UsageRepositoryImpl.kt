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

import dev.quietly.data.source.HistoricalUsageSource
import dev.quietly.domain.AppImportanceEngine
import dev.quietly.domain.AppInsight
import dev.quietly.data.db.AppEntity
import dev.quietly.data.db.DailyUsageRecord

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val dao:                   AppUsageDao,
    private val overrideDao:           AppOverrideDao,
    private val source:                UsageStatsSource,
    private val historicalUsageSource: HistoricalUsageSource,
    private val appImportanceEngine:   AppImportanceEngine
) : UsageRepository {

    override fun observeDay(day: Int): Flow<List<AppUsageEntity>> = dao.observeDay(day)

    override suspend fun syncToday() {
        val today = LocalDate.now().toEpochDay().toInt()

        // 1. Initial 90-day backfill if local history table is empty
        val existingHistory = dao.queryRange(fromDay = 0, toDay = today)
        if (existingHistory.isEmpty()) {
            val historicalRecords = historicalUsageSource.fetchHistoricalDailyUsage(daysBack = 90)
            val entities = historicalRecords.map { rec ->
                AppUsageEntity(
                    packageName = rec.packageName,
                    dateEpochDay = rec.epochDay.toInt(),
                    appLabel = rec.packageName,
                    totalTimeMs = rec.usageMinutes.toLong() * 60_000L,
                    lastSeenEpochDay = rec.epochDay.toInt()
                )
            }
            dao.upsertAll(entities)
        }

        // 2. Sync today's live screen time
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

    override suspend fun getAppInsights(): List<AppInsight> {
        val today = LocalDate.now().toEpochDay().toInt()
        val aggregated = query90DayAggregated(today)
        val allDailyRows = allPerDayRows90(today)

        val historyRecords = allDailyRows.map { row ->
            DailyUsageRecord(
                epochDay = row.dateEpochDay.toLong(),
                packageName = row.packageName,
                usageMinutes = (row.totalTimeMs / 60_000L).toInt()
            )
        }

        val todayEntries = source.queryToday()
        val todayMap = todayEntries.associateBy { it.packageName }

        val allPackages = (aggregated.map { it.packageName } + todayEntries.map { it.packageName }).distinct()

        return allPackages.map { pkg ->
            val appUsage = todayMap[pkg] ?: aggregated.find { it.packageName == pkg }
            val appEntity = AppEntity(
                packageName = pkg,
                appName = appUsage?.appLabel ?: pkg,
                usageTimeMillis = todayMap[pkg]?.totalTimeMs ?: 0L
            )
            val launches = appUsage?.launchCount ?: 0
            appImportanceEngine.evaluate(
                app = appEntity,
                history = historyRecords,
                todayOpenCount = launches,
                lateNightMinutes = 0
            )
        }.sortedByDescending { it.distractionScore }
    }

    // ── Overrides ────────────────────────────────────────────────────────────────────

    override suspend fun getOverrides(): List<AppOverrideEntity> = overrideDao.getAll()

    override suspend fun setOverride(entity: AppOverrideEntity) = overrideDao.upsert(entity)

    override suspend fun clearOverride(packageName: String) = overrideDao.delete(packageName)
}
