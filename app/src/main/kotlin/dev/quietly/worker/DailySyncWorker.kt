package dev.quietly.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.quietly.data.prefs.SecurePreferences
import dev.quietly.domain.repository.UsageRepository
import java.util.concurrent.TimeUnit

@HiltWorker
class DailySyncWorker @AssistedInject constructor(
    @Assisted ctx:    Context,
    @Assisted params: WorkerParameters,
    private val repo:  UsageRepository,
    private val prefs: SecurePreferences
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        repo.syncToday()
        repo.purgeOld(prefs.retentionDays)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                ).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "daily_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
