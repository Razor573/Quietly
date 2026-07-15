package dev.quietly.data.db.dao

import androidx.room.*
import dev.quietly.data.db.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE packageName = :pkg LIMIT 1")
    suspend fun forPackage(pkg: String): GoalEntity?

    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE reminderEnabled = 1")
    suspend fun activeGoals(): List<GoalEntity>
}
