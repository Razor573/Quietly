package dev.quietly.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.quietly.domain.repository.GoalRepository
import dev.quietly.domain.repository.UsageRepository
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@HiltWorker
class GoalReminderWorker @AssistedInject constructor(
    @Assisted ctx:    Context,
    @Assisted params: WorkerParameters,
    private val usageRepo: UsageRepository,
    private val goalRepo:  GoalRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now().toEpochDay().toInt()
        usageRepo.syncToday()

        val nm = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Goal Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        goalRepo.observeAll().collect { goals ->
            goals.filter { it.reminderEnabled }.forEach { goal ->
                val usage = usageRepo.queryRange(today, today)
                    .firstOrNull { it.packageName == goal.packageName } ?: return@forEach
                val pct = usage.totalTimeMs.toFloat() / goal.dailyLimitMs.toFloat()
                if (pct >= 0.9f && pct < 1.1f) {
                    val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Screen time alert — ${goal.appLabel.ifBlank { goal.packageName }}")
                        .setContentText("You've used 90% of today's ${goal.dailyLimitMs / 3_600_000}h limit.")
                        .setAutoCancel(true)
                        .build()
                    nm.notify(goal.packageName.hashCode(), notif)
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "goal_reminders"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<GoalReminderWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "goal_reminder",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
