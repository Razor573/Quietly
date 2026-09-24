package dev.quietly.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.data.source.UsageStatsSource
import dev.quietly.domain.repository.GoalRepository
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

enum class AppSort { TIME_DESC, TIME_ASC, LAUNCHES, NAME }
enum class AppAuditFilter { ALL, HEAVY_SINKS, MICRO_CHECKERS, UNUSED }

data class AppsUiState(
    val allApps: List<AppUsageEntity> = emptyList(),
    val displayApps: List<AppUsageEntity> = emptyList(),
    val goals: Map<String, GoalEntity> = emptyMap(),
    val query: String = "",
    val sort: AppSort = AppSort.TIME_DESC,
    val auditFilter: AppAuditFilter = AppAuditFilter.ALL,
    val selectedCategory: String = "All",
    val availableCategories: List<String> = emptyList(),
    val totalApps: Int = 0,
    val totalOpens: Int = 0,
    val totalTimeMs: Long = 0L,
    val heavySinksCount: Int = 0,
    val microCheckersCount: Int = 0,
    val avgSessionMinutes: Float = 0f,
    val isLoading: Boolean = true
) {
    val filtered: List<AppUsageEntity> get() = displayApps
}

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val usageRepo: UsageRepository,
    private val goalRepo: GoalRepository,
    private val statsSource: UsageStatsSource? = null
) : ViewModel() {

    private val _state = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _state.asStateFlow()

    init {
        val today = LocalDate.now().toEpochDay().toInt()

        // Background sync without blocking initial UI
        viewModelScope.launch(Dispatchers.IO) {
            try {
                usageRepo.syncToday()
            } catch (_: Exception) {}
        }

        // Live observation from Room DB (renders in 0ms)
        viewModelScope.launch {
            combine(
                usageRepo.observeDay(today),
                goalRepo.observeAll()
            ) { apps, goals ->
                apps to goals
            }.catch {
                _state.update { it.copy(isLoading = false) }
            }.collect { (apps, goals) ->
                val activeOrInstalled = if (apps.isEmpty()) {
                    try {
                        withContext(Dispatchers.IO) {
                            statsSource?.getInstalledUserApps() ?: emptyList()
                        }
                    } catch (_: Exception) { emptyList() }
                } else {
                    apps
                }

                val totalMs = activeOrInstalled.sumOf { it.totalTimeMs }
                val totalOpens = activeOrInstalled.sumOf { it.launchCount }
                val avgSession = if (totalOpens > 0) (totalMs / 60_000f) / totalOpens else 0f

                val heavySinks = activeOrInstalled.count { it.totalTimeMs >= 45 * 60_000L }
                val microCheckers = activeOrInstalled.count {
                    it.launchCount >= 8 && (it.totalTimeMs / it.launchCount.coerceAtLeast(1)) < 2 * 60_000L
                }

                val catList = mutableListOf("All")
                activeOrInstalled.map { it.category }.filter { it.isNotBlank() && it != "Other" }.distinct().forEach {
                    catList.add(it)
                }
                catList.add("Other")

                val current = _state.value
                val processed = processList(
                    activeOrInstalled,
                    current.query,
                    current.sort,
                    current.auditFilter,
                    current.selectedCategory
                )

                _state.update { s ->
                    s.copy(
                        allApps = activeOrInstalled,
                        displayApps = processed,
                        goals = goals.associateBy { it.packageName },
                        totalApps = activeOrInstalled.count { it.totalTimeMs > 0L },
                        totalOpens = totalOpens,
                        totalTimeMs = totalMs,
                        heavySinksCount = heavySinks,
                        microCheckersCount = microCheckers,
                        avgSessionMinutes = avgSession,
                        availableCategories = catList.distinct(),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setQuery(q: String) = _state.update { s ->
        s.copy(query = q, displayApps = processList(s.allApps, q, s.sort, s.auditFilter, s.selectedCategory))
    }

    fun setSort(sort: AppSort) = _state.update { s ->
        s.copy(sort = sort, displayApps = processList(s.allApps, s.query, sort, s.auditFilter, s.selectedCategory))
    }

    fun setAuditFilter(filter: AppAuditFilter) = _state.update { s ->
        s.copy(auditFilter = filter, displayApps = processList(s.allApps, s.query, s.sort, filter, s.selectedCategory))
    }

    fun setCategory(category: String) = _state.update { s ->
        s.copy(selectedCategory = category, displayApps = processList(s.allApps, s.query, s.sort, s.auditFilter, category))
    }

    fun saveGoal(pkg: String, label: String, limitMs: Long, remind: Boolean) {
        viewModelScope.launch {
            goalRepo.upsert(GoalEntity(
                packageName = pkg,
                appLabel = label,
                dailyLimitMs = limitMs,
                reminderEnabled = remind
            ))
        }
    }

    private fun processList(
        list: List<AppUsageEntity>,
        query: String,
        sort: AppSort,
        auditFilter: AppAuditFilter,
        category: String
    ): List<AppUsageEntity> {
        return list.asSequence()
            .filter { app ->
                when (auditFilter) {
                    AppAuditFilter.ALL -> app.totalTimeMs > 0L || app.launchCount > 0
                    AppAuditFilter.HEAVY_SINKS -> app.totalTimeMs >= 45 * 60_000L
                    AppAuditFilter.MICRO_CHECKERS -> app.launchCount >= 8 && (app.totalTimeMs / app.launchCount.coerceAtLeast(1)) < 2 * 60_000L
                    AppAuditFilter.UNUSED -> app.totalTimeMs == 0L
                }
            }
            .filter { app ->
                if (category == "All") true
                else app.category.equals(category, ignoreCase = true)
            }
            .filter { app ->
                if (query.isBlank()) true
                else app.appLabel.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
            }
            .sortedWith(
                when (sort) {
                    AppSort.TIME_DESC -> compareByDescending { it.totalTimeMs }
                    AppSort.TIME_ASC -> compareBy { it.totalTimeMs }
                    AppSort.LAUNCHES -> compareByDescending { it.launchCount }
                    AppSort.NAME -> compareBy { it.appLabel.lowercase() }
                }
            )
            .toList()
    }
}
