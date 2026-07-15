package dev.quietly.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.domain.repository.GoalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repo: GoalRepository
) : ViewModel() {

    val goals: StateFlow<List<GoalEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun upsertGoal(packageName: String, dailyLimitMs: Long) {
        viewModelScope.launch {
            val existing = repo.forPackage(packageName)
            repo.upsert(
                (existing ?: GoalEntity(packageName = packageName, dailyLimitMs = dailyLimitMs))
                    .copy(dailyLimitMs = dailyLimitMs)
            )
        }
    }

    fun delete(goal: GoalEntity) {
        viewModelScope.launch { repo.delete(goal) }
    }
}
