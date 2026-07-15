package dev.quietly.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.data.db.dao.DayTotal
import dev.quietly.domain.repository.GoalRepository
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val isLoading:    Boolean                    = true,
    val totalTodayMs: Long                       = 0L,
    val appUsages:    List<AppUsageEntity>       = emptyList(),
    val goals:        Map<String, GoalEntity>    = emptyMap(),
    val weeklyTotals: List<DayTotal>             = emptyList()  // NEW: 7-day bar chart data
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageRepo: UsageRepository,
    private val goalRepo:  GoalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        val today = LocalDate.now().toEpochDay().toInt()

        // Observe today's DB rows (live)
        viewModelScope.launch {
            combine(
                usageRepo.observeDay(today),
                goalRepo.observeAll()
            ) { usages, goals ->
                Pair(usages, goals)
            }.collect { (usages, goals) ->
                _state.update { s ->
                    s.copy(
                        isLoading    = false,
                        appUsages    = usages,
                        totalTodayMs = usages.sumOf { it.totalTimeMs },
                        goals        = goals.associateBy { it.packageName }
                    )
                }
            }
        }

        // Poll UsageStatsManager every 60 s and write to DB
        viewModelScope.launch {
            while (true) {
                usageRepo.syncToday()
                loadWeekly()
                delay(60_000)
            }
        }
    }

    private suspend fun loadWeekly() {
        val today   = LocalDate.now().toEpochDay().toInt()
        val fromDay = today - 6
        val totals  = usageRepo.dailyTotals(fromDay, today)
        _state.update { it.copy(weeklyTotals = totals) }
    }
}
