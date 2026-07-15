package dev.quietly.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

data class InsightsUiState(
    val topApps          : List<AppUsageEntity> = emptyList(),
    val suggestedToRemove: List<AppUsageEntity> = emptyList()
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repo: UsageRepository
) : ViewModel() {

    private val today   = LocalDate.now()
    private val fromDay = today.minusDays(6).toEpochDay()
    private val toDay   = today.toEpochDay()

    // 2-hour daily average threshold — suggest removal if exceeded
    private val SUGGEST_THRESHOLD_MS = 2 * 60 * 60_000L

    val uiState: StateFlow<InsightsUiState> =
        repo.observeRange(fromDay, toDay).map { apps ->
            val top = apps.sortedByDescending { it.totalTimeMs }.take(5)
            // Average per day = total / 7
            val suggested = apps.filter { it.totalTimeMs / 7 > SUGGEST_THRESHOLD_MS }
            InsightsUiState(topApps = top, suggestedToRemove = suggested)
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = InsightsUiState()
        )
}
