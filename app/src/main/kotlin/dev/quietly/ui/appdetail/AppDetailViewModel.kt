package dev.quietly.ui.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.dao.DayTotal
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.data.source.UsageStatsSource
import dev.quietly.domain.repository.GoalRepository
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AppDetailUiState(
    val isLoading:    Boolean              = true,
    val packageName:  String               = "",
    val appLabel:     String               = "",
    val category:     String               = "",
    val todayMs:      Long                 = 0L,
    val avgDailyMs:   Long                 = 0L,
    val weeklyTotals: List<DayTotal>       = emptyList(),
    val history:      List<AppUsageEntity> = emptyList(),
    val playStoreUrl: String               = "",
    val goal:         GoalEntity?          = null
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val usageRepo: UsageRepository,
    private val goalRepo:  GoalRepository,
    private val usageSource: UsageStatsSource
) : ViewModel() {

    private val _state = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _state.asStateFlow()

    fun load(pkg: String) {
        viewModelScope.launch {
            val today    = LocalDate.now().toEpochDay().toInt()
            val from7    = today - 6
            val history  = usageRepo.historyForApp(pkg, 30)
            val todayRow = history.firstOrNull { it.dateEpochDay == today }

            val weeklyTotals = history
                .filter   { it.dateEpochDay in from7..today }
                .map      { DayTotal(it.dateEpochDay, it.totalTimeMs) }
                .sortedBy { it.dateEpochDay }

            val avg = if (history.isNotEmpty())
                history.sumOf { it.totalTimeMs } / history.size else 0L

            val label = history.firstOrNull()?.appLabel?.ifBlank { null }
                ?: usageSource.getLabel(pkg)
            val category = history.firstOrNull()?.category?.ifBlank { null }
                ?: usageSource.getCategory(pkg)

            val goal = goalRepo.getByPackage(pkg)

            _state.update {
                it.copy(
                    isLoading    = false,
                    packageName  = pkg,
                    appLabel     = label,
                    category     = category,
                    todayMs      = todayRow?.totalTimeMs ?: 0L,
                    avgDailyMs   = avg,
                    weeklyTotals = weeklyTotals,
                    history      = history,
                    goal         = goal,
                    playStoreUrl = "https://play.google.com/store/apps/details?id=$pkg"
                )
            }
        }
    }

    fun setGoal(limitMs: Long, reminder: Boolean) {
        val current = _state.value
        if (current.packageName.isBlank()) return
        viewModelScope.launch {
            val goal = GoalEntity(
                packageName = current.packageName,
                appLabel = current.appLabel,
                dailyLimitMs = limitMs,
                reminderEnabled = reminder
            )
            goalRepo.upsert(goal)
            _state.update { it.copy(goal = goal) }
        }
    }

    fun deleteGoal() {
        val current = _state.value
        val goal = current.goal ?: return
        viewModelScope.launch {
            goalRepo.delete(goal)
            _state.update { it.copy(goal = null) }
        }
    }

    fun calibrateAppTime(actualMinutes: Int) {
        val current = _state.value
        if (current.packageName.isBlank()) return
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay().toInt()
            val actualMs = actualMinutes * 60_000L
            usageRepo.recordCalibrationFeedback(
                epochDay = today,
                packageName = current.packageName,
                rawDurationMs = current.todayMs,
                userActualDurationMs = actualMs
            )
            load(current.packageName)
        }
    }
}
