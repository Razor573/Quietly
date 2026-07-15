package dev.quietly.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.domain.repository.GoalRepository
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val isLoading  : Boolean                   = true,
    val appUsages  : List<AppUsageEntity>      = emptyList(),
    val goals      : Map<String, GoalEntity>   = emptyMap(),
    val totalTodayMs: Long                     = 0L
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageRepo: UsageRepository,
    private val goalRepo : GoalRepository
) : ViewModel() {

    private val today = LocalDate.now().toEpochDay()

    val uiState: StateFlow<DashboardUiState> =
        combine(
            usageRepo.observeDay(today),
            goalRepo.observeAll()
        ) { usages, goals ->
            DashboardUiState(
                isLoading    = false,
                appUsages    = usages,
                goals        = goals.associateBy { it.packageName },
                totalTodayMs = usages.sumOf { it.totalTimeMs }
            )
        }.stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = DashboardUiState()
        )

    init {
        viewModelScope.launch { usageRepo.syncToday() }
    }
}
