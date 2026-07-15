package dev.quietly.data.source

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
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

    private val blockList = setOf(
        ctx.packageName,
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

    // API 31+ category int values — use raw ints so KSP/kotlinc never
    // sees an unresolved symbol reference on minSdk 26 build runners.
    // Values sourced from AOSP ApplicationInfo.java (stable, never change):
    //   CATEGORY_MUSIC = 8, CATEGORY_PRODUCTIVITY = 9, CATEGORY_ACCESSIBILITY = 11
    private val CATEGORY_MUSIC_INT         = 8
    private val CATEGORY_PRODUCTIVITY_INT  = 9
    private val CATEGORY_ACCESSIBILITY_INT = 11

    fun queryToday(): List<AppUsageEntity> = queryRange(
        LocalDate.now().toEpochDay().toInt(),
        LocalDate.now().toEpochDay().toInt()
    )

    fun queryRange(fromEpochDay: Int, toEpochDay: Int): List<AppUsageEntity> {
        val fromMs = epochDayToMs(fromEpochDay)
        val toMs   = epochDayToMs(toEpochDay) + 86_400_000L

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
        categoryFromPmCategory(pm.getApplicationInfo(pkg, 0))
    } catch (_: Exception) { "Other" }

    private fun categoryFromPmCategory(info: ApplicationInfo): String {
        // These constants are available on all API levels >= 26
        val baseCategory = when (info.category) {
            ApplicationInfo.CATEGORY_GAME   -> "Games"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_VIDEO  -> "Video"
            ApplicationInfo.CATEGORY_NEWS   -> "News"
            ApplicationInfo.CATEGORY_MAPS   -> "Maps"
            else                            -> null
        }
        if (baseCategory != null) return baseCategory

        // Use raw int values for API 31+ categories — avoids KSP unresolved
        // reference on minSdk 26 runners regardless of runtime version guards.
        return when (info.category) {
            CATEGORY_MUSIC_INT         -> "Music"
            CATEGORY_PRODUCTIVITY_INT  -> "Productivity"
            CATEGORY_ACCESSIBILITY_INT -> "Accessibility"
            else                       -> "Other"
        }
    }

    private fun epochDayToMs(epochDay: Int): Long =
        epochDay.toLong() * 86_400_000L
}
