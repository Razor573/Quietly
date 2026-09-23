package dev.quietly.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.domain.repository.GoalRepository
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repo: GoalRepository,
    private val usageRepo: UsageRepository
) : ViewModel() {
    val goals: StateFlow<List<GoalEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayUsage: StateFlow<Map<String, Long>> =
        usageRepo.observeDay(LocalDate.now().toEpochDay().toInt())
            .map { list -> list.associate { it.packageName to it.totalTimeMs } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun addGoal(pkg: String, label: String, limitMs: Long, reminder: Boolean) {
        viewModelScope.launch {
            repo.upsert(GoalEntity(
                packageName     = pkg,
                appLabel        = label,
                dailyLimitMs    = limitMs,
                reminderEnabled = reminder
            ))
        }
    }

    fun delete(goal: GoalEntity) = viewModelScope.launch { repo.delete(goal) }

    fun toggleReminder(goal: GoalEntity) = viewModelScope.launch {
        repo.upsert(goal.copy(reminderEnabled = !goal.reminderEnabled))
    }
}
