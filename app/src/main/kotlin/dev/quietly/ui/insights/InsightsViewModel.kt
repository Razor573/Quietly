package dev.quietly.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.AppOverrideEntity
import dev.quietly.domain.ImportanceEngine
import dev.quietly.domain.ImportanceEngine.RecommendationType
import dev.quietly.domain.ImportanceEngine.ScoredApp
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class InsightsUiState(
    val isLoading:         Boolean          = true,
    /** All apps ranked by importance score (highest first). */
    val rankedApps:        List<ScoredApp>  = emptyList(),
    /** Apps that are safe removal candidates. */
    val removeList:        List<ScoredApp>  = emptyList(),
    /** Apps that are distraction-heavy and should be limited, not removed. */
    val limitList:         List<ScoredApp>  = emptyList(),
    /** Protected apps (essential / user-marked). */
    val protectedList:     List<ScoredApp>  = emptyList(),
    /** Category breakdown: category name -> total ms in 90-day window. */
    val categoryBreakdown: Map<String, Long> = emptyMap(),
    val analysisWindowDays: Int             = 90
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val usageRepo: UsageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val today = LocalDate.now().toEpochDay().toInt()

            // ── Fetch 90-day data ─────────────────────────────────────────
            val aggregated   = usageRepo.query90DayAggregated(today)
            val allDailyRows = usageRepo.allPerDayRows90(today)
            val overrideList = usageRepo.getOverrides()

            // Build per-day map: packageName -> list of daily rows
            val perDayMap = allDailyRows.groupBy { it.packageName }

            // Build override map
            val overrideMap = overrideList.associate { entity ->
                entity.packageName to dev.quietly.domain.UserOverride(
                    packageName = entity.packageName,
                    type = dev.quietly.domain.UserOverride.Type.valueOf(entity.overrideType)
                )
            }

            // ── Run importance engine ───────────────────────────────────────
            val scored = ImportanceEngine.score(
                rows          = aggregated,
                perDayRows    = perDayMap,
                overrides     = overrideMap,
                todayEpochDay = today
            )

            // ── Partition into sections ──────────────────────────────────────
            val removeList    = scored.filter { it.recommendation == RecommendationType.REMOVE }
            val limitList     = scored.filter { it.recommendation == RecommendationType.LIMIT }
            val protectedList = scored.filter { it.isProtected }

            // Category breakdown: sum totalTimeMs per category (90-day window)
            val catBreak = aggregated
                .groupBy { it.category }
                .mapValues { (_, rows) -> rows.sumOf { it.totalTimeMs } }

            _state.update {
                it.copy(
                    isLoading          = false,
                    rankedApps         = scored,
                    removeList         = removeList,
                    limitList          = limitList,
                    protectedList      = protectedList,
                    categoryBreakdown  = catBreak,
                    analysisWindowDays = 90
                )
            }
        }
    }

    /** Set or update a per-app user override and reload. */
    fun setOverride(packageName: String, type: String) {
        viewModelScope.launch {
            usageRepo.setOverride(
                AppOverrideEntity(packageName = packageName, overrideType = type)
            )
            load()
        }
    }

    /** Clear a per-app override and reload. */
    fun clearOverride(packageName: String) {
        viewModelScope.launch {
            usageRepo.clearOverride(packageName)
            load()
        }
    }
}
