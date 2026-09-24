package dev.quietly.domain.repository

import dev.quietly.data.db.dao.DayTotal
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.AppOverrideEntity
import dev.quietly.domain.ImportanceEngine
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun observeDay(day: Int): Flow<List<AppUsageEntity>>
    suspend fun syncToday()
    suspend fun replaceRange(fromDay: Int, toDay: Int, entries: List<AppUsageEntity>)
    suspend fun saveEntries(entries: List<AppUsageEntity>)
    suspend fun queryRange(fromDay: Int, toDay: Int): List<AppUsageEntity>
    suspend fun dailyTotals(fromDay: Int, toDay: Int): List<DayTotal>
    suspend fun historyForApp(pkg: String, limit: Int = 30): List<AppUsageEntity>
    suspend fun purgeOld(retentionDays: Int)

    // ── 90-day importance engine support ────────────────────────────────────────

    /** Aggregated row per package across the 90-day window. */
    suspend fun query90DayAggregated(today: Int): List<AppUsageEntity>

    /** All individual daily rows across the 90-day window (for active-day counting). */
    suspend fun allPerDayRows90(today: Int): List<AppUsageEntity>

    // ── Per-app overrides ─────────────────────────────────────────────────────

    suspend fun getOverrides(): List<AppOverrideEntity>
    suspend fun setOverride(entity: AppOverrideEntity)
    suspend fun clearOverride(packageName: String)

    // ── Machine Learning Calibration ──────────────────────────────────────────

    fun getDayTelemetry(day: Int): dev.quietly.domain.ml.DayTelemetry
    fun observeCalibrationSamples(): Flow<List<dev.quietly.data.db.entity.CalibrationSampleEntity>>
    suspend fun getAllCalibrationSamples(): List<dev.quietly.data.db.entity.CalibrationSampleEntity>
    suspend fun recordCalibrationFeedback(
        epochDay: Int,
        packageName: String?,
        rawDurationMs: Long,
        userActualDurationMs: Long
    )
    suspend fun clearCalibration()
}
