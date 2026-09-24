package dev.quietly.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.quietly.data.db.dao.AppOverrideDao
import dev.quietly.data.db.dao.AppUsageDao
import dev.quietly.data.db.dao.CalibrationSampleDao
import dev.quietly.data.db.dao.GoalDao
import dev.quietly.data.db.entity.AppOverrideEntity
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.CalibrationSampleEntity
import dev.quietly.data.db.entity.GoalEntity

@Database(
    entities  = [AppUsageEntity::class, GoalEntity::class, AppOverrideEntity::class, CalibrationSampleEntity::class],
    version   = 6,   // v6: ML calibration learning samples table
    exportSchema = false
)
abstract class QuietlyDatabase : RoomDatabase() {
    abstract fun appUsageDao():           AppUsageDao
    abstract fun goalDao():               GoalDao
    abstract fun appOverrideDao():        AppOverrideDao
    abstract fun calibrationSampleDao():  CalibrationSampleDao
}
