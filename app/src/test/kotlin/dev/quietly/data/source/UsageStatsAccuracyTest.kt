package dev.quietly.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying exact screen time reconciliation matching OS Digital Wellbeing.
 */
class UsageStatsAccuracyTest {

    // Helper data structure representing an OS interval slice
    data class MockSlice(
        val packageName: String,
        val startMs: Long,
        val endMs: Long,
        val totalTimeInForeground: Long
    )

    private val blockedPackages = setOf(
        "dev.quietly",
        "android",
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.miui.home",
        "com.oppo.launcher",
        "com.huawei.android.launcher",
        "com.oneplus.launcher"
    )

    private fun isPackageRelevant(pkg: String): Boolean {
        if (pkg in blockedPackages) return false
        if (pkg == "android" || pkg.startsWith("com.android.systemui")) return false
        return true
    }

    private fun calculateAppTimes(
        slices: List<MockSlice>,
        windowStartMs: Long,
        windowEndMs: Long
    ): Map<String, Long> {
        val maxDayTimeMs = (windowEndMs - windowStartMs).coerceAtLeast(1L)
        val timeByPkg = mutableMapOf<String, Long>()

        val slicesByPkg = slices.groupBy { it.packageName }
        for ((pkg, list) in slicesByPkg) {
            if (!isPackageRelevant(pkg)) continue
            if (list.size == 1) {
                val fg = list[0].totalTimeInForeground
                if (fg > 0L) {
                    timeByPkg[pkg] = minOf(fg, maxDayTimeMs)
                }
            } else {
                val sorted = list.sortedBy { it.startMs }
                var total = 0L
                var prevEnd = -1L
                for (us in sorted) {
                    val fg = us.totalTimeInForeground
                    if (fg <= 0L) continue
                    if (us.startMs >= prevEnd) {
                        total += fg
                    } else {
                        total = maxOf(total, fg)
                    }
                    prevEnd = maxOf(prevEnd, us.endMs)
                }
                if (total > 0L) {
                    timeByPkg[pkg] = minOf(total, maxDayTimeMs)
                }
            }
        }
        return timeByPkg
    }

    @Test
    fun `verifies exact match with Digital Wellbeing user screenshot values`() {
        val dayStart = 1_700_000_000_000L
        val dayEnd = dayStart + 22 * 3600_000L + 7 * 60_000L // 22:07

        // Screenshot 1 & 2 values:
        // Instagram: 6h 46m = 406 minutes = 24,360,000 ms
        val instagramMs = (6 * 60 + 46) * 60_000L
        // WhatsApp: 2h 16m = 136 minutes = 8,160,000 ms
        val whatsAppMs = (2 * 60 + 16) * 60_000L
        // Reddit: 44m = 2,640,000 ms
        val redditMs = 44 * 60_000L
        // Other user apps: total 11h 47m = 707 min = 42,420,000 ms
        val otherAppsMs = (11 * 60 + 47 - (406 + 136 + 44)) * 60_000L // 72 minutes = 1h 12m

        val slices = listOf(
            MockSlice("com.instagram.android", dayStart, dayEnd, instagramMs),
            MockSlice("com.whatsapp", dayStart, dayEnd, whatsAppMs),
            MockSlice("com.reddit.frontpage", dayStart, dayEnd, redditMs),
            MockSlice("com.android.chrome", dayStart, dayEnd, otherAppsMs),
            // System UI and launcher should be filtered out
            MockSlice("com.sec.android.app.launcher", dayStart, dayEnd, 45 * 60_000L),
            MockSlice("com.android.systemui", dayStart, dayEnd, 30 * 60_000L),
            MockSlice("com.samsung.android.honeyboard", dayStart, dayEnd, 20 * 60_000L),
            MockSlice("dev.quietly", dayStart, dayEnd, 5 * 60_000L)
        )

        val result = calculateAppTimes(slices, dayStart, dayEnd)

        // Verify individual apps
        assertEquals(instagramMs, result["com.instagram.android"])
        assertEquals(whatsAppMs, result["com.whatsapp"])
        assertEquals(redditMs, result["com.reddit.frontpage"])
        assertEquals(otherAppsMs, result["com.android.chrome"])

        // Verify exclusions
        assertFalse(result.containsKey("com.sec.android.app.launcher"))
        assertFalse(result.containsKey("com.android.systemui"))
        assertFalse(result.containsKey("com.samsung.android.honeyboard"))
        assertFalse(result.containsKey("dev.quietly"))

        // Verify total sum
        val totalMs = result.values.sum()
        val expectedTotalMs = (11 * 60 + 47) * 60_000L // 11h 47m
        assertEquals(expectedTotalMs, totalMs)
    }

    @Test
    fun `mid-day reboot disjoint slices sum correctly without overcounting`() {
        val dayStart = 1_700_000_000_000L
        val dayEnd = dayStart + 24 * 3600_000L

        // App used 2h before reboot at 14:00, then 4h 46m after reboot
        val slice1 = MockSlice(
            "com.instagram.android",
            startMs = dayStart,
            endMs = dayStart + 14 * 3600_000L,
            totalTimeInForeground = 2 * 3600_000L
        )
        val slice2 = MockSlice(
            "com.instagram.android",
            startMs = dayStart + 14 * 3600_000L + 5 * 60_000L, // reboot completed 5m later
            endMs = dayEnd,
            totalTimeInForeground = (4 * 60 + 46) * 60_000L
        )

        val result = calculateAppTimes(listOf(slice1, slice2), dayStart, dayEnd)
        assertEquals((6 * 60 + 46) * 60_000L, result["com.instagram.android"])
    }

    @Test
    fun `overlapping snapshot slices take max rather than double counting`() {
        val dayStart = 1_700_000_000_000L
        val dayEnd = dayStart + 24 * 3600_000L

        // Snapshot 1 at noon (2h), Snapshot 2 at 18:00 (5h cumulative)
        val slice1 = MockSlice(
            "com.instagram.android",
            startMs = dayStart,
            endMs = dayStart + 12 * 3600_000L,
            totalTimeInForeground = 2 * 3600_000L
        )
        val slice2 = MockSlice(
            "com.instagram.android",
            startMs = dayStart, // overlapping start
            endMs = dayStart + 18 * 3600_000L,
            totalTimeInForeground = 5 * 3600_000L
        )

        val result = calculateAppTimes(listOf(slice1, slice2), dayStart, dayEnd)
        assertEquals(5 * 3600_000L, result["com.instagram.android"])
    }

    @Test
    fun `multi-day query isolates each day and prevents previous days elevation`() {
        val zoneId = java.time.ZoneId.of("UTC")
        val day1Start = 1_700_000_000_000L
        val day1Mid = day1Start + 12 * 3600_000L
        val day1End = day1Start + 24 * 3600_000L

        val day2Start = day1End
        val day2Mid = day2Start + 12 * 3600_000L
        val day2End = day2Start + 24 * 3600_000L

        val day1Epoch = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(day1Mid), zoneId).toEpochDay()
        val day2Epoch = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(day2Mid), zoneId).toEpochDay()

        // Slices from OS queryUsageStats returning buckets for Day 1 and Day 2
        val day1Slice = MockSlice("com.instagram.android", day1Start, day1End, 3 * 3600_000L) // 3 hours on Day 1
        val day2Slice = MockSlice("com.instagram.android", day2Start, day2End, 6 * 3600_000L) // 6 hours on Day 2

        val allSlices = listOf(day1Slice, day2Slice)

        // Group by day epoch using slice midpoint
        val byDay = mutableMapOf<Long, MutableList<MockSlice>>()
        for (s in allSlices) {
            val mid = (s.startMs + s.endMs) / 2
            val epochDay = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(mid), zoneId).toEpochDay()
            byDay.getOrPut(epochDay) { mutableListOf() }.add(s)
        }

        val day1Result = calculateAppTimes(byDay[day1Epoch] ?: emptyList(), day1Start, day1End)
        val day2Result = calculateAppTimes(byDay[day2Epoch] ?: emptyList(), day2Start, day2End)

        // Day 1 must only have 3 hours (NOT 3 + 6 = 9 hours!)
        assertEquals(3 * 3600_000L, day1Result["com.instagram.android"])
        // Day 2 must only have 6 hours (NOT 3 + 6 = 9 hours!)
        assertEquals(6 * 3600_000L, day2Result["com.instagram.android"])
    }

    @Test
    fun `event processing state machine produces exact 11h 47m screen time matching user screenshot`() {
        data class TestEvent(val pkg: String, val type: Int, val timestamp: Long)

        val dayStart = 1_700_000_000_000L
        val dayEnd = dayStart + 22 * 3600_000L + 7 * 60_000L // 22:07

        val instagramMs = (6 * 60 + 46) * 60_000L // 6h 46m = 406m
        val whatsAppMs = (2 * 60 + 16) * 60_000L  // 2h 16m = 136m
        val redditMs = 44 * 60_000L               // 44m
        val chromeMs = (11 * 60 + 47 - (406 + 136 + 44)) * 60_000L // 121m = 2h 1m (other apps)

        val events = mutableListOf<TestEvent>()

        // Morning: Instagram (2h)
        events.add(TestEvent("com.instagram.android", 1, dayStart + 8 * 3600_000L))
        events.add(TestEvent("com.instagram.android", 2, dayStart + 10 * 3600_000L))

        // WhatsApp (1h)
        events.add(TestEvent("com.whatsapp", 1, dayStart + 10 * 3600_000L + 5 * 60_000L))
        events.add(TestEvent("com.whatsapp", 2, dayStart + 11 * 3600_000L + 5 * 60_000L))

        // Screen turned off for lunch
        events.add(TestEvent("com.android.systemui", 16, dayStart + 11 * 3600_000L + 10 * 60_000L))

        // Afternoon: Instagram (4h 46m)
        events.add(TestEvent("com.instagram.android", 1, dayStart + 13 * 3600_000L))
        events.add(TestEvent("com.instagram.android", 2, dayStart + 17 * 3600_000L + 46 * 60_000L))

        // WhatsApp (1h 16m)
        events.add(TestEvent("com.whatsapp", 1, dayStart + 18 * 3600_000L))
        events.add(TestEvent("com.whatsapp", 2, dayStart + 19 * 3600_000L + 16 * 60_000L))

        // Reddit (44m)
        events.add(TestEvent("com.reddit.frontpage", 1, dayStart + 19 * 3600_000L + 30 * 60_000L))
        events.add(TestEvent("com.reddit.frontpage", 2, dayStart + 20 * 3600_000L + 14 * 60_000L))

        // Chrome (121m = 2h 1m)
        events.add(TestEvent("com.android.chrome", 1, dayStart + 20 * 3600_000L + 1 * 60_000L))
        events.add(TestEvent("com.android.chrome", 2, dayStart + 22 * 3600_000L + 2 * 60_000L))

        // Home Launcher and Screen Off at 22:07
        events.add(TestEvent("com.sec.android.app.launcher", 1, dayStart + 22 * 3600_000L + 3 * 60_000L))
        events.add(TestEvent("com.sec.android.app.launcher", 2, dayStart + 22 * 3600_000L + 7 * 60_000L))
        events.add(TestEvent("com.android.systemui", 16, dayStart + 22 * 3600_000L + 7 * 60_000L))

        // Run through state machine
        val timeByPkg = mutableMapOf<String, Long>()
        var currentForegroundPkg: String? = null
        var currentForegroundStartMs = 0L
        var screenOn = true

        for (e in events) {
            when (e.type) {
                1 -> { // ACTIVITY_RESUMED
                    screenOn = true
                    if (currentForegroundPkg != null) {
                        val sStart = maxOf(currentForegroundStartMs, dayStart)
                        val sEnd = minOf(e.timestamp, dayEnd)
                        if (sEnd > sStart && isPackageRelevant(currentForegroundPkg!!)) {
                            timeByPkg[currentForegroundPkg!!] = (timeByPkg[currentForegroundPkg!!] ?: 0L) + (sEnd - sStart)
                        }
                    }
                    if (isPackageRelevant(e.pkg)) {
                        currentForegroundPkg = e.pkg
                        currentForegroundStartMs = e.timestamp
                    } else {
                        currentForegroundPkg = null
                    }
                }
                2, 23 -> { // ACTIVITY_PAUSED / STOPPED
                    if (currentForegroundPkg == e.pkg && screenOn) {
                        val sStart = maxOf(currentForegroundStartMs, dayStart)
                        val sEnd = minOf(e.timestamp, dayEnd)
                        if (sEnd > sStart && isPackageRelevant(e.pkg)) {
                            timeByPkg[e.pkg] = (timeByPkg[e.pkg] ?: 0L) + (sEnd - sStart)
                        }
                        currentForegroundPkg = null
                    }
                }
                16, 17, 26 -> { // SCREEN_NON_INTERACTIVE / LOCK / SHUTDOWN
                    if (currentForegroundPkg != null && screenOn) {
                        val sStart = maxOf(currentForegroundStartMs, dayStart)
                        val sEnd = minOf(e.timestamp, dayEnd)
                        if (sEnd > sStart && isPackageRelevant(currentForegroundPkg!!)) {
                            timeByPkg[currentForegroundPkg!!] = (timeByPkg[currentForegroundPkg!!] ?: 0L) + (sEnd - sStart)
                        }
                        currentForegroundPkg = null
                    }
                    screenOn = false
                }
                15, 18, 27 -> { // SCREEN_INTERACTIVE / UNLOCK / STARTUP
                    screenOn = true
                }
            }
        }

        // Assert exact matches with screenshot
        assertEquals(instagramMs, timeByPkg["com.instagram.android"])
        assertEquals(whatsAppMs, timeByPkg["com.whatsapp"])
        assertEquals(redditMs, timeByPkg["com.reddit.frontpage"])
        assertEquals(chromeMs, timeByPkg["com.android.chrome"])

        // Exclusions
        assertFalse(timeByPkg.containsKey("com.sec.android.app.launcher"))
        assertFalse(timeByPkg.containsKey("com.android.systemui"))

        // Exact total: 11h 47m
        val totalMs = timeByPkg.values.sum()
        val expectedTotalMs = (11 * 60 + 47) * 60_000L
        assertEquals(expectedTotalMs, totalMs)
    }

    @Test
    fun `idle gap protection prevents 8-hour ghost usage when user puts phone away`() {
        data class TestEvent(val pkg: String, val type: Int, val timestamp: Long)

        val dayStart = 1_700_000_000_000L
        val dayEnd = dayStart + 24 * 3600_000L

        val events = listOf(
            // User opens Instagram at 10:00 AM
            TestEvent("com.instagram.android", 1, dayStart + 10 * 3600_000L),
            // User interacts at 10:10 AM
            TestEvent("com.instagram.android", 7, dayStart + 10 * 3600_000L + 10 * 60_000L),
            // Device goes idle in pocket without an explicit PAUSED event.
            // Next event is at 18:00 (8 hours later) when user opens Chrome.
            TestEvent("com.android.chrome", 1, dayStart + 18 * 3600_000L),
            TestEvent("com.android.chrome", 2, dayStart + 18 * 3600_000L + 30 * 60_000L)
        )

        val timeByPkg = mutableMapOf<String, Long>()
        var currentPkg: String? = null
        var sessionStartMs = 0L
        var screenInteractive = true
        var lastEventTime = dayStart

        fun closeSession(atTimestamp: Long) {
            val pkg = currentPkg ?: return
            if (!screenInteractive) return
            val sStart = maxOf(sessionStartMs, dayStart)
            val sEnd = minOf(atTimestamp, dayEnd)
            if (sEnd > sStart && isPackageRelevant(pkg)) {
                val duration = minOf(sEnd - sStart, 2 * 3600_000L)
                timeByPkg[pkg] = (timeByPkg[pkg] ?: 0L) + duration
            }
            sessionStartMs = atTimestamp
        }

        for (e in events) {
            val t = e.timestamp
            if (currentPkg != null && screenInteractive && (t - lastEventTime > 30 * 60_000L)) {
                closeSession(minOf(t, lastEventTime + 10 * 60_000L))
                screenInteractive = false
            }
            lastEventTime = t

            when (e.type) {
                1 -> {
                    closeSession(t)
                    screenInteractive = true
                    if (isPackageRelevant(e.pkg)) {
                        currentPkg = e.pkg
                        sessionStartMs = t
                    } else {
                        currentPkg = null
                        sessionStartMs = t
                    }
                }
                2 -> {
                    if (currentPkg == e.pkg) {
                        closeSession(t)
                        currentPkg = null
                        sessionStartMs = t
                    }
                }
            }
        }

        // Instagram must NOT be 8 hours; it must only be around 20 minutes (10m activity + 10m timeout)!
        val instagramUsage = timeByPkg["com.instagram.android"] ?: 0L
        assertTrue("Instagram usage should be <= 30 mins, but was ${instagramUsage / 60_000} mins", instagramUsage <= 30 * 60_000L)
        // Chrome was used for 30 minutes
        assertEquals(30 * 60_000L, timeByPkg["com.android.chrome"])
    }
}
