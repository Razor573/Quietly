package dev.quietly.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.quietly.data.db.AppUsageDao
import dev.quietly.data.db.GoalDao
import dev.quietly.data.db.QuietlyDatabase
import dev.quietly.data.db.dao.AppOverrideDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE app_usage ADD COLUMN launchCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE app_usage ADD COLUMN category TEXT NOT NULL DEFAULT 'Other'")
            db.execSQL("ALTER TABLE goals ADD COLUMN appLabel TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE goals ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add lastSeenEpochDay to app_usage (defaults to dateEpochDay semantically,
            // but SQLite cannot reference other columns in ALTER TABLE, so default to 0
            // and let the next sync backfill it)
            db.execSQL(
                "ALTER TABLE app_usage ADD COLUMN lastSeenEpochDay INTEGER NOT NULL DEFAULT 0"
            )
            // Create per-app overrides table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS app_overrides (
                    packageName TEXT NOT NULL PRIMARY KEY,
                    overrideType TEXT NOT NULL
                )
            """.trimIndent())
        }
    }

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): QuietlyDatabase =
        Room.databaseBuilder(ctx, QuietlyDatabase::class.java, "quietly.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideUsageDao(db: QuietlyDatabase): AppUsageDao         = db.appUsageDao()
    @Provides fun provideGoalDao(db: QuietlyDatabase): GoalDao               = db.goalDao()
    @Provides fun provideOverrideDao(db: QuietlyDatabase): AppOverrideDao   = db.appOverrideDao()
}
