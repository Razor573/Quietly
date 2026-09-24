package dev.quietly.data.source

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

// API-31+ category int constants (avoids KSP unresolved-reference on minSdk 26)
private const val CATEGORY_MUSIC_INT         = 8
private const val CATEGORY_PRODUCTIVITY_INT  = 9
private const val CATEGORY_ACCESSIBILITY_INT = 11

@Singleton
class UsageStatsSource @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm  = ctx.packageManager

    private val blockList = setOf(
        ctx.packageName,
        "android",
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
        "com.syntellia.fleksy.keyboard",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.miui.home",
        "com.oppo.launcher",
        "com.huawei.android.launcher",
        "com.oneplus.launcher",
        "com.teslacoilsw.launcher",
        "com.microsoft.launcher",
        "com.transsion.launcher",
        "com.samsung.android.app.aodservice",
        "com.sec.android.cover",
        "com.samsung.android.dynamiclock",
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.rubin.app",
        "com.google.android.ambientindication",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.samsung.android.mdecservice",
        "com.samsung.android.smartcallprovider",
        "com.samsung.android.visualeffect"
    )

    private val knownUserFacingApps = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.canary",
        "com.chrome.dev",
        "com.google.android.apps.messaging",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.android.settings",
        "com.google.android.settings",
        "com.android.vending",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.photos",
        "com.sec.android.gallery3d",
        "com.android.camera",
        "com.sec.android.app.camera",
        "com.google.android.GoogleCamera",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.google.android.calculator",
        "com.android.calculator2",
        "com.sec.android.app.popupcalculator",
        "com.google.android.calendar",
        "com.samsung.android.calendar",
        "com.google.android.gm",
        "com.google.android.apps.maps",
        "com.google.android.youtube",
        "com.amazon.mShop.android.shopping"
    )

    private val dayTelemetryCache = java.util.concurrent.ConcurrentHashMap<Int, dev.quietly.domain.ml.DayTelemetry>()

    // In-memory caches to eliminate IPC latency
    private val launchablePackagesCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val launcherPackagesCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val imePackagesCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val labelCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val categoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val relevanceCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    @Volatile private var cachedLauncher: String? = null
    @Volatile private var cachesInitialized = false

    private fun ensureCaches() {
        if (cachesInitialized) return
        synchronized(this) {
            if (cachesInitialized) return
            try {
                val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(mainIntent, 0)
                }
                for (info in resolves) {
                    val pkg = info.activityInfo?.packageName ?: continue
                    launchablePackagesCache.add(pkg)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pre-cache launcher activities", e)
            }

            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                val homeResolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(homeIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(homeIntent, 0)
                }
                for (info in homeResolves) {
                    val pkg = info.activityInfo?.packageName ?: continue
                    launcherPackagesCache.add(pkg)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pre-cache home launchers", e)
            }

            try {
                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.inputMethodList?.forEach { ime ->
                    imePackagesCache.add(ime.packageName)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pre-cache keyboard IMEs", e)
            }

            cachedLauncher = resolveDefaultLauncher()
            cachesInitialized = true
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Query today's usage from local midnight until now. */
    fun queryToday(): List<AppUsageEntity> {
        val todayEpochDay = LocalDate.now().toEpochDay().toInt()
        return queryDay(todayEpochDay)
    }

    /** Query usage for a single local-timezone epoch day. */
    fun queryDay(epochDay: Int): List<AppUsageEntity> {
        val nowMs = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        return querySingleDay(epochDay, nowMs, zoneId)
    }

    /**
     * Query usage for a range of local days from fromEpochDay to toEpochDay.
     * Evaluates each day in strict isolation so that screen time never leaks,
     * overcounts, or accumulates between consecutive days.
     */
    fun queryRange(fromEpochDay: Int, toEpochDay: Int): List<AppUsageEntity> {
        val nowMs = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val results = mutableListOf<AppUsageEntity>()
        for (day in fromEpochDay..toEpochDay) {
            results.addAll(querySingleDay(day, nowMs, zoneId))
        }
        return results.sortedWith(
            compareBy<AppUsageEntity> { it.dateEpochDay }
                .thenByDescending { it.totalTimeMs }
                .thenBy { it.appLabel.lowercase() }
        )
    }

    /**
     * Accurately calculates app usage for a single calendar day matching OS Digital Wellbeing.
     *
     * 1. Uses the unaggregated UsageEvents stream to calculate exact foreground durations
     *    and launch counts, closed accurately on app transitions and screen-off events.
     * 2. Completely isolates day boundaries [dayStartMs, dayEndMs] to prevent multi-day leakage.
     * 3. Falls back to INTERVAL_DAILY deduplicated slices only when UsageEvents is pruned (>7 days).
     */
    fun querySingleDay(day: Int, nowMs: Long, zoneId: ZoneId): List<AppUsageEntity> {
        return queryDayWithTelemetry(day, nowMs, zoneId).first
    }

    fun getDayTelemetry(day: Int): dev.quietly.domain.ml.DayTelemetry {
        return dayTelemetryCache[day] ?: run {
            val nowMs = System.currentTimeMillis()
            val zoneId = ZoneId.systemDefault()
            queryDayWithTelemetry(day, nowMs, zoneId).second
        }
    }

    /**
     * Accurately calculates app usage and rich phone telemetry for a single calendar day.
     */
    fun queryDayWithTelemetry(
        day: Int,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<List<AppUsageEntity>, dev.quietly.domain.ml.DayTelemetry> {
        val fromDate = LocalDate.ofEpochDay(day.toLong())
        val dayStartMs = fromDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val todayEpochDay = LocalDate.now().toEpochDay().toInt()
        val isToday = (day == todayEpochDay)
        val dayEndMs = if (isToday) {
            nowMs
        } else {
            fromDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli().coerceAtMost(nowMs)
        }

        if (dayStartMs >= nowMs) {
            val emptyTel = dev.quietly.domain.ml.DayTelemetry(day, 0L)
            return emptyList<AppUsageEntity>() to emptyTel
        }
        val maxDayMs = (dayEndMs - dayStartMs).coerceAtLeast(1L)

        ensureCaches()

        val timeByPkg = mutableMapOf<String, Long>()
        val launchesByPkg = mutableMapOf<String, Int>()
        val sessionCounts = mutableMapOf<String, Int>()
        val longestSessions = mutableMapOf<String, Long>()
        val hourlyDistribution = LongArray(24)
        var passiveSessionTimeMs = 0L
        var mediaTimeMs = 0L

        var usedUsageEvents = false

        // 1. Process real-time UsageEvents stream
        val eventsQueryStart = (dayStartMs - 1800_000L).coerceAtLeast(0L)
        try {
            val events = usm.queryEvents(eventsQueryStart, dayEndMs)
            if (events != null && events.hasNextEvent()) {
                var currentPkg: String? = null
                var sessionStartMs = 0L
                var screenInteractive = true
                var hasRelevantEvents = false
                var lastEventTime = eventsQueryStart

                fun closeSession(atTimestamp: Long) {
                    val pkg = currentPkg ?: return
                    if (!screenInteractive) return
                    val sStart = maxOf(sessionStartMs, dayStartMs)
                    val sEnd = minOf(atTimestamp, dayEndMs)
                    if (sEnd > sStart && isRelevantPackage(pkg)) {
                        // Protect against missing screen-off events:
                        val duration = minOf(sEnd - sStart, 2 * 3600_000L)
                        timeByPkg[pkg] = (timeByPkg[pkg] ?: 0L) + duration
                        sessionCounts[pkg] = (sessionCounts[pkg] ?: 0) + 1
                        longestSessions[pkg] = maxOf(longestSessions[pkg] ?: 0L, duration)

                        val cat = getCategory(pkg)
                        val isMedia = (cat == "Video" || cat == "Music" || cat == "Games" ||
                                       pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("spotify"))
                        if (isMedia) {
                            mediaTimeMs += duration
                        } else if (duration > 25 * 60_000L) {
                            passiveSessionTimeMs += duration
                        }

                        // Hourly distribution accumulation
                        for (h in 0..23) {
                            val hStart = dayStartMs + h * 3600_000L
                            val hEnd = hStart + 3600_000L
                            val overlap = maxOf(0L, minOf(sEnd, hEnd) - maxOf(sStart, hStart))
                            if (overlap > 0L) {
                                hourlyDistribution[h] += minOf(overlap, duration)
                            }
                        }
                    }
                    sessionStartMs = atTimestamp
                }

                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    val t = event.timeStamp

                    // Detect gap where device went idle (> 30 mins with no OS events)
                    if (currentPkg != null && screenInteractive && (t - lastEventTime > 30 * 60_000L)) {
                        closeSession(minOf(t, lastEventTime + 10 * 60_000L))
                        screenInteractive = false
                    }
                    lastEventTime = t

                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            closeSession(t)
                            screenInteractive = true
                            if (isRelevantPackage(pkg)) {
                                hasRelevantEvents = true
                                if (pkg != currentPkg && t in dayStartMs..dayEndMs) {
                                    launchesByPkg[pkg] = (launchesByPkg[pkg] ?: 0) + 1
                                }
                                currentPkg = pkg
                                sessionStartMs = t
                            } else {
                                currentPkg = null
                                sessionStartMs = t
                            }
                        }

                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED -> {
                            if (currentPkg == pkg) {
                                closeSession(t)
                                currentPkg = null
                                sessionStartMs = t
                            }
                        }

                        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                        UsageEvents.Event.KEYGUARD_SHOWN,
                        UsageEvents.Event.DEVICE_SHUTDOWN -> {
                            closeSession(t)
                            screenInteractive = false
                            sessionStartMs = t
                        }

                        UsageEvents.Event.SCREEN_INTERACTIVE,
                        UsageEvents.Event.KEYGUARD_HIDDEN,
                        UsageEvents.Event.DEVICE_STARTUP -> {
                            screenInteractive = true
                            sessionStartMs = t
                        }
                    }
                }

                // Final closure at dayEndMs
                if (currentPkg != null && screenInteractive) {
                    if (isToday) {
                        val activeEnd = minOf(dayEndMs, lastEventTime + 15 * 60_000L)
                        closeSession(activeEnd)
                    } else {
                        if (dayEndMs - lastEventTime <= 15 * 60_000L) {
                            closeSession(dayEndMs)
                        } else {
                            closeSession(minOf(dayEndMs, lastEventTime + 5 * 60_000L))
                        }
                    }
                }

                if (hasRelevantEvents) {
                    usedUsageEvents = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryEvents failed for day $day", e)
        }

        // 2. Fallback: queryUsageStats(INTERVAL_DAILY) if UsageEvents was pruned
        if (!usedUsageEvents || timeByPkg.isEmpty()) {
            try {
                val statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStartMs, dayEndMs)
                if (!statsList.isNullOrEmpty()) {
                    val grouped = statsList.filter { isRelevantPackage(it.packageName ?: "") }
                        .groupBy { it.packageName ?: "" }
                    for ((pkg, list) in grouped) {
                        val targetMid = (dayStartMs + dayEndMs) / 2
                        val bestSlice = list.minByOrNull { slice ->
                            val sliceMid = (slice.firstTimeStamp + slice.lastTimeStamp) / 2
                            Math.abs(sliceMid - targetMid)
                        }
                        if (bestSlice != null && bestSlice.totalTimeInForeground > 0L) {
                            val fg = bestSlice.totalTimeInForeground
                            if (bestSlice.lastTimeStamp >= dayStartMs && bestSlice.firstTimeStamp <= dayEndMs) {
                                timeByPkg[pkg] = minOf(fg, maxDayMs)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "queryUsageStats fallback failed for day $day", e)
            }
        }

        // 3. Strict clamping
        for ((pkg, time) in timeByPkg) {
            timeByPkg[pkg] = minOf(time, maxDayMs)
        }

        val totalDaySum = timeByPkg.values.sum()
        if (totalDaySum > maxDayMs && totalDaySum > 0L) {
            val scale = maxDayMs.toDouble() / totalDaySum.toDouble()
            for ((pkg, time) in timeByPkg) {
                timeByPkg[pkg] = (time * scale).toLong().coerceAtLeast(1L)
            }
        }

        val allPkgs = (timeByPkg.keys + launchesByPkg.keys).filter { isRelevantPackage(it) }

        val appTelemetries = mutableMapOf<String, dev.quietly.domain.ml.AppTelemetry>()
        val entityList = allPkgs.mapNotNull { pkg ->
            val totalTimeMs = timeByPkg[pkg] ?: 0L
            val launches = launchesByPkg[pkg] ?: if (totalTimeMs > 0L) 1 else 0
            if (totalTimeMs <= 0L && launches <= 0) return@mapNotNull null

            val cat = getCategory(pkg)
            val isMedia = (cat == "Video" || cat == "Music" || cat == "Games" ||
                           pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("spotify"))

            appTelemetries[pkg] = dev.quietly.domain.ml.AppTelemetry(
                packageName = pkg,
                rawMs = totalTimeMs,
                launches = launches,
                sessionCount = sessionCounts[pkg] ?: 1,
                longestSessionMs = longestSessions[pkg] ?: totalTimeMs,
                isMediaApp = isMedia
            )

            AppUsageEntity(
                packageName      = pkg,
                dateEpochDay     = day,
                appLabel         = getLabel(pkg),
                totalTimeMs      = totalTimeMs,
                launchCount      = launches,
                lastSeenEpochDay = day,
                category         = cat
            )
        }.sortedWith(
            compareByDescending<AppUsageEntity> { it.totalTimeMs }
                .thenBy { it.appLabel.lowercase() }
        )

        val telemetry = dev.quietly.domain.ml.DayTelemetry(
            epochDay = day,
            rawTotalMs = timeByPkg.values.sum(),
            totalLaunches = launchesByPkg.values.sum(),
            sessionCount = sessionCounts.values.sum(),
            longestSessionMs = longestSessions.values.maxOrNull() ?: 0L,
            mediaTimeMs = mediaTimeMs,
            passiveSessionTimeMs = passiveSessionTimeMs,
            hourlyDistributionMs = hourlyDistribution.toList(),
            appTelemetries = appTelemetries
        )
        dayTelemetryCache[day] = telemetry

        return entityList to telemetry
    }

    /** Determine the local epoch day that a daily UsageStats interval belongs to. */
    fun resolveEpochDay(us: UsageStats, zoneId: ZoneId): Long {
        val mid = if (us.firstTimeStamp > 0L && us.lastTimeStamp >= us.firstTimeStamp) {
            (us.firstTimeStamp + us.lastTimeStamp) / 2
        } else if (us.lastTimeStamp > 0L) {
            us.lastTimeStamp
        } else if (us.firstTimeStamp > 0L) {
            us.firstTimeStamp
        } else {
            return -1L
        }
        return LocalDate.ofInstant(Instant.ofEpochMilli(mid), zoneId).toEpochDay()
    }

    /** Returns all installed user-launchable apps. */
    fun getInstalledUserApps(): List<AppUsageEntity> {
        ensureCaches()
        val myPkg = ctx.packageName
        val todayEpochDay = LocalDate.now().toEpochDay().toInt()
        return launchablePackagesCache
            .filter { it != myPkg && isRelevantPackage(it) }
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
    }

    /** Returns true if the package should be counted or listed. */
    fun isRelevantPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        if (pkg == ctx.packageName) return false
        if (pkg == "android") return false
        if (pkg == "com.android.systemui" || pkg.startsWith("com.android.systemui.")) return false
        if (pkg in blockList) return false

        relevanceCache[pkg]?.let { return it }

        ensureCaches()
        val defaultLauncher = cachedLauncher
        if (defaultLauncher != null && pkg == defaultLauncher) {
            relevanceCache[pkg] = false
            return false
        }
        if (pkg in launcherPackagesCache || pkg in imePackagesCache) {
            relevanceCache[pkg] = false
            return false
        }

        // All non-launcher, non-systemui, non-keyboard foreground apps are valid user-facing apps!
        relevanceCache[pkg] = true
        return true
    }

    private fun resolveDefaultLauncher(): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, 0)
            }
            resolveInfo?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }
    }

    /** Resolve a human-readable label for a package (cached in memory). */
    fun getLabel(pkg: String): String {
        labelCache[pkg]?.let { return it }
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            cleanPackageLabel(pkg)
        }
        labelCache[pkg] = label
        return label
    }

    private fun cleanPackageLabel(pkg: String): String {
        val lastSegment = pkg.substringAfterLast('.')
        return lastSegment.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    /** Map ApplicationInfo.category to a plain string (cached in memory). */
    fun getCategory(pkg: String): String {
        categoryCache[pkg]?.let { return it }
        val category = try {
            val cat = pm.getApplicationInfo(pkg, 0).category
            when (cat) {
                ApplicationInfo.CATEGORY_GAME          -> "Games"
                ApplicationInfo.CATEGORY_SOCIAL        -> "Social"
                ApplicationInfo.CATEGORY_VIDEO         -> "Video"
                ApplicationInfo.CATEGORY_NEWS          -> "News"
                ApplicationInfo.CATEGORY_MAPS          -> "Maps"
                ApplicationInfo.CATEGORY_IMAGE         -> "Photos"
                CATEGORY_MUSIC_INT                     -> "Music"
                CATEGORY_PRODUCTIVITY_INT              -> "Productivity"
                CATEGORY_ACCESSIBILITY_INT             -> "Accessibility"
                else                                   -> inferCategory(pkg)
            }
        } catch (_: Exception) {
            inferCategory(pkg)
        }
        categoryCache[pkg] = category
        return category
    }

    private fun inferCategory(pkg: String): String {
        val lower = pkg.lowercase()
        return when {
            lower.contains("instagram") || lower.contains("whatsapp") || lower.contains("facebook") ||
            lower.contains("reddit") || lower.contains("twitter") || lower.contains("tiktok") ||
            lower.contains("telegram") || lower.contains("snapchat") || lower.contains("discord") ||
            lower.contains("social") || lower.contains("messenger") -> "Social"

            lower.contains("youtube") || lower.contains("netflix") || lower.contains("twitch") ||
            lower.contains("hulu") || lower.contains("disney") || lower.contains("primevideo") ||
            lower.contains("video") || lower.contains("player") -> "Video"

            lower.contains("spotify") || lower.contains("music") || lower.contains("soundcloud") ||
            lower.contains("audio") || lower.contains("podcast") -> "Music"

            lower.contains("game") || lower.contains("play") || lower.contains("supercell") ||
            lower.contains("rovio") || lower.contains("king") || lower.contains("unity") -> "Games"

            lower.contains("chrome") || lower.contains("browser") || lower.contains("search") ||
            lower.contains("quicksearchbox") || lower.contains("drive") || lower.contains("docs") ||
            lower.contains("sheets") || lower.contains("office") || lower.contains("notes") ||
            lower.contains("gmail") || lower.contains("mail") || lower.contains("calendar") ||
            lower.contains("calculator") || lower.contains("clock") || lower.contains("deskclock") -> "Productivity"

            lower.contains("photo") || lower.contains("gallery") || lower.contains("camera") -> "Photos"
            lower.contains("map") || lower.contains("waze") || lower.contains("nav") -> "Maps"
            lower.contains("news") || lower.contains("feed") -> "News"
            else -> "Other"
        }
    }

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
        private const val TAG = "UsageStatsSource"
    }
}
