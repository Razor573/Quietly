package dev.quietly.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.quietly.data.db.dao.AppOverrideDao
import dev.quietly.data.db.dao.AppUsageDao
import dev.quietly.data.db.dao.GoalDao
import dev.quietly.data.db.entity.AppOverrideEntity
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity

@Database(
    entities  = [AppUsageEntity::class, GoalEntity::class, AppOverrideEntity::class],
    version   = 4,   // v4: app_overrides table + lastSeenEpochDay column
    exportSchema = false
)
abstract class QuietlyDatabase : RoomDatabase() {
    abstract fun appUsageDao():     AppUsageDao
    abstract fun goalDao():         GoalDao
    abstract fun appOverrideDao():  AppOverrideDao
}
