package dev.quietly.data.source

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.domain.RawUsageEvent
import dev.quietly.domain.UsageEventAggregator
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

    fun queryToday(): List<AppUsageEntity> {
        val nowMs = System.currentTimeMillis()
        val fromMs = nowMs - DAY_MS
        val fromEpochDay = fromMs / DAY_MS
        val toEpochDay = nowMs / DAY_MS
        return queryWindow(
            fromMs = fromMs,
            toMsExclusive = nowMs,
            fromEpochDay = fromEpochDay,
            toEpochDay = toEpochDay,
            nowMs = nowMs,
            diagnosticLabel = "last24h"
        )
    }

    fun queryRange(fromEpochDay: Int, toEpochDay: Int): List<AppUsageEntity> {
        val nowMs = System.currentTimeMillis()
        return queryWindow(
            fromMs = epochDayToMs(fromEpochDay.toLong()),
            toMsExclusive = epochDayToMs(toEpochDay.toLong() + 1L),
            fromEpochDay = fromEpochDay.toLong(),
            toEpochDay = toEpochDay.toLong(),
            nowMs = nowMs
        )
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
        return when (info.category) {
            CATEGORY_GAME_INT          -> "Games"
            CATEGORY_AUDIO_INT         -> "Audio"
            CATEGORY_VIDEO_INT         -> "Video"
            CATEGORY_IMAGE_INT         -> "Image"
            CATEGORY_SOCIAL_INT        -> "Social"
            CATEGORY_NEWS_INT          -> "News"
            CATEGORY_MAPS_INT          -> "Maps"
            CATEGORY_MUSIC_INT         -> "Music"
            CATEGORY_PRODUCTIVITY_INT  -> "Productivity"
            CATEGORY_ACCESSIBILITY_INT -> "Accessibility"
            CATEGORY_UNDEFINED_INT     -> "Other"
            else                       -> "Other"
        }
    }

    private fun queryWindow(
        fromMs: Long,
        toMsExclusive: Long,
        fromEpochDay: Long,
        toEpochDay: Long,
        nowMs: Long,
        diagnosticLabel: String? = null
    ): List<AppUsageEntity> {
        val events = usm.queryEvents(fromMs, toMsExclusive)
        val event = UsageEvents.Event()
        var totalEventsSeen = 0
        val mappedEvents = mutableListOf<RawUsageEvent>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            totalEventsSeen++
            val pkg = event.packageName ?: continue
            if (pkg in blockList) continue
            if (!isUserApp(pkg)) continue
            val mappedType = mapEventType(event.eventType) ?: continue
            mappedEvents += RawUsageEvent(
                packageName = pkg,
                eventType = mappedType,
                timestampMs = event.timeStamp
            )
        }

        if (diagnosticLabel != null) {
            Log.d(
                TAG,
                "Usage events seen ($diagnosticLabel): total=$totalEventsSeen, mapped=${mappedEvents.size}"
            )
        }

        return UsageEventAggregator.aggregate(
            events = mappedEvents,
            fromEpochDay = fromEpochDay,
            toEpochDay = toEpochDay,
            nowMs = nowMs
        ).map { daily ->
            AppUsageEntity(
                packageName = daily.packageName,
                dateEpochDay = daily.epochDay.toInt(),
                appLabel = getLabel(daily.packageName),
                totalTimeMs = daily.totalMs,
                launchCount = daily.launches,
                category = getCategory(daily.packageName),
                lastSeenEpochDay = daily.epochDay.toInt()
            )
        }.sortedWith(compareByDescending<AppUsageEntity> { it.dateEpochDay }.thenByDescending { it.totalTimeMs })
    }

    private fun mapEventType(eventType: Int): Int? = when (eventType) {
        UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventAggregator.EVENT_ACTIVITY_RESUMED
        UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventAggregator.EVENT_ACTIVITY_PAUSED
        UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventAggregator.EVENT_ACTIVITY_STOPPED
        else -> null
    }

    private fun epochDayToMs(epochDay: Long): Long = epochDay * DAY_MS

    private companion object {
        private const val TAG = "UsageStatsSource"
        private const val DAY_MS = 86_400_000L
        private const val CATEGORY_UNDEFINED_INT = -1
        private const val CATEGORY_GAME_INT = 0
        private const val CATEGORY_AUDIO_INT = 1
        private const val CATEGORY_VIDEO_INT = 3
        private const val CATEGORY_IMAGE_INT = 4
        private const val CATEGORY_SOCIAL_INT = 5
        private const val CATEGORY_NEWS_INT = 6
        private const val CATEGORY_MAPS_INT = 7
        private const val CATEGORY_MUSIC_INT = 8
        private const val CATEGORY_PRODUCTIVITY_INT = 9
        private const val CATEGORY_ACCESSIBILITY_INT = 11
    }
}
