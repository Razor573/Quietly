package dev.quietly.domain

import java.util.TimeZone

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

data class RawUsageEvent(
    val packageName: String,
    val eventType: Int,
    val timestampMs: Long,
    val className: String? = null
)

data class DailyAppUsage(
    val packageName: String,
    val epochDay: Long,   // local-time epoch day (midnight in device TZ)
    val totalMs: Long,
    val launches: Int
)

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

private data class Session(val startMs: Long, var endMs: Long? = null)

private class PackageState {
    val sessions = mutableListOf<Session>()
    var launches = 0
}

// ---------------------------------------------------------------------------
// Aggregator
// ---------------------------------------------------------------------------

object UsageEventAggregator {

    // Android UsageEvents event type constants
    const val EVENT_ACTIVITY_RESUMED       = 1
    const val EVENT_ACTIVITY_PAUSED        = 2
    const val EVENT_ACTIVITY_STOPPED       = 23
    const val EVENT_SCREEN_NON_INTERACTIVE = 16
    const val EVENT_KEYGUARD_SHOWN         = 17

    /**
     * Aggregates raw usage events into per-app, per-local-day totals.
     *
     * Key fixes vs the previous implementation:
     * 1. Day bucketing uses the DEVICE's local timezone, not UTC.
     *    Without this, users east of UTC (e.g. UTC+4) see evening usage
     *    attributed to the wrong day and filtered out.
     * 2. Dangling sessions (app still open when query window ends) are closed
     *    at [nowMs] instead of being silently dropped.
     * 3. ACTIVITY_STOPPED is treated the same as ACTIVITY_PAUSED so sessions
     *    are always closed even when the paused event is missing.
     *
     * @param events       Raw events from UsageStatsManager, in any order.
     * @param fromEpochDay First local-day to include (inclusive), as local epoch day.
     * @param toEpochDay   Last local-day to include (inclusive), as local epoch day.
     * @param nowMs        Current wall-clock time; open sessions are closed here.
     */
    fun aggregate(
        events: List<RawUsageEvent>,
        fromEpochDay: Long,
        toEpochDay: Long,
        nowMs: Long = System.currentTimeMillis()
    ): List<DailyAppUsage> {
        if (events.isEmpty() || toEpochDay < fromEpochDay) return emptyList()

        val tz = TimeZone.getDefault()
        val tzOffsetMs = tz.getOffset(nowMs).toLong()

        // Convert local epoch day to absolute ms boundaries
        val dayMs = 86_400_000L
        val fromMs = fromEpochDay * dayMs - tzOffsetMs
        val toMsExclusive = (toEpochDay + 1L) * dayMs - tzOffsetMs

        // ------------------------------------------------------------------
        // 1. Build session list per package from the event stream
        // ------------------------------------------------------------------
        val sorted = events.sortedBy { it.timestampMs }
        val stateByPkg = mutableMapOf<String, PackageState>()
        val activeActivitiesByPkg = mutableMapOf<String, MutableSet<String>>()
        val pkgForegroundStart = mutableMapOf<String, Long>()

        for (e in sorted) {
            val pkg = e.packageName
            val cls = e.className ?: "default"
            val state = stateByPkg.getOrPut(pkg) { PackageState() }

            when (e.eventType) {
                EVENT_ACTIVITY_RESUMED -> {
                    val activeSet = activeActivitiesByPkg.getOrPut(pkg) { mutableSetOf() }
                    if (activeSet.isEmpty()) {
                        pkgForegroundStart[pkg] = e.timestampMs
                        state.launches++
                    }
                    activeSet.add(cls)
                }
                EVENT_ACTIVITY_PAUSED -> {
                    val activeSet = activeActivitiesByPkg[pkg]
                    if (activeSet != null) {
                        activeSet.remove(cls)
                        if (activeSet.isEmpty()) {
                            val startTime = pkgForegroundStart.remove(pkg)
                            if (startTime != null && e.timestampMs >= startTime) {
                                state.sessions.add(Session(startMs = startTime, endMs = e.timestampMs))
                            }
                        }
                    }
                }
                EVENT_ACTIVITY_STOPPED -> {
                    val activeSet = activeActivitiesByPkg[pkg]
                    if (activeSet != null && activeSet.contains(cls)) {
                        activeSet.remove(cls)
                        if (activeSet.isEmpty()) {
                            val startTime = pkgForegroundStart.remove(pkg)
                            if (startTime != null && e.timestampMs >= startTime) {
                                state.sessions.add(Session(startMs = startTime, endMs = e.timestampMs))
                            }
                        }
                    }
                }
                EVENT_SCREEN_NON_INTERACTIVE, EVENT_KEYGUARD_SHOWN -> {
                    // Screen locked / non-interactive: pause all active sessions across all packages
                    for ((activePkg, startTime) in pkgForegroundStart) {
                        if (e.timestampMs > startTime) {
                            val activeState = stateByPkg.getOrPut(activePkg) { PackageState() }
                            activeState.sessions.add(Session(startMs = startTime, endMs = e.timestampMs))
                        }
                    }
                    pkgForegroundStart.clear()
                    activeActivitiesByPkg.clear()
                }
            }
        }

        // ------------------------------------------------------------------
        // 2. Close any session still open at nowMs (app is in the foreground)
        // ------------------------------------------------------------------
        for ((pkg, startTime) in pkgForegroundStart) {
            val state = stateByPkg.getOrPut(pkg) { PackageState() }
            if (nowMs > startTime) {
                state.sessions.add(Session(startMs = startTime, endMs = nowMs))
            }
        }

        // ------------------------------------------------------------------
        // 3. Bucket session durations into local-timezone days
        // ------------------------------------------------------------------
        val totalsByKey = mutableMapOf<Pair<String, Long>, Long>()

        stateByPkg.forEach { (pkg, state) ->
            for (session in state.sessions) {
                val rawEnd = session.endMs ?: continue
                if (rawEnd <= session.startMs) continue

                // Clamp to the requested window
                val clampedStart = maxOf(session.startMs, fromMs)
                val clampedEnd   = minOf(rawEnd, toMsExclusive)
                if (clampedEnd <= clampedStart) continue

                // Walk through each calendar day covered by this session
                var cursor = clampedStart
                while (cursor < clampedEnd) {
                    // Convert cursor to a local-time epoch day
                    val localMs  = cursor + tzOffsetMs
                    val localDay = localMs / dayMs

                    // End of this calendar day in absolute ms
                    val nextDayAbsMs = (localDay + 1L) * dayMs - tzOffsetMs
                    val segEnd = minOf(nextDayAbsMs, clampedEnd)

                    val key = pkg to localDay
                    totalsByKey[key] = (totalsByKey[key] ?: 0L) + (segEnd - cursor)
                    cursor = segEnd
                }
            }
        }

        // ------------------------------------------------------------------
        // 4. Filter to requested day range and materialise results
        // ------------------------------------------------------------------
        return totalsByKey
            .filter { (key, _) -> key.second in fromEpochDay..toEpochDay }
            .map { (key, totalMs) ->
                DailyAppUsage(
                    packageName = key.first,
                    epochDay    = key.second,
                    totalMs     = totalMs,
                    launches    = stateByPkg[key.first]?.launches ?: 0
                )
            }
    }
}
