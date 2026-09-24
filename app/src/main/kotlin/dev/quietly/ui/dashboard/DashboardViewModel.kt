package dev.quietly.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.data.db.dao.DayTotal
import dev.quietly.data.source.UsageStatsSource
import dev.quietly.domain.intelligence.HabitIntelligenceEngine
import dev.quietly.domain.intelligence.HabitIntelligenceEngine.IntelligenceReport
import dev.quietly.domain.repository.GoalRepository
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val isLoading:            Boolean                                                    = true,
    val totalTodayMs:         Long                                                       = 0L,
    val appUsages:            List<AppUsageEntity>                                       = emptyList(),
    val goals:                Map<String, GoalEntity>                                    = emptyMap(),
    val weeklyTotals:         List<DayTotal>                                             = emptyList(),
    val intelligenceReport:   IntelligenceReport?                                        = null,
    val selectedEpochDay:     Int                                                        = LocalDate.now().toEpochDay().toInt(),
    val selectedDayUsages:    List<AppUsageEntity>                                       = emptyList(),
    val selectedDayTotalMs:   Long                                                       = 0L,
    val selectedDayTelemetry: dev.quietly.domain.ml.DayTelemetry?                        = null,
    val isViewingPastDay:     Boolean                                                    = false,
    val calibrationWeights:   dev.quietly.domain.ml.CalibrationModelWeights              = dev.quietly.domain.ml.CalibrationModelWeights(),
    val calibrationSamples:   List<dev.quietly.data.db.entity.CalibrationSampleEntity>   = emptyList(),
    val isCalibrating:        Boolean                                                    = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageRepo:   UsageRepository,
    private val goalRepo:    GoalRepository,
    private val statsSource: UsageStatsSource
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var cachedHistory90: List<AppUsageEntity> = emptyList()
    private var selectDayJob: Job? = null

    init {
        val today = LocalDate.now().toEpochDay().toInt()

        // Load 90-day history once for ML baseline off-main-thread
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cachedHistory90 = usageRepo.allPerDayRows90(today)
            } catch (_: Exception) {}
        }

        // Observe calibration training samples and train ML model in real-time
        viewModelScope.launch {
            usageRepo.observeCalibrationSamples().collect { samples ->
                val weights = withContext(Dispatchers.Default) {
                    val days = samples.map { it.epochDay }.distinct()
                    val telemetries = withContext(Dispatchers.IO) {
                        days.associateWith { usageRepo.getDayTelemetry(it) }
                    }
                    dev.quietly.domain.ml.ScreenTimeAdaptiveModel.train(samples, telemetries)
                }
                _state.update { s ->
                    s.copy(
                        calibrationWeights = weights,
                        calibrationSamples = samples
                    )
                }
            }
        }

        // Observe today's DB rows (live)
        viewModelScope.launch {
            combine(
                usageRepo.observeDay(today),
                goalRepo.observeAll()
            ) { usages, goals ->
                Pair(usages, goals)
            }.collect { (usages, goals) ->
                val todayTotal = usages.sumOf { it.totalTimeMs }
                val report = withContext(Dispatchers.Default) {
                    HabitIntelligenceEngine.analyze(
                        todayUsage = usages,
                        history90Days = cachedHistory90,
                        goals = goals
                    )
                }
                val todayTel = withContext(Dispatchers.IO) {
                    usageRepo.getDayTelemetry(today)
                }
                _state.update { s ->
                    val isPast = s.isViewingPastDay
                    s.copy(
                        isLoading            = false,
                        appUsages            = usages,
                        totalTodayMs         = todayTotal,
                        goals                = goals.associateBy { it.packageName },
                        intelligenceReport   = report,
                        selectedDayTelemetry = if (!isPast) todayTel else s.selectedDayTelemetry,
                        selectedDayUsages    = if (!isPast) usages else s.selectedDayUsages,
                        selectedDayTotalMs   = if (!isPast) todayTotal else s.selectedDayTotalMs
                    )
                }
            }
        }

        // Poll UsageStatsManager every 60 s and write to DB on IO dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    usageRepo.syncToday()
                    loadWeekly()
                } catch (_: Exception) {}
                delay(60_000)
            }
        }
    }

    fun selectDay(epochDay: Int) {
        val today = LocalDate.now().toEpochDay().toInt()
        if (epochDay == today) {
            selectDayJob?.cancel()
            val todayTel = usageRepo.getDayTelemetry(today)
            _state.update { s ->
                s.copy(
                    selectedEpochDay     = today,
                    isViewingPastDay     = false,
                    selectedDayTotalMs   = s.totalTodayMs,
                    selectedDayUsages    = s.appUsages,
                    selectedDayTelemetry = todayTel
                )
            }
            return
        }

        selectDayJob?.cancel()
        selectDayJob = viewModelScope.launch {
            try {
                val telemetry = withContext(Dispatchers.IO) {
                    usageRepo.getDayTelemetry(epochDay)
                }

                // 1. Fast path: Check Room DB first
                val dbRows = withContext(Dispatchers.IO) {
                    usageRepo.queryRange(epochDay, epochDay)
                }
                if (dbRows.isNotEmpty()) {
                    val totalMs = dbRows.sumOf { it.totalTimeMs }
                    if (totalMs <= 24 * 3600_000L) {
                        _state.update { s ->
                            s.copy(
                                selectedEpochDay     = epochDay,
                                isViewingPastDay     = true,
                                selectedDayTotalMs   = totalMs,
                                selectedDayUsages    = dbRows,
                                selectedDayTelemetry = telemetry
                            )
                        }
                        return@launch
                    }
                }

                // 2. Query system source on IO thread
                val rawEntries = withContext(Dispatchers.IO) {
                    statsSource.queryDay(epochDay)
                }
                val freshEntries = withContext(Dispatchers.IO) {
                    val samples = usageRepo.getAllCalibrationSamples()
                    val weights = dev.quietly.domain.ml.ScreenTimeAdaptiveModel.train(samples, mapOf(epochDay to telemetry))
                    val appPairs = rawEntries.map { it.packageName to it.totalTimeMs }
                    val calibratedMap = dev.quietly.domain.ml.ScreenTimeAdaptiveModel.calibrateAppUsages(
                        rawApps = appPairs,
                        epochDay = epochDay,
                        telemetry = telemetry,
                        weights = weights
                    )
                    rawEntries.map { entry ->
                        entry.copy(totalTimeMs = calibratedMap[entry.packageName] ?: entry.totalTimeMs)
                    }
                }
                withContext(Dispatchers.IO) {
                    usageRepo.replaceRange(epochDay, epochDay, freshEntries)
                }
                val totalMs = freshEntries.sumOf { it.totalTimeMs }
                _state.update { s ->
                    s.copy(
                        selectedEpochDay     = epochDay,
                        isViewingPastDay     = true,
                        selectedDayTotalMs   = totalMs,
                        selectedDayUsages    = freshEntries,
                        selectedDayTelemetry = telemetry
                    )
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Failed to select past day $epochDay", e)
            }
        }
    }

    fun selectPreviousDay() {
        selectDay(_state.value.selectedEpochDay - 1)
    }

    fun selectNextDay() {
        val today = LocalDate.now().toEpochDay().toInt()
        if (_state.value.selectedEpochDay < today) {
            selectDay(_state.value.selectedEpochDay + 1)
        }
    }

    /**
     * Records user ground-truth screen time.
     * Retrains ML model weights and recalibrates stored time for the day.
     */
    fun submitCalibrationFeedback(
        epochDay: Int,
        packageName: String?,
        rawDurationMs: Long,
        actualMinutes: Int
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isCalibrating = true) }
            val actualMs = actualMinutes * 60_000L
            withContext(Dispatchers.IO) {
                usageRepo.recordCalibrationFeedback(
                    epochDay = epochDay,
                    packageName = packageName,
                    rawDurationMs = rawDurationMs,
                    userActualDurationMs = actualMs
                )
            }
            // Trigger refresh to recalculate aggregates
            refresh()
            _state.update { it.copy(isCalibrating = false) }
        }
    }

    fun resetCalibration() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                usageRepo.clearCalibration()
                usageRepo.syncToday()
            }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    usageRepo.syncToday()
                    loadWeekly()
                    val today = LocalDate.now().toEpochDay().toInt()
                    cachedHistory90 = usageRepo.allPerDayRows90(today)
                }
                val current = _state.value
                val updatedReport = withContext(Dispatchers.Default) {
                    HabitIntelligenceEngine.analyze(
                        todayUsage = current.appUsages,
                        history90Days = cachedHistory90,
                        goals = current.goals.values.toList()
                    )
                }
                _state.update { it.copy(intelligenceReport = updatedReport) }
            } catch (_: Exception) {}
        }
    }

    private suspend fun loadWeekly() {
        val today   = LocalDate.now().toEpochDay().toInt()
        val fromDay = today - 6
        val totals  = withContext(Dispatchers.IO) {
            usageRepo.dailyTotals(fromDay, today)
        }
        _state.update { it.copy(weeklyTotals = totals) }
    }
}
