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

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): QuietlyDatabase =
        Room.databaseBuilder(ctx, QuietlyDatabase::class.java, "quietly.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideUsageDao(db: QuietlyDatabase): AppUsageDao = db.appUsageDao()
    @Provides fun provideGoalDao(db: QuietlyDatabase): GoalDao = db.goalDao()
}
