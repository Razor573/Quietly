package dev.quietly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageEventAggregatorTest {
    private val day = 20_000L
    private val nextDay = day + 1L

    @Test
    fun `single session aggregates 30 minutes`() {
        val events = listOf(
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_RESUMED, day, 10),
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_PAUSED, day, 40)
        )

        val result = UsageEventAggregator.aggregate(events, day, day, nowMs = ts(day, 41))

        assertEquals(1, result.size)
        assertEquals(1_800_000L, result.first().totalMs)
    }

    @Test
    fun `multiple sessions same day aggregate correctly`() {
        val events = listOf(
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_RESUMED, day, 0),
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_PAUSED, day, 10),
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_RESUMED, day, 20),
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_PAUSED, day, 30),
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_RESUMED, day, 40),
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_PAUSED, day, 50)
        )

        val result = UsageEventAggregator.aggregate(events, day, day, nowMs = ts(day, 51))

        assertEquals(1, result.size)
        assertEquals(1_800_000L, result.first().totalMs)
        assertEquals(3, result.first().launches)
    }

    @Test
    fun `cross-midnight session splits across days`() {
        val events = listOf(
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_RESUMED, day, 23 * 60 + 50),
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_PAUSED, nextDay, 10)
        )

        val result = UsageEventAggregator.aggregate(events, day, nextDay, nowMs = ts(nextDay, 11))
        val byDay = result.associateBy { it.epochDay }

        assertEquals(2, result.size)
        assertEquals(600_000L, byDay[day]?.totalMs)
        assertEquals(600_000L, byDay[nextDay]?.totalMs)
    }

    @Test
    fun `dangling open session closes at now`() {
        val events = listOf(
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_RESUMED, day, 60)
        )

        val result = UsageEventAggregator.aggregate(events, day, day, nowMs = ts(day, 90))

        assertEquals(1, result.size)
        assertEquals(1_800_000L, result.first().totalMs)
    }

    @Test
    fun `no events returns empty result`() {
        val result = UsageEventAggregator.aggregate(emptyList(), day, day, nowMs = ts(day, 1))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `interleaved multi-app events are isolated`() {
        val events = listOf(
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_RESUMED, day, 0),
            raw("pkg.b", UsageEventAggregator.EVENT_ACTIVITY_RESUMED, day, 5),
            raw("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_PAUSED, day, 10),
            raw("pkg.b", UsageEventAggregator.EVENT_ACTIVITY_PAUSED, day, 15)
        )

        val result = UsageEventAggregator.aggregate(events, day, day, nowMs = ts(day, 20))
        val byPkg = result.associateBy { it.packageName }

        assertEquals(2, result.size)
        assertEquals(600_000L, byPkg["pkg.a"]?.totalMs)
        assertEquals(600_000L, byPkg["pkg.b"]?.totalMs)
    }

    @Test
    fun `day clamping counts only in-range portion`() {
        val events = listOf(
            RawUsageEvent("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_RESUMED, ts(day - 1, 23 * 60 + 50)),
            RawUsageEvent("pkg.a", UsageEventAggregator.EVENT_ACTIVITY_PAUSED, ts(day, 10))
        )

        val result = UsageEventAggregator.aggregate(events, day, day, nowMs = ts(day, 11))

        assertEquals(1, result.size)
        assertEquals(600_000L, result.first().totalMs)
    }

    private fun raw(packageName: String, eventType: Int, epochDay: Long, minuteOfDay: Int): RawUsageEvent =
        RawUsageEvent(packageName, eventType, ts(epochDay, minuteOfDay))

    private fun ts(epochDay: Long, minuteOfDay: Int): Long =
        epochDay * 86_400_000L + minuteOfDay * 60_000L
}
