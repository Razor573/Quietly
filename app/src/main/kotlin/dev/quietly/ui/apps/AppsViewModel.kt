package dev.quietly.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.source.UsageStatsSource
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class AppSort { TIME_DESC, TIME_ASC, LAUNCHES, NAME }

data class AppsUiState(
    val apps:          List<AppUsageEntity> = emptyList(),
    val filtered:      List<AppUsageEntity> = emptyList(),
    val query:         String               = "",
    val sort:          AppSort              = AppSort.TIME_DESC,
    val totalApps:     Int                  = 0,
    val totalOpens:    Int                  = 0,
    val totalTimeMs:   Long                 = 0L,
    val isLoading:     Boolean              = true
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val usageRepo: UsageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _state.asStateFlow()

    init {
        val today = LocalDate.now().toEpochDay().toInt()
        viewModelScope.launch {
            usageRepo.syncToday()
            usageRepo.observeDay(today).collect { apps ->
                _state.update { s ->
                    val sorted = sort(apps, s.sort)
                    val filtered = filter(sorted, s.query)
                    s.copy(
                        apps        = sorted,
                        filtered    = filtered,
                        totalApps   = apps.size,
                        totalOpens  = apps.sumOf { it.launchCount },
                        totalTimeMs = apps.sumOf { it.totalTimeMs },
                        isLoading   = false
                    )
                }
            }
        }
    }

    fun setQuery(q: String) = _state.update { s ->
        s.copy(query = q, filtered = filter(s.apps, q))
    }

    fun setSort(sort: AppSort) = _state.update { s ->
        val sorted   = sort(s.apps, sort)
        s.copy(sort = sort, apps = sorted, filtered = filter(sorted, s.query))
    }

    private fun sort(list: List<AppUsageEntity>, sort: AppSort) = when (sort) {
        AppSort.TIME_DESC -> list.sortedByDescending { it.totalTimeMs }
        AppSort.TIME_ASC  -> list.sortedBy { it.totalTimeMs }
        AppSort.LAUNCHES  -> list.sortedByDescending { it.launchCount }
        AppSort.NAME      -> list.sortedBy { it.appLabel.lowercase() }
    }

    private fun filter(list: List<AppUsageEntity>, q: String) =
        if (q.isBlank()) list
        else list.filter { it.appLabel.contains(q, ignoreCase = true) }
}
