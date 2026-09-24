package dev.quietly.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.domain.repository.GoalRepository
import dev.quietly.domain.repository.UsageRepository
import dev.quietly.util.toHoursMinutes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MindfulnessQuest(
    val id: String,
    val title: String,
    val description: String,
    val points: Int = 25,
    val isCompleted: Boolean = false,
    val tag: String = "FOCUS"
)

data class SmartGoalRecommendation(
    val packageName: String,
    val appLabel: String,
    val suggestedLimitMs: Long,
    val reason: String
)

data class GoalsHubUiState(
    val goals: List<GoalEntity> = emptyList(),
    val todayUsage: Map<String, Long> = emptyMap(),
    val wellnessScore: Int = 85,
    val wellnessStatus: String = "Balanced",
    val wellnessTip: String = "Maintain healthy interval breaks every 45 minutes of screen time.",
    val quests: List<MindfulnessQuest> = emptyList(),
    val recommendations: List<SmartGoalRecommendation> = emptyList(),
    val completedQuestsCount: Int = 0,
    val totalQuestsPoints: Int = 0,
    val isLoading: Boolean = false
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repo: GoalRepository,
    private val usageRepo: UsageRepository
) : ViewModel() {

    // Expose backwards-compatible StateFlows
    val goals: StateFlow<List<GoalEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayUsage: StateFlow<Map<String, Long>> =
        usageRepo.observeDay(LocalDate.now().toEpochDay().toInt())
            .map { list -> list.associate { it.packageName to it.totalTimeMs } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val completedQuestIds = MutableStateFlow<Set<String>>(emptySet())

    private val defaultQuests = listOf(
        MindfulnessQuest(
            id = "quest_morning",
            title = "Morning Clarity Sprint",
            description = "Stay off endless social media feeds for the first 45 minutes of waking up.",
            points = 25,
            tag = "MORNING"
        ),
        MindfulnessQuest(
            id = "quest_deep_work",
            title = "Deep Focus Flow",
            description = "Complete a single 60-minute stretch without opening any entertainment apps.",
            points = 30,
            tag = "FOCUS"
        ),
        MindfulnessQuest(
            id = "quest_digital_sunset",
            title = "Digital Sunset",
            description = "Power down screens at least 40 minutes before getting into bed.",
            points = 25,
            tag = "REST"
        ),
        MindfulnessQuest(
            id = "quest_checkin_discipline",
            title = "Pocket Discipline",
            description = "Avoid habitual phone unlocks unless responding to urgent communication.",
            points = 20,
            tag = "HABIT"
        )
    )

    val hubUiState: StateFlow<GoalsHubUiState> = combine(
        repo.observeAll(),
        usageRepo.observeDay(LocalDate.now().toEpochDay().toInt()),
        completedQuestIds
    ) { currentGoals, todayApps, completedIds ->
        val usageMap = todayApps.associate { it.packageName to it.totalTimeMs }
        val totalMs = todayApps.sumOf { it.totalTimeMs }
        val hours = totalMs / 3_600_000f

        // Calculate dynamic wellness score (0-100)
        val baseScore = (100 - (hours * 5.5f).toInt()).coerceIn(20, 100)
        val bonus = completedIds.size * 5
        val finalScore = (baseScore + bonus).coerceIn(10, 100)

        val status = when {
            finalScore >= 80 -> "Mindful Flow"
            finalScore >= 60 -> "Balanced Rhythm"
            else -> "High Screen Load"
        }

        val heaviestApp = todayApps.filter { it.totalTimeMs > 0L }.maxByOrNull { it.totalTimeMs }
        val tip = when {
            heaviestApp != null && heaviestApp.totalTimeMs > 2 * 3600_000L ->
                "${heaviestApp.appLabel} is taking up ${heaviestApp.totalTimeMs.toHoursMinutes()} today. Consider taking a 15-minute screen-free break."
            hours > 8f ->
                "Total screen time is elevated today (${totalMs.toHoursMinutes()}). A quick walk or stretch helps reset your attention span."
            else ->
                "Great balance today! Engaging in offline hobbies preserves long-term dopamine sensitivity."
        }

        // Generate smart recommendations for apps without goals
        val existingGoalPkgs = currentGoals.map { it.packageName }.toSet()
        val recommendations = todayApps
            .filter { it.totalTimeMs >= 45 * 60_000L && it.packageName !in existingGoalPkgs }
            .sortedByDescending { it.totalTimeMs }
            .take(2)
            .map { app ->
                val suggestedLimit = (app.totalTimeMs * 0.8f).toLong().coerceAtLeast(30 * 60_000L)
                SmartGoalRecommendation(
                    packageName = app.packageName,
                    appLabel = app.appLabel,
                    suggestedLimitMs = suggestedLimit,
                    reason = "Currently used for ${app.totalTimeMs.toHoursMinutes()} today. A daily limit of ${suggestedLimit.toHoursMinutes()} will save you ${(app.totalTimeMs - suggestedLimit).toHoursMinutes()}!"
                )
            }

        val questList = defaultQuests.map { q ->
            q.copy(isCompleted = q.id in completedIds)
        }
        val totalEarned = questList.filter { it.isCompleted }.sumOf { it.points }

        GoalsHubUiState(
            goals = currentGoals,
            todayUsage = usageMap,
            wellnessScore = finalScore,
            wellnessStatus = status,
            wellnessTip = tip,
            quests = questList,
            recommendations = recommendations,
            completedQuestsCount = completedIds.size,
            totalQuestsPoints = totalEarned,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GoalsHubUiState(quests = defaultQuests)
    )

    fun toggleQuest(questId: String) {
        val current = completedQuestIds.value
        completedQuestIds.value = if (questId in current) current - questId else current + questId
    }

    fun applyRecommendation(rec: SmartGoalRecommendation) {
        addGoal(rec.packageName, rec.appLabel, rec.suggestedLimitMs, reminder = true)
    }

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
