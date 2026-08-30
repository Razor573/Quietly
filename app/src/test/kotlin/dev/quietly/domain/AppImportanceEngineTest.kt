package dev.quietly.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.quietly.data.db.AppEntity
import dev.quietly.data.db.DailyUsageRecord
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppImportanceEngineTest {

    private lateinit var engine: AppImportanceEngine
    private var mockCategory: Int = ApplicationInfo.CATEGORY_UNDEFINED

    @Before
    fun setUp() {
        val dummyContext = DummyContext()
        engine = AppImportanceEngine(dummyContext)
        engine.categoryResolver = { mockCategory }
    }

    @Test
    fun `evaluate essential whitelist returns ESSENTIAL_UTILITY`() {
        val app = AppEntity("com.google.android.dialer", "Phone", 60_000L)
        val insight = engine.evaluate(app, emptyList())

        assertEquals(ImportanceClassification.ESSENTIAL_UTILITY, insight.classification)
        assertEquals(95, insight.importanceScore)
        assertEquals(5, insight.distractionScore)
        assertFalse(insight.suggestedMute)
        assertTrue(insight.recommendation.contains("Essential tool"))
    }

    @Test
    fun `evaluate telecom or banking app returns ESSENTIAL_UTILITY`() {
        val app = AppEntity("com.mybank.app", "MyBank", 120_000L)
        val insight = engine.evaluate(app, emptyList())

        assertEquals(ImportanceClassification.ESSENTIAL_UTILITY, insight.classification)
        assertEquals(95, insight.importanceScore)
        assertFalse(insight.suggestedMute)
    }

    @Test
    fun `evaluate heavy game with late night usage triggers HIGH_DISTRACTION_RISK and Bedtime Quiet Mode`() {
        mockCategory = ApplicationInfo.CATEGORY_GAME
        val pkg = "com.example.game"
        val app = AppEntity(pkg, "Awesome Game", 120 * 60 * 1000L) // 120 mins today
        val history = listOf(DailyUsageRecord(100L, pkg, 60))

        val insight = engine.evaluate(
            app = app,
            history = history,
            todayOpenCount = 5,
            lateNightMinutes = 25
        )

        assertEquals(ImportanceClassification.HIGH_DISTRACTION_RISK, insight.classification)
        assertTrue(insight.suggestedMute)
        assertTrue(insight.recommendation.contains("Bedtime Quiet Mode"))
    }

    @Test
    fun `evaluate compulsive checking loop triggers Notification Batching recommendation`() {
        mockCategory = ApplicationInfo.CATEGORY_SOCIAL
        val pkg = "com.example.social"
        val app = AppEntity(pkg, "Social Network", 120 * 60 * 1000L) // 120 mins total -> durationWeight = 23.33
        val history = listOf(DailyUsageRecord(100L, pkg, 30))

        val insight = engine.evaluate(
            app = app,
            history = history,
            todayOpenCount = 30, // 30 opens for 120 mins (4 min avg) -> wait, avg session length needs to be < 3.0 min
            lateNightMinutes = 0
        )

        val app2 = AppEntity(pkg, "Social Network", 120 * 60 * 1000L)
        val history2 = listOf(DailyUsageRecord(100L, pkg, 120))
        val insight2 = engine.evaluate(
            app = app2,
            history = history2,
            todayOpenCount = 50,
            lateNightMinutes = 0
        )

        assertEquals(ImportanceClassification.HIGH_DISTRACTION_RISK, insight2.classification)
        assertTrue(insight2.suggestedMute)
        assertTrue(insight2.recommendation.contains("Notification Batching"))
    }

    @Test
    fun `evaluate passive background app returns PASSIVE_BACKGROUND`() {
        mockCategory = ApplicationInfo.CATEGORY_MAPS
        val pkg = "com.example.maps"
        val app = AppEntity(pkg, "Maps App", 40 * 60 * 1000L)
        val history = listOf(DailyUsageRecord(100L, pkg, 40))

        val insight = engine.evaluate(app, history)

        assertEquals(ImportanceClassification.PASSIVE_BACKGROUND, insight.classification)
        assertFalse(insight.suggestedMute)
    }

    @Test
    fun `evaluate dormant app returns DORMANT_CLUTTER`() {
        mockCategory = ApplicationInfo.CATEGORY_UNDEFINED
        val pkg = "com.example.unused"
        val app = AppEntity(pkg, "Unused App", 0L)
        val history = emptyList<DailyUsageRecord>()

        val insight = engine.evaluate(app, history)

        assertEquals(ImportanceClassification.DORMANT_CLUTTER, insight.classification)
        assertTrue(insight.suggestedMute)
    }
}

private class DummyContext : android.content.ContextWrapper(null) {
    override fun getPackageName(): String = "dev.quietly"
}
