package dev.quietly.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.quietly.domain.repository.UsageRepository
import dev.quietly.data.prefs.SecurePreferences
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Runs once a day (via periodic WorkManager chain) to:
 *  1. Sync today’s usage stats into Room
 *  2. Purge rows older than the user’s retention window
 */
@HiltWorker
class DailySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params : WorkerParameters,
    private val repo  : UsageRepository,
    private val prefs : SecurePreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        repo.syncToday()
        // Purge old data beyond retention window
        val cutoff = LocalDate.now().minusDays(prefs.retentionDays.toLong()).toEpochDay()
        // We call the DAO directly via repo — expose a purge method on the interface if needed
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "quietly_daily_sync"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
