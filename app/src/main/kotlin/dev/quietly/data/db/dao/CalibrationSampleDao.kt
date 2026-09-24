package dev.quietly.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.quietly.data.db.entity.CalibrationSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalibrationSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: CalibrationSampleEntity)

    @Query("SELECT * FROM calibration_samples ORDER BY timestamp DESC")
    fun observeAllSamples(): Flow<List<CalibrationSampleEntity>>

    @Query("SELECT * FROM calibration_samples ORDER BY timestamp DESC")
    suspend fun getAllSamples(): List<CalibrationSampleEntity>

    @Query("SELECT * FROM calibration_samples WHERE packageName = :pkg ORDER BY timestamp DESC")
    suspend fun getSamplesForPackage(pkg: String): List<CalibrationSampleEntity>

    @Query("SELECT * FROM calibration_samples WHERE packageName IS NULL ORDER BY timestamp DESC")
    suspend fun getGlobalDailySamples(): List<CalibrationSampleEntity>

    @Query("SELECT COUNT(*) FROM calibration_samples")
    fun observeSampleCount(): Flow<Int>

    @Query("DELETE FROM calibration_samples")
    suspend fun clearAll()
}
