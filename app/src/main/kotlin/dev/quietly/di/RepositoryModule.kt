package dev.quietly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quietly.domain.repository.UsageRepository
import dev.quietly.data.repository.UsageRepositoryImpl
import dev.quietly.domain.repository.GoalRepository
import dev.quietly.data.repository.GoalRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindUsageRepo(impl: UsageRepositoryImpl): UsageRepository

    @Binds @Singleton
    abstract fun bindGoalRepo(impl: GoalRepositoryImpl): GoalRepository
}
