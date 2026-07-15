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

enum class AppSortOrder { TIME_DESC, TIME_ASC, LAUNCHES_DESC, NAME_ASC }

data class AppsUiState(
    val isLoading : Boolean              = true,
    val apps      : List<AppUsageEntity> = emptyList(),
    val sortOrder : AppSortOrder         = AppSortOrder.TIME_DESC,
    val searchQuery: String              = ""
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val usageRepo  : UsageRepository,
    private val usageSource: UsageStatsSource
) : ViewModel() {

    private val today = LocalDate.now().toEpochDay()

    private val _sortOrder  = MutableStateFlow(AppSortOrder.TIME_DESC)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<AppsUiState> =
        combine(
            usageRepo.observeDay(today),
            _sortOrder,
            _searchQuery
        ) { apps, sort, query ->
            val filtered = if (query.isBlank()) apps
                           else apps.filter { it.appLabel.contains(query, ignoreCase = true) }
            val sorted = when (sort) {
                AppSortOrder.TIME_DESC    -> filtered.sortedByDescending { it.totalTimeMs }
                AppSortOrder.TIME_ASC     -> filtered.sortedBy { it.totalTimeMs }
                AppSortOrder.LAUNCHES_DESC -> filtered.sortedByDescending { it.launchCount }
                AppSortOrder.NAME_ASC     -> filtered.sortedBy { it.appLabel.lowercase() }
            }
            AppsUiState(
                isLoading  = false,
                apps       = sorted,
                sortOrder  = sort,
                searchQuery = query
            )
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppsUiState()
        )

    init { viewModelScope.launch { usageRepo.syncToday() } }

    fun setSortOrder(order: AppSortOrder) { _sortOrder.value = order }
    fun setSearchQuery(q: String)         { _searchQuery.value = q }
}
