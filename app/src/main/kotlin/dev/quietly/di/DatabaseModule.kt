package dev.quietly.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.quietly.data.db.QuietlyDatabase
import dev.quietly.data.db.dao.AppUsageDao
import dev.quietly.data.db.dao.GoalDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): QuietlyDatabase =
        Room.databaseBuilder(ctx, QuietlyDatabase::class.java, "quietly.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideAppUsageDao(db: QuietlyDatabase): AppUsageDao = db.appUsageDao()
    @Provides fun provideGoalDao(db: QuietlyDatabase): GoalDao = db.goalDao()
}
