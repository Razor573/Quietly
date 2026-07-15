package dev.quietly.data.db.dao

import androidx.room.*
import dev.quietly.data.db.entity.AppOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppOverrideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppOverrideEntity)

    @Query("DELETE FROM app_overrides WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("SELECT * FROM app_overrides")
    fun observeAll(): Flow<List<AppOverrideEntity>>

    @Query("SELECT * FROM app_overrides")
    suspend fun getAll(): List<AppOverrideEntity>

    @Query("SELECT * FROM app_overrides WHERE packageName = :pkg LIMIT 1")
    suspend fun getForPackage(pkg: String): AppOverrideEntity?
}
