package dev.quietly.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class InsightItem(
    val packageName: String,
    val appLabel:    String,
    val avgDailyMs:  Long,
    val category:    String
)

data class InsightsUiState(
    val topApps:          List<InsightItem> = emptyList(),
    val suggestedRemove:  List<InsightItem> = emptyList(),
    val categoryBreakdown: Map<String, Long> = emptyMap(),
    val isLoading:        Boolean            = true
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val usageRepo: UsageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val today   = LocalDate.now().toEpochDay().toInt()
            val from30  = today - 29

            val range   = usageRepo.queryRange(from30, today)
            // Group by package, average per day
            val byPkg   = range.groupBy { it.packageName }
            val items   = byPkg.map { (pkg, rows) ->
                InsightItem(
                    packageName = pkg,
                    appLabel    = rows.first().appLabel,
                    avgDailyMs  = rows.sumOf { it.totalTimeMs } / rows.size,
                    category    = rows.first().category
                )
            }.sortedByDescending { it.avgDailyMs }

            // Suggest removing apps with avg > 2h/day
            val twoHours = 2 * 3_600_000L
            val suggested = items.filter { it.avgDailyMs > twoHours }

            // Category breakdown
            val catBreak = items.groupBy { it.category }
                .mapValues { (_, v) -> v.sumOf { it.avgDailyMs } }

            _state.update {
                it.copy(
                    isLoading         = false,
                    topApps           = items.take(10),
                    suggestedRemove   = suggested,
                    categoryBreakdown = catBreak
                )
            }
        }
    }
}
