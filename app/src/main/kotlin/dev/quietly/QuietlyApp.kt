package dev.quietly

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.quietly.worker.DailySyncWorker
import dev.quietly.worker.GoalReminderWorker
import javax.inject.Inject

@HiltAndroidApp
class QuietlyApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        DailySyncWorker.schedule(this)
        GoalReminderWorker.schedule(this)
    }
}
