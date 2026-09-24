package dev.quietly.domain.intelligence

import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class HabitIntelligenceEngineTest {

    @Test
    fun `predicts goal breach when app usage velocity exceeds pace`() {
        val todayUsage = listOf(
            AppUsageEntity("com.social.media", 100, "Social Feed", 2_400_000L, 15, "Social"),
            AppUsageEntity("com.work.chat", 100, "Work Chat", 600_000L, 5, "Productivity")
        )
        val goals = listOf(
            GoalEntity("com.social.media", "Social Feed", 3_000_000L, reminderEnabled = true) // 50m limit, 40m used
        )

        // At 14:00 (2 PM), 40 mins used out of 50 min limit -> should predict breach
        val report = HabitIntelligenceEngine.analyze(
            todayUsage = todayUsage,
            history90Days = emptyList(),
            goals = goals,
            nowTime = LocalTime.of(14, 0)
        )

        assertTrue(report.isReady)
        val goalPrediction = report.goalPredictions.firstOrNull { it.packageName == "com.social.media" }
        assertNotNull(goalPrediction)
        assertTrue(goalPrediction!!.willBreach)
    }

    @Test
    fun `forecasts projected screen time within reasonable bounds`() {
        val todayUsage = listOf(
            AppUsageEntity("com.app.a", 100, "App A", 3_600_000L, 10, "General") // 1 hour at 12:00
        )
        val report = HabitIntelligenceEngine.analyze(
            todayUsage = todayUsage,
            history90Days = emptyList(),
            goals = emptyList(),
            nowTime = LocalTime.of(12, 0)
        )

        assertTrue(report.forecast.projectedEndDayMs >= report.forecast.currentMs)
        assertTrue(report.forecast.confidenceDeltaMs > 0L)
        assertNotNull(report.forecast.pacingStatus)
    }

    @Test
    fun `classifies micro-checker when launch switch frequency is high`() {
        val todayUsage = listOf(
            AppUsageEntity("com.app.a", 100, "App A", 600_000L, 30, "General"), // 10 min total, 30 launches!
            AppUsageEntity("com.app.b", 100, "App B", 300_000L, 15, "General")
        )
        val report = HabitIntelligenceEngine.analyze(
            todayUsage = todayUsage,
            history90Days = emptyList(),
            goals = emptyList(),
            nowTime = LocalTime.of(15, 0)
        )

        assertEquals(HabitIntelligenceEngine.PersonaType.MICRO_CHECKER, report.persona.type)
        assertTrue(report.persona.confidencePct >= 80)
    }

    @Test
    fun `calculates shannon attention entropy and model diagnostics accurately`() {
        val todayUsage = listOf(
            AppUsageEntity("com.app.a", 100, "App A", 1_800_000L, 5, "Productivity"),
            AppUsageEntity("com.app.b", 100, "App B", 1_800_000L, 5, "Productivity")
        )
        val history = listOf(
            AppUsageEntity("com.app.a", 99, "App A", 7_200_000L, 20, "Productivity")
        )

        val report = HabitIntelligenceEngine.analyze(
            todayUsage = todayUsage,
            history90Days = history,
            goals = emptyList(),
            nowTime = LocalTime.of(12, 0)
        )

        // Equal split between 2 apps -> max entropy for 2 apps (~1.0)
        assertTrue(report.focusMetrics.attentionEntropy > 0.90f)
        assertEquals("High-Entropy Scatter", report.focusMetrics.entropyLabel)
        assertEquals(1, report.diagnostics.trainedDaysCount)
        assertTrue(report.diagnostics.safeRunwayRemainingMs > 0L)
        assertNotNull(report.diagnostics.momentumStatus)
    }
}
