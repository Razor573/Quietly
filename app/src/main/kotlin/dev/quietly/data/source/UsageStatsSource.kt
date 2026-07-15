package dev.quietly.data.source

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quietly.data.db.entity.AppUsageEntity
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads raw foreground events from UsageStatsManager and maps them to
 * AppUsageEntity rows. Uses a RESUME/PAUSE event-pair approach so we
 * never double-count overlapping sessions.
 */
@Singleton
class UsageStatsSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm  = context.packageManager

    fun queryDay(date: LocalDate): List<AppUsageEntity> {
        val zone     = ZoneId.systemDefault()
        val startMs  = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs    = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val epochDay = date.toEpochDay()

        // Accumulate per-package: totalTime, launchCount, lastUsed
        data class Acc(var totalMs: Long = 0, var launches: Int = 0, var lastUsed: Long = 0)
        val acc = mutableMapOf<String, Acc>()
        val resumeTime = mutableMapOf<String, Long>()

        val events = usm.queryEvents(startMs, endMs)
        val event  = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val a   = acc.getOrPut(pkg) { Acc() }

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    resumeTime[pkg] = event.timeStamp
                    a.launches++
                    a.lastUsed = maxOf(a.lastUsed, event.timeStamp)
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    resumeTime.remove(pkg)?.let { start ->
                        a.totalMs += (event.timeStamp - start).coerceAtLeast(0)
                    }
                }
            }
        }

        // If app is still in foreground at window end, close the session
        resumeTime.forEach { (pkg, start) ->
            acc.getOrPut(pkg) { Acc() }.totalMs += (endMs - start).coerceAtLeast(0)
        }

        return acc.map { (pkg, a) ->
            AppUsageEntity(
                packageName  = pkg,
                appLabel     = resolveLabel(pkg),
                dateEpochDay = epochDay,
                totalTimeMs  = a.totalMs,
                launchCount  = a.launches,
                lastUsedMs   = a.lastUsed
            )
        }
    }

    private fun resolveLabel(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) { pkg }
}
