package dev.quietly.data.source

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.domain.RawUsageEvent
import dev.quietly.domain.UsageEventAggregator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

// API-31+ category int constants (avoids KSP unresolved-reference on minSdk 26)
private const val CATEGORY_MUSIC_INT          = 8
private const val CATEGORY_PRODUCTIVITY_INT   = 9
private const val CATEGORY_ACCESSIBILITY_INT  = 11

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

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Query today's usage from local midnight until now. */
    fun queryToday(): List<AppUsageEntity> {
        val nowMs = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val todayLocalDate = LocalDate.now()
        val todayEpochDay = todayLocalDate.toEpochDay()
        val startOfTodayMs = todayLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        Log.d(TAG, "queryToday: todayEpochDay=$todayEpochDay startOfTodayMs=$startOfTodayMs")
        return queryWindow(
            fromMs          = startOfTodayMs,
            toMsExclusive   = nowMs,
            fromEpochDay    = todayEpochDay,
            toEpochDay      = todayEpochDay,
            nowMs           = nowMs,
            diagnosticLabel = "today"
        )
    }

    /** Query a range of local-timezone days (both ends inclusive). */
    fun queryRange(fromEpochDay: Int, toEpochDay: Int): List<AppUsageEntity> {
        val nowMs = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val fromLocalDate = LocalDate.ofEpochDay(fromEpochDay.toLong())
        val toLocalDate = LocalDate.ofEpochDay(toEpochDay.toLong())
        val fromMs = fromLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val toMsExclusive = toLocalDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli().coerceAtMost(nowMs + 1000L)
        return queryWindow(
            fromMs        = fromMs,
            toMsExclusive = toMsExclusive,
            fromEpochDay  = fromEpochDay.toLong(),
            toEpochDay    = toEpochDay.toLong(),
            nowMs         = nowMs
        )
    }

    /** Returns all installed user-launchable apps. */
    fun getInstalledUserApps(): List<AppUsageEntity> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolves: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val myPkg = ctx.packageName
        val todayEpochDay = LocalDate.now().toEpochDay().toInt()
        return resolves
            .asSequence()
            .map { it.activityInfo.packageName }
            .filter { it != myPkg && it !in blockList }
            .distinct()
            .map { pkg ->
                AppUsageEntity(
                    packageName      = pkg,
                    dateEpochDay     = todayEpochDay,
                    appLabel         = getLabel(pkg),
                    totalTimeMs      = 0L,
                    launchCount      = 0,
                    lastSeenEpochDay = todayEpochDay,
                    category         = getCategory(pkg)
                )
            }
            .sortedBy { it.appLabel.lowercase() }
            .toList()
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private fun queryWindow(
        fromMs: Long,
        toMsExclusive: Long,
        fromEpochDay: Long,
        toEpochDay: Long,
        nowMs: Long,
        diagnosticLabel: String = ""
    ): List<AppUsageEntity> {
        val events = usm.queryEvents(fromMs, toMsExclusive)
        val event  = UsageEvents.Event()
        val rawEvents = mutableListOf<RawUsageEvent>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg in blockList) continue
            if (!isUserApp(pkg))  continue
            rawEvents.add(
                RawUsageEvent(
                    packageName = pkg,
                    eventType   = event.eventType,
                    timestampMs = event.timeStamp,
                    className   = event.className
                )
            )
        }

        if (diagnosticLabel.isNotEmpty()) {
            Log.d(TAG, "[$diagnosticLabel] total raw events: ${rawEvents.size}")
        }

        val daily = UsageEventAggregator.aggregate(
            events       = rawEvents,
            fromEpochDay = fromEpochDay,
            toEpochDay   = toEpochDay,
            nowMs        = nowMs
        )

        if (diagnosticLabel.isNotEmpty()) {
            Log.d(TAG, "[$diagnosticLabel] aggregated entries: ${daily.size}")
            daily.sortedByDescending { it.totalMs }.take(5).forEach {
                Log.d(TAG, "  ${it.packageName}: ${it.totalMs / 60_000}min")
            }
        }

        if (fromEpochDay != toEpochDay) {
            return daily.map { d ->
                AppUsageEntity(
                    packageName      = d.packageName,
                    dateEpochDay     = d.epochDay.toInt(),
                    appLabel         = getLabel(d.packageName),
                    totalTimeMs      = d.totalMs,
                    launchCount      = d.launches,
                    lastSeenEpochDay = d.epochDay.toInt(),
                    category         = getCategory(d.packageName)
                )
            }
        }

        val dailyMap = daily.associateBy { it.packageName }

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolves: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val myPkg = ctx.packageName
        val launcherPkgs = resolves
            .asSequence()
            .map { it.activityInfo.packageName }
            .filter { it != myPkg && it !in blockList }
            .distinct()
            .toSet()

        val allPkgs = (dailyMap.keys + launcherPkgs)

        return allPkgs.map { pkg ->
            val d = dailyMap[pkg]
            if (d != null) {
                AppUsageEntity(
                    packageName  = d.packageName,
                    dateEpochDay = d.epochDay.toInt(),
                    appLabel     = getLabel(d.packageName),
                    totalTimeMs  = d.totalMs,
                    launchCount  = d.launches,
                    lastSeenEpochDay = d.epochDay.toInt(),
                    category     = getCategory(d.packageName)
                )
            } else {
                AppUsageEntity(
                    packageName  = pkg,
                    dateEpochDay = toEpochDay.toInt(),
                    appLabel     = getLabel(pkg),
                    totalTimeMs  = 0L,
                    launchCount  = 0,
                    lastSeenEpochDay = toEpochDay.toInt(),
                    category     = getCategory(pkg)
                )
            }
        }.sortedWith(
            compareByDescending<AppUsageEntity> { it.totalTimeMs }
                .thenBy { it.appLabel.lowercase() }
        )
    }

    /** Resolve a human-readable label for a package (falls back to package name). */
    fun getLabel(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) { pkg }

    /** Map ApplicationInfo.category to a plain string. */
    fun getCategory(pkg: String): String = try {
        when (pm.getApplicationInfo(pkg, 0).category) {
            ApplicationInfo.CATEGORY_GAME    -> "Games"
            ApplicationInfo.CATEGORY_SOCIAL  -> "Social"
            ApplicationInfo.CATEGORY_VIDEO   -> "Video"
            ApplicationInfo.CATEGORY_NEWS    -> "News"
            ApplicationInfo.CATEGORY_MAPS    -> "Maps"
            ApplicationInfo.CATEGORY_IMAGE   -> "Photos"
            CATEGORY_MUSIC_INT               -> "Music"
            CATEGORY_PRODUCTIVITY_INT        -> "Productivity"
            CATEGORY_ACCESSIBILITY_INT       -> "Accessibility"
            else                             -> "Other"
        }
    } catch (_: PackageManager.NameNotFoundException) { "Other" }

    /** Returns true if the package belongs to a user-installed app or user launchable app. */
    private fun isUserApp(pkg: String): Boolean = try {
        val appInfo = pm.getApplicationInfo(pkg, 0)
        // Check if it's not a system app, OR if it has a launcher intent (user-launchable app like Chrome or Youtube system updates)
        if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
            true
        } else {
            // Check if user launchable
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            launchIntent != null
        }
    } catch (_: PackageManager.NameNotFoundException) { false }

    /**
     * Converts an absolute timestamp to a local-timezone epoch day.
     */
    fun localEpochDay(timestampMs: Long): Long {
        return LocalDate.ofInstant(
            Instant.ofEpochMilli(timestampMs),
            ZoneId.systemDefault()
        ).toEpochDay()
    }

    companion object {
        private const val TAG    = "UsageStatsSource"
        private const val DAY_MS = 86_400_000L
    }
}
