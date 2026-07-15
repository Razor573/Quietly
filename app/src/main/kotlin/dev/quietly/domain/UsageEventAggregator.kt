package dev.quietly.domain

private const val DAY_MS = 86_400_000L

data class RawUsageEvent(
    val packageName: String,
    val eventType: Int,
    val timestampMs: Long
)

data class DailyAppUsage(
    val packageName: String,
    val epochDay: Long,
    val totalMs: Long,
    val launches: Int
)

private data class Session(val startMs: Long, var endMs: Long? = null)
private data class PackageSessions(
    val sessions: MutableList<Session> = mutableListOf()
)

object UsageEventAggregator {
    const val EVENT_ACTIVITY_RESUMED = 1
    const val EVENT_ACTIVITY_PAUSED = 2
    const val EVENT_ACTIVITY_STOPPED = 3

    fun aggregate(
        events: List<RawUsageEvent>,
        fromEpochDay: Long,
        toEpochDay: Long,
        nowMs: Long = System.currentTimeMillis()
    ): List<DailyAppUsage> {
        if (events.isEmpty() || toEpochDay < fromEpochDay) return emptyList()

        val fromMs = fromEpochDay * DAY_MS
        val toMsExclusive = (toEpochDay + 1L) * DAY_MS

        val sessionsByPackage = mutableMapOf<String, PackageSessions>()
        val launchesByKey = mutableMapOf<Pair<String, Long>, Int>()

        events.sortedBy { it.timestampMs }.forEach { event ->
            val state = sessionsByPackage.getOrPut(event.packageName) { PackageSessions() }
            when (event.eventType) {
                EVENT_ACTIVITY_RESUMED -> {
                    state.sessions += Session(startMs = event.timestampMs)
                    if (event.timestampMs >= fromMs && event.timestampMs < toMsExclusive) {
                        val day = event.timestampMs / DAY_MS
                        val key = event.packageName to day
                        launchesByKey[key] = (launchesByKey[key] ?: 0) + 1
                    }
                }
                EVENT_ACTIVITY_PAUSED, EVENT_ACTIVITY_STOPPED -> {
                    val open = state.sessions.lastOrNull { it.endMs == null } ?: return@forEach
                    open.endMs = maxOf(event.timestampMs, open.startMs)
                }
            }
        }

        sessionsByPackage.values.forEach { pkg ->
            pkg.sessions.forEach { session ->
                if (session.endMs == null) {
                    session.endMs = maxOf(nowMs, session.startMs)
                }
            }
        }

        val totalsByKey = mutableMapOf<Pair<String, Long>, Long>()
        sessionsByPackage.forEach { (packageName, state) ->
            state.sessions.forEach { session ->
                val endMs = session.endMs ?: return@forEach
                var clampedStart = maxOf(session.startMs, fromMs)
                val clampedEnd = minOf(endMs, toMsExclusive)
                if (clampedEnd <= clampedStart) return@forEach

                while (clampedStart < clampedEnd) {
                    val day = clampedStart / DAY_MS
                    val dayEnd = minOf((day + 1L) * DAY_MS, clampedEnd)
                    val key = packageName to day
                    totalsByKey[key] = (totalsByKey[key] ?: 0L) + (dayEnd - clampedStart)
                    clampedStart = dayEnd
                }
            }
        }

        return totalsByKey.entries
            .map { (key, totalMs) ->
                DailyAppUsage(
                    packageName = key.first,
                    epochDay = key.second,
                    totalMs = totalMs,
                    launches = launchesByKey[key] ?: 0
                )
            }
            .filter { it.totalMs > 0L }
            .sortedWith(compareByDescending<DailyAppUsage> { it.epochDay }.thenByDescending { it.totalMs })
    }
}
