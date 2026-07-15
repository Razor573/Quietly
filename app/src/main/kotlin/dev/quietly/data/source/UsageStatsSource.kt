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
 * Reads raw foreground events from UsageStatsManager.
 * Filters:
 *  - own package (dev.quietly) so the app never shows itself
 *  - system launcher / Android shell packages
 *  - entries with zero foreground time
 */
@Singleton
class UsageStatsSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm  = context.packageManager

    private val ownPackage = context.packageName

    // Packages that appear in events but are never real user-facing apps
    private val systemPackageBlocklist = setOf(
        "android",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.oneplus.launcher",
        ownPackage   // never count Quietly itself
    )

    fun queryDay(date: LocalDate): List<AppUsageEntity> {
        val zone     = ZoneId.systemDefault()
        val startMs  = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs    = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val epochDay = date.toEpochDay()

        data class Acc(var totalMs: Long = 0L, var launches: Int = 0, var lastUsed: Long = 0L)
        val acc        = mutableMapOf<String, Acc>()
        val resumeTime = mutableMapOf<String, Long>()

        val events = usm.queryEvents(startMs, endMs)
        val event  = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg in systemPackageBlocklist) continue

            val a = acc.getOrPut(pkg) { Acc() }

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    resumeTime[pkg] = event.timeStamp
                    a.launches++
                    if (event.timeStamp > a.lastUsed) a.lastUsed = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    resumeTime.remove(pkg)?.let { start ->
                        a.totalMs += (event.timeStamp - start).coerceAtLeast(0)
                    }
                }
            }
        }

        // Close any still-open sessions
        val now = System.currentTimeMillis().coerceAtMost(endMs)
        resumeTime.forEach { (pkg, start) ->
            if (pkg !in systemPackageBlocklist)
                acc.getOrPut(pkg) { Acc() }.totalMs += (now - start).coerceAtLeast(0)
        }

        // Only return apps with actual foreground time > 0
        return acc
            .filter { (_, a) -> a.totalMs > 0L }
            .map { (pkg, a) ->
                AppUsageEntity(
                    packageName  = pkg,
                    appLabel     = resolveLabel(pkg),
                    dateEpochDay = epochDay,
                    totalTimeMs  = a.totalMs,
                    launchCount  = a.launches,
                    lastUsedMs   = a.lastUsed
                )
            }
            .sortedByDescending { it.totalTimeMs }
    }

    private fun resolveLabel(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) { pkg }
}
