package dev.quietly.domain.ml

import dev.quietly.data.db.entity.CalibrationSampleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeAdaptiveModelTest {

    @Test
    fun defaultWeights_hasDefaults() {
        val weights = ScreenTimeAdaptiveModel.train(emptyList())
        assertEquals(0, weights.sampleCount)
        assertEquals(0f, weights.confidence, 0.001f)
        assertEquals(0.85, weights.passiveGhostRetentionRate, 0.01)
        assertEquals(0.98, weights.activeSessionRetentionRate, 0.01)
    }

    @Test
    fun singleSampleUnderestimation_anchorsAndCorrectsUpwards() {
        // System measured 2 hours (7_200_000 ms), but user says they actually spent 3 hours (10_800_000 ms)
        val sample = CalibrationSampleEntity(
            epochDay = 20000,
            packageName = null,
            rawDurationMs = 2 * 3600_000L,
            userActualDurationMs = 3 * 3600_000L,
            timestamp = System.currentTimeMillis()
        )
        val weights = ScreenTimeAdaptiveModel.train(listOf(sample))
        // The calibrated time for that day should return the user's exact anchored ground-truth
        val predicted = ScreenTimeAdaptiveModel.predictCalibratedDuration(
            rawMs = 2 * 3600_000L,
            epochDay = 20000,
            telemetry = null,
            weights = weights
        )
        assertEquals(3 * 3600_000L, predicted)

        // For a new, unanchored day, active retention should increase above 1.0 to compensate
        assertTrue("Active retention rate should increase when user actual > system raw", weights.activeSessionRetentionRate > 1.0)
    }

    @Test
    fun singleSampleOverestimation_anchorsAndPrunes() {
        // System measured 4 hours (14_400_000 ms), user actual was 2 hours (7_200_000 ms)
        val sample = CalibrationSampleEntity(
            epochDay = 20000,
            packageName = null,
            rawDurationMs = 4 * 3600_000L,
            userActualDurationMs = 2 * 3600_000L,
            timestamp = System.currentTimeMillis()
        )
        val weights = ScreenTimeAdaptiveModel.train(listOf(sample))
        // The calibrated time for that day should return the user's exact anchored ground-truth
        val predicted = ScreenTimeAdaptiveModel.predictCalibratedDuration(
            rawMs = 4 * 3600_000L,
            epochDay = 20000,
            telemetry = null,
            weights = weights
        )
        assertEquals(2 * 3600_000L, predicted)

        // For unanchored days, passive retention rate drops to prune idle time
        assertTrue("Passive retention should prune downwards", weights.passiveGhostRetentionRate < 0.85)
    }

    @Test
    fun calibrateAppUsages_preservesProportions() {
        val apps = listOf(
            "com.instagram.android" to 2 * 3600_000L,
            "com.youtube.android" to 1 * 3600_000L
        )
        val sample = CalibrationSampleEntity(
            epochDay = 20000,
            packageName = null,
            rawDurationMs = 3 * 3600_000L,
            userActualDurationMs = (1.5 * 3600_000L).toLong(),
            timestamp = System.currentTimeMillis()
        )
        val weights = ScreenTimeAdaptiveModel.train(listOf(sample))
        val calibrated = ScreenTimeAdaptiveModel.calibrateAppUsages(
            rawApps = apps,
            epochDay = 20000,
            telemetry = null,
            weights = weights
        )

        val insta = calibrated["com.instagram.android"] ?: 0L
        val youtube = calibrated["com.youtube.android"] ?: 0L

        assertTrue("Instagram time should be scaled down", insta < 2 * 3600_000L)
        assertTrue("YouTube time should be scaled down", youtube < 1 * 3600_000L)
        // Instagram should remain roughly 2x YouTube
        val ratio = insta.toDouble() / youtube.toDouble()
        assertEquals(2.0, ratio, 0.1)
    }

    @Test
    fun telemetryAwareModel_differentiatesActiveAndPassiveDays() {
        // Day 1 (yesterday): 7 hours, mostly active pickups and interactions
        val day1 = 20001
        val sample1 = CalibrationSampleEntity(
            epochDay = day1,
            packageName = null,
            rawDurationMs = 7 * 3600_000L,
            userActualDurationMs = 7 * 3600_000L, // Accurate
            timestamp = System.currentTimeMillis()
        )
        val telemetry1 = DayTelemetry(
            epochDay = day1,
            rawTotalMs = 7 * 3600_000L,
            passiveSessionTimeMs = 30 * 60_000L, // Only 30 mins passive
            mediaTimeMs = 60 * 60_000L,
            sessionCount = 45,
            totalLaunches = 60,
            longestSessionMs = 45 * 60_000L
        )

        // Day 2 (today): 12 hours raw, but 5 hours were passive screen-left-on/video idle
        val day2 = 20002
        val sample2 = CalibrationSampleEntity(
            epochDay = day2,
            packageName = null,
            rawDurationMs = 12 * 3600_000L,
            userActualDurationMs = 8 * 3600_000L, // User says actual was 8h
            timestamp = System.currentTimeMillis()
        )
        val telemetry2 = DayTelemetry(
            epochDay = day2,
            rawTotalMs = 12 * 3600_000L,
            passiveSessionTimeMs = 5 * 3600_000L, // 5 hours passive
            mediaTimeMs = 4 * 3600_000L,
            sessionCount = 20,
            totalLaunches = 25,
            longestSessionMs = 180 * 60_000L
        )

        val telemetries = mapOf(day1 to telemetry1, day2 to telemetry2)
        val weights = ScreenTimeAdaptiveModel.train(listOf(sample1, sample2), telemetries)

        // High confidence and distinct days learned
        assertTrue("Days learned should be 2", weights.daysLearnedCount == 2)
        // Passive ghost leakage should be pruned
        assertTrue("Passive retention should prune idle time", weights.passiveGhostRetentionRate < 0.85)
        // Active session fidelity should be maintained high
        assertTrue("Active retention rate should be high", weights.activeSessionRetentionRate >= 0.85)

        // Calibrate day 1: active day should retain ~7 hours
        val calDay1 = ScreenTimeAdaptiveModel.predictCalibratedDuration(
            rawMs = 7 * 3600_000L,
            epochDay = day1,
            telemetry = telemetry1,
            weights = weights
        )
        assertEquals(7 * 3600_000L, calDay1) // Exact anchor match

        // Calibrate day 2: 12h day with ground truth 8h should return 8h
        val calDay2 = ScreenTimeAdaptiveModel.predictCalibratedDuration(
            rawMs = 12 * 3600_000L,
            epochDay = day2,
            telemetry = telemetry2,
            weights = weights
        )
        assertEquals(8 * 3600_000L, calDay2) // Exact anchor match

        // Uncalibrated day 3 with 12 hours raw and 4 hours passive idle
        val telemetry3 = DayTelemetry(
            epochDay = 20003,
            rawTotalMs = 12 * 3600_000L,
            passiveSessionTimeMs = 4 * 3600_000L,
            mediaTimeMs = 3 * 3600_000L,
            sessionCount = 25,
            totalLaunches = 30,
            longestSessionMs = 120 * 60_000L
        )
        val calDay3 = ScreenTimeAdaptiveModel.predictCalibratedDuration(
            rawMs = 12 * 3600_000L,
            epochDay = 20003,
            telemetry = telemetry3,
            weights = weights
        )
        // The calibrated time should prune the passive leakage and be lower than 12h
        assertTrue("Day 3 with 4h passive should be pruned below 12h", calDay3 < 11 * 3600_000L)
        // But should NOT be squashed down to 7h just because yesterday was 7h!
        assertTrue("Day 3 should retain its individual character above 8h", calDay3 >= 8 * 3600_000L)
    }

    @Test
    fun calibrateAppUsages_whenTotalMatches_preservesExactTimes() {
        val apps = listOf(
            "com.whatsapp" to 45 * 60_000L,
            "com.youtube.android" to 2 * 3600_000L,
            "com.instagram.android" to 1 * 3600_000L
        )
        // Day where user confirms total 3h 45m matches raw 3h 45m
        val totalMs = 45 * 60_000L + 2 * 3600_000L + 1 * 3600_000L
        val sample = CalibrationSampleEntity(
            epochDay = 20005,
            packageName = null,
            rawDurationMs = totalMs,
            userActualDurationMs = totalMs,
            timestamp = System.currentTimeMillis()
        )
        val weights = ScreenTimeAdaptiveModel.train(listOf(sample))
        val calibrated = ScreenTimeAdaptiveModel.calibrateAppUsages(
            rawApps = apps,
            epochDay = 20005,
            telemetry = null,
            weights = weights
        )

        assertEquals("WhatsApp should be exactly preserved", 45 * 60_000L, calibrated["com.whatsapp"])
        assertEquals("YouTube should be exactly preserved", 2 * 3600_000L, calibrated["com.youtube.android"])
        assertEquals("Instagram should be exactly preserved", 1 * 3600_000L, calibrated["com.instagram.android"])
    }

    @Test
    fun calibrateAppUsages_withIdleGhostApp_protectsActiveApps() {
        // WhatsApp: actively opened 30 times for 30 minutes total
        // Chrome: left open on desk for 4 hours with 1 launch
        val apps = listOf(
            "com.whatsapp" to 30 * 60_000L,
            "com.android.chrome" to 4 * 3600_000L
        )
        val rawTotal = 30 * 60_000L + 4 * 3600_000L
        val actualTotal = 30 * 60_000L + 1 * 3600_000L // User actual was 1.5h (Chrome only 1h active)

        val sample = CalibrationSampleEntity(
            epochDay = 20006,
            packageName = null,
            rawDurationMs = rawTotal,
            userActualDurationMs = actualTotal,
            timestamp = System.currentTimeMillis()
        )
        val telemetry = DayTelemetry(
            epochDay = 20006,
            rawTotalMs = rawTotal,
            passiveSessionTimeMs = 3 * 3600_000L,
            sessionCount = 31,
            totalLaunches = 31,
            longestSessionMs = 200 * 60_000L,
            appTelemetries = mapOf(
                "com.whatsapp" to AppTelemetry(
                    packageName = "com.whatsapp",
                    rawMs = 30 * 60_000L,
                    launches = 30,
                    longestSessionMs = 2 * 60_000L, // 2-min sessions
                    isMediaApp = false
                ),
                "com.android.chrome" to AppTelemetry(
                    packageName = "com.android.chrome",
                    rawMs = 4 * 3600_000L,
                    launches = 1,
                    longestSessionMs = 200 * 60_000L, // 3h 20m unbroken idle session!
                    isMediaApp = false
                )
            )
        )

        val weights = ScreenTimeAdaptiveModel.train(listOf(sample), mapOf(20006 to telemetry))
        val calibrated = ScreenTimeAdaptiveModel.calibrateAppUsages(
            rawApps = apps,
            epochDay = 20006,
            telemetry = telemetry,
            weights = weights
        )

        val whatsappCalibrated = calibrated["com.whatsapp"] ?: 0L
        val chromeCalibrated = calibrated["com.android.chrome"] ?: 0L

        // WhatsApp was actively used (30 opens, 2 min avg) and should be preserved near 30 mins
        assertTrue("WhatsApp should be protected from excessive pruning", whatsappCalibrated >= 25 * 60_000L)
        // Chrome was the idle culprit and absorbed the reduction
        assertTrue("Chrome should absorb the idle pruning", chromeCalibrated < 2 * 3600_000L)
    }
}
