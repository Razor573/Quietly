package dev.quietly.data.repository

import dev.quietly.data.db.dao.AppOverrideDao
import dev.quietly.data.db.dao.AppUsageDao
import dev.quietly.data.db.dao.DayTotal
import dev.quietly.data.db.entity.AppOverrideEntity
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.source.UsageStatsSource
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val dao:            AppUsageDao,
    private val overrideDao:    AppOverrideDao,
    private val calibrationDao: dev.quietly.data.db.dao.CalibrationSampleDao,
    private val source:         UsageStatsSource
) : UsageRepository {

    @Volatile private var hasSyncedPastHistoryThisSession = false

    override fun observeDay(day: Int): Flow<List<AppUsageEntity>> = dao.observeDay(day)

    override suspend fun replaceRange(fromDay: Int, toDay: Int, entries: List<AppUsageEntity>) =
        withContext(Dispatchers.IO) {
            dao.replaceRange(fromDay, toDay, entries)
        }

    override suspend fun saveEntries(entries: List<AppUsageEntity>) = withContext(Dispatchers.IO) {
        if (entries.isNotEmpty()) {
            dao.upsertAll(entries)
        }
    }

    override suspend fun syncToday() = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toEpochDay().toInt()
        val rawTodayEntries = source.queryDay(today)
        val calibratedEntries = applyCalibration(rawTodayEntries)
        dao.replaceRange(today, today, calibratedEntries)
        ensurePastHistoryInitialized(today)
    }

    private suspend fun ensurePastHistoryInitialized(today: Int) {
        val fromDay = today - 6
        val toDay = today - 1
        val existingTotals = dao.dailyTotals(fromDay, toDay)
        val hasCorruptedDay = existingTotals.any { it.totalTimeMs > 24 * 3600_000L }
        val isMissingDays = existingTotals.size < 6

        if (!hasSyncedPastHistoryThisSession || hasCorruptedDay || isMissingDays) {
            val pastEntries = source.queryRange(fromDay, toDay)
            val calibratedPast = applyCalibration(pastEntries)
            dao.replaceRange(fromDay, toDay, calibratedPast)
            hasSyncedPastHistoryThisSession = true
        }
    }

    override fun getDayTelemetry(day: Int): dev.quietly.domain.ml.DayTelemetry =
        source.getDayTelemetry(day)

    private suspend fun applyCalibration(entries: List<AppUsageEntity>): List<AppUsageEntity> {
        val samples = calibrationDao.getAllSamples()
        if (samples.isEmpty()) return entries

        val groupedByDay = entries.groupBy { it.dateEpochDay }
        val telemetriesByDay = groupedByDay.keys.associateWith { day ->
            source.getDayTelemetry(day)
        }

        val weights = dev.quietly.domain.ml.ScreenTimeAdaptiveModel.train(samples, telemetriesByDay)

        val calibratedList = mutableListOf<AppUsageEntity>()
        for ((day, dayEntries) in groupedByDay) {
            val telemetry = telemetriesByDay[day]
            val appPairs = dayEntries.map { it.packageName to it.totalTimeMs }
            val calibratedMap = dev.quietly.domain.ml.ScreenTimeAdaptiveModel.calibrateAppUsages(
                rawApps = appPairs,
                epochDay = day,
                telemetry = telemetry,
                weights = weights
            )
            calibratedList.addAll(dayEntries.map { entry ->
                entry.copy(totalTimeMs = calibratedMap[entry.packageName] ?: entry.totalTimeMs)
            })
        }
        return calibratedList
    }

    override suspend fun queryRange(fromDay: Int, toDay: Int): List<AppUsageEntity> =
        withContext(Dispatchers.IO) {
            if (fromDay == toDay) {
                dao.queryDay(fromDay)
            } else {
                dao.queryRange(fromDay, toDay)
            }
        }

    override suspend fun dailyTotals(fromDay: Int, toDay: Int): List<DayTotal> =
        withContext(Dispatchers.IO) { dao.dailyTotals(fromDay, toDay) }

    override suspend fun historyForApp(pkg: String, limit: Int): List<AppUsageEntity> =
        withContext(Dispatchers.IO) { dao.historyForApp(pkg, limit) }

    override suspend fun purgeOld(retentionDays: Int) = withContext(Dispatchers.IO) {
        val cutoff = LocalDate.now().minusDays(retentionDays.toLong()).toEpochDay().toInt()
        dao.purgeOlderThan(cutoff)
    }

    // ── 90-day engine ──────────────────────────────────────────────────────────────

    override suspend fun query90DayAggregated(today: Int): List<AppUsageEntity> =
        withContext(Dispatchers.IO) { dao.queryRangeAggregated(fromDay = today - 89, toDay = today) }

    override suspend fun allPerDayRows90(today: Int): List<AppUsageEntity> =
        withContext(Dispatchers.IO) { dao.allPerDayRows(fromDay = today - 89, toDay = today) }

    // ── Overrides ────────────────────────────────────────────────────────────────────

    override suspend fun getOverrides(): List<AppOverrideEntity> =
        withContext(Dispatchers.IO) { overrideDao.getAll() }

    override suspend fun setOverride(entity: AppOverrideEntity) =
        withContext(Dispatchers.IO) { overrideDao.upsert(entity) }

    override suspend fun clearOverride(packageName: String) =
        withContext(Dispatchers.IO) { overrideDao.delete(packageName) }

    // ── Machine Learning Calibration ──────────────────────────────────────────

    override fun observeCalibrationSamples(): Flow<List<dev.quietly.data.db.entity.CalibrationSampleEntity>> =
        calibrationDao.observeAllSamples()

    override suspend fun getAllCalibrationSamples(): List<dev.quietly.data.db.entity.CalibrationSampleEntity> =
        withContext(Dispatchers.IO) { calibrationDao.getAllSamples() }

    override suspend fun recordCalibrationFeedback(
        epochDay: Int,
        packageName: String?,
        rawDurationMs: Long,
        userActualDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        val sample = dev.quietly.data.db.entity.CalibrationSampleEntity(
            epochDay = epochDay,
            packageName = packageName,
            rawDurationMs = rawDurationMs,
            userActualDurationMs = userActualDurationMs
        )
        calibrationDao.insertSample(sample)

        // Immediately update stored DB entries so user sees instant effect
        val baseDayEntries = try {
            val queried = source.queryDay(epochDay)
            if (queried.isNotEmpty()) queried else dao.queryDay(epochDay)
        } catch (_: Exception) {
            dao.queryDay(epochDay)
        }

        if (baseDayEntries.isNotEmpty()) {
            val telemetry = source.getDayTelemetry(epochDay)
            val allSamples = calibrationDao.getAllSamples()
            val weights = dev.quietly.domain.ml.ScreenTimeAdaptiveModel.train(allSamples, mapOf(epochDay to telemetry))
            val appPairs = baseDayEntries.map { it.packageName to it.totalTimeMs }
            val calibratedMap = dev.quietly.domain.ml.ScreenTimeAdaptiveModel.calibrateAppUsages(
                rawApps = appPairs,
                epochDay = epochDay,
                telemetry = telemetry,
                weights = weights
            )
            val calibrated = baseDayEntries.map { entry ->
                val finalTime = if (packageName != null && entry.packageName == packageName) {
                    userActualDurationMs.coerceIn(0L, 86_400_000L)
                } else {
                    calibratedMap[entry.packageName] ?: entry.totalTimeMs
                }
                entry.copy(totalTimeMs = finalTime)
            }
            dao.replaceRange(epochDay, epochDay, calibrated)
        }
    }

    override suspend fun clearCalibration() = withContext(Dispatchers.IO) {
        calibrationDao.clearAll()
    }
}
