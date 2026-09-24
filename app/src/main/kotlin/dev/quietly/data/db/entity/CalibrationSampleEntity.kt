package dev.quietly.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores calibration training samples provided by the user.
 * Each entry maps the raw tracked duration to the user's ground-truth duration.
 */
@Entity(tableName = "calibration_samples")
data class CalibrationSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val epochDay: Int,
    val packageName: String?, // null if for daily total, or specific package if for app
    val rawDurationMs: Long,
    val userActualDurationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
