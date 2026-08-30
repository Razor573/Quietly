package dev.quietly.data.source

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quietly.data.db.DailyUsageRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoricalUsageSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Extracts past daily usage history (default: 90 days) from the Android OS.
     */
    suspend fun fetchHistoricalDailyUsage(daysBack: Int = 90): List<DailyUsageRecord> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val zoneId = ZoneId.systemDefault()
        val startOfToday = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val startTime = startOfToday - TimeUnit.DAYS.toMillis(daysBack.toLong())

        // Whitelist only user-launchable packages
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val launchablePackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }.map { it.activityInfo.packageName }.toSet()

        val rawStats = usageStatsManager?.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            startOfToday
        ) ?: emptyList()

        val dailyMap = mutableMapOf<Pair<Long, String>, Long>()

        for (stat in rawStats) {
            val pkg = stat.packageName
            // Exclude system UI, installers, and non-launchable packages
            if (stat.totalTimeInForeground <= 0 ||
                pkg == context.packageName ||
                !launchablePackages.contains(pkg)
            ) {
                continue
            }

            val statDate = Instant.ofEpochMilli(stat.firstTimeStamp).atZone(zoneId).toLocalDate()
            val epochDay = statDate.toEpochDay()
            val key = Pair(epochDay, pkg)

            dailyMap[key] = (dailyMap[key] ?: 0L) + stat.totalTimeInForeground
        }

        dailyMap.map { (key, durationMillis) ->
            DailyUsageRecord(
                epochDay = key.first,
                packageName = key.second,
                usageMinutes = (durationMillis / (1000 * 60)).toInt()
            )
        }
    }
}
