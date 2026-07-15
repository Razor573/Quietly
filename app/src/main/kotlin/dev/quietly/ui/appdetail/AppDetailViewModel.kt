package dev.quietly.ui.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.dao.DayTotal
import dev.quietly.data.db.entity.AppUsageEntity
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
    val appLabel:     String               = "",
    val category:     String               = "",
    val todayMs:      Long                 = 0L,
    val avgDailyMs:   Long                 = 0L,
    val weeklyTotals: List<DayTotal>       = emptyList(),
    val history:      List<AppUsageEntity> = emptyList(),
    val playStoreUrl: String               = ""
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val usageRepo: UsageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _state.asStateFlow()

    fun load(pkg: String) {
        viewModelScope.launch {
            val today   = LocalDate.now().toEpochDay().toInt()
            val from7   = today - 6
            val history = usageRepo.historyForApp(pkg, 30)
            val todayRow = history.firstOrNull { it.dateEpochDay == today }

            val weeklyTotals = history
                .filter   { it.dateEpochDay in from7..today }
                .map      { DayTotal(it.dateEpochDay, it.totalTimeMs) }
                .sortedBy { it.dateEpochDay }

            val avg = if (history.isNotEmpty())
                history.sumOf { it.totalTimeMs } / history.size else 0L

            _state.update {
                it.copy(
                    isLoading    = false,
                    appLabel     = history.firstOrNull()?.appLabel ?: pkg,
                    category     = history.firstOrNull()?.category ?: "",
                    todayMs      = todayRow?.totalTimeMs ?: 0L,
                    avgDailyMs   = avg,
                    weeklyTotals = weeklyTotals,
                    history      = history,
                    playStoreUrl = "https://play.google.com/store/apps/details?id=$pkg"
                )
            }
        }
    }
}
