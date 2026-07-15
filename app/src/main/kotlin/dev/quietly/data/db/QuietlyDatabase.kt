package dev.quietly.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.quietly.data.db.dao.AppUsageDao
import dev.quietly.data.db.dao.GoalDao
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity

@Database(
    entities  = [AppUsageEntity::class, GoalEntity::class],
    version   = 3,   // bumped: category + reminderEnabled columns
    exportSchema = false
)
abstract class QuietlyDatabase : RoomDatabase() {
    abstract fun appUsageDao(): AppUsageDao
    abstract fun goalDao():     GoalDao
}
