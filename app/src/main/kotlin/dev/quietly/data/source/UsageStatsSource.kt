package dev.quietly.data.source

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quietly.data.db.entity.AppUsageEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsSource @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm  = ctx.packageManager

    /** System / launcher packages to always exclude */
    private val blockList = setOf(
        ctx.packageName,                          // Quietly itself
        "com.android.launcher3",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.samsung.android.app.springboard",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.vivo.launcher",
        "com.oneplus.launcher",
        "com.android.systemui",
        "com.android.settings",
        "android",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard"
    )

    /**
     * Returns today's usage for all non-system/non-blocked apps.
     * Uses ACTIVITY_RESUMED / ACTIVITY_PAUSED events for accurate foreground time.
     */
    fun queryToday(): List<AppUsageEntity> = queryRange(
        LocalDate.now().toEpochDay().toInt(),
        LocalDate.now().toEpochDay().toInt()
    )

    fun queryRange(fromEpochDay: Int, toEpochDay: Int): List<AppUsageEntity> {
        val fromMs = epochDayToMs(fromEpochDay)
        val toMs   = epochDayToMs(toEpochDay) + 86_400_000L

        // foreground-time accumulator: pkg -> (resumeTimestamp, totalMs, launchCount)
        data class Acc(var resumeTs: Long = -1L, var totalMs: Long = 0L, var launches: Int = 0)
        val acc = mutableMapOf<String, Acc>()

        val events = usm.queryEvents(fromMs, toMs)
        val event  = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            if (pkg in blockList) continue
            if (!isUserApp(pkg)) continue
            val a = acc.getOrPut(pkg) { Acc() }
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    a.resumeTs = event.timeStamp
                    a.launches++
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (a.resumeTs > 0) {
                        a.totalMs += (event.timeStamp - a.resumeTs).coerceAtLeast(0)
                        a.resumeTs = -1
                    }
                }
            }
        }
        // close any still-open sessions (app currently foreground)
        val nowMs = System.currentTimeMillis()
        acc.values.forEach { a ->
            if (a.resumeTs > 0) {
                a.totalMs += (nowMs - a.resumeTs).coerceAtLeast(0)
                a.resumeTs = -1
            }
        }

        val today = LocalDate.now().toEpochDay().toInt()
        return acc.entries
            .filter { it.value.totalMs > 0 }
            .map { (pkg, a) ->
                AppUsageEntity(
                    packageName  = pkg,
                    dateEpochDay = today,
                    appLabel     = getLabel(pkg),
                    totalTimeMs  = a.totalMs,
                    launchCount  = a.launches,
                    category     = getCategory(pkg)
                )
            }
            .sortedByDescending { it.totalTimeMs }
    }

    private fun isUserApp(pkg: String): Boolean = try {
        val flags = pm.getApplicationInfo(pkg, 0).flags
        (flags and ApplicationInfo.FLAG_SYSTEM) == 0
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun getLabel(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private fun getCategory(pkg: String): String = try {
        val info = pm.getApplicationInfo(pkg, 0)
        when (pm.getApplicationLabel(info).toString().lowercase()) {
            else -> categoryFromPmCategory(pm.getApplicationInfo(pkg, 0))
        }
    } catch (_: Exception) { "Other" }

    private fun categoryFromPmCategory(info: ApplicationInfo): String {
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME            -> "Games"
            ApplicationInfo.CATEGORY_SOCIAL          -> "Social"
            ApplicationInfo.CATEGORY_VIDEO           -> "Video"
            ApplicationInfo.CATEGORY_NEWS            -> "News"
            ApplicationInfo.CATEGORY_MAPS            -> "Maps"
            ApplicationInfo.CATEGORY_MUSIC           -> "Music"
            ApplicationInfo.CATEGORY_PRODUCTIVITY    -> "Productivity"
            ApplicationInfo.CATEGORY_ACCESSIBILITY   -> "Accessibility"
            else                                     -> "Other"
        }
    }

    private fun epochDayToMs(epochDay: Int): Long =
        epochDay.toLong() * 86_400_000L
}
