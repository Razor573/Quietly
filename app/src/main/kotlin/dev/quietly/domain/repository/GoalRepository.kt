package dev.quietly.domain.repository

import dev.quietly.data.db.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeAll(): Flow<List<GoalEntity>>
    suspend fun upsert(goal: GoalEntity)
    suspend fun delete(goal: GoalEntity)
    suspend fun forPackage(pkg: String): GoalEntity?
    suspend fun activeGoals(): List<GoalEntity>
}
