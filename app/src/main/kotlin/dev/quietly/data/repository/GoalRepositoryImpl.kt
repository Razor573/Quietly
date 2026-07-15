package dev.quietly.data.repository

import dev.quietly.data.db.dao.GoalDao
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val dao: GoalDao
) : GoalRepository {
    override fun observeAll(): Flow<List<GoalEntity>> = dao.observeAll()
    override suspend fun upsert(goal: GoalEntity) = dao.upsert(goal)
    override suspend fun delete(goal: GoalEntity) = dao.delete(goal)
    override suspend fun forPackage(pkg: String): GoalEntity? = dao.forPackage(pkg)
    override suspend fun activeGoals(): List<GoalEntity> = dao.activeGoals()
}
