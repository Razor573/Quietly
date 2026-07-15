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

/**
 * Hourly check: for any active goal where today’s usage exceeds 90% of the
 * daily limit, fire a local notification. No network required.
 */
@HiltWorker
class GoalReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params : WorkerParameters,
    private val usageRepo : UsageRepository,
    private val goalRepo  : GoalRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today  = LocalDate.now().toEpochDay()
        val goals  = goalRepo.activeGoals()
        val usages = usageRepo.topApps(today, today, limit = 100)
            .associateBy { it.packageName }

        goals.forEach { goal ->
            val used = usages[goal.packageName]?.totalTimeMs ?: return@forEach
            if (used >= goal.dailyLimitMs * 0.9) {
                notify(
                    pkg      = goal.packageName,
                    limitMs  = goal.dailyLimitMs,
                    usedMs   = used
                )
            }
        }
        return Result.success()
    }

    private fun notify(pkg: String, limitMs: Long, usedMs: Long) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Goal Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val remaining = ((limitMs - usedMs) / 60_000).coerceAtLeast(0)
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔇 Quietly — Goal reminder")
            .setContentText("$pkg: ${remaining}m remaining today")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(pkg.hashCode(), notif)
    }

    companion object {
        const val WORK_NAME = "quietly_goal_reminder"
        private const val CHANNEL_ID = "quietly_goals"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<GoalReminderWorker>(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
