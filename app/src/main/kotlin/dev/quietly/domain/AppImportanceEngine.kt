package dev.quietly.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quietly.data.db.AppEntity
import dev.quietly.data.db.DailyUsageRecord
import javax.inject.Inject
import javax.inject.Singleton

enum class ImportanceClassification {
    ESSENTIAL_UTILITY,       // Core communication/tools (Phone, 2FA, Maps)
    HIGH_DISTRACTION_RISK,   // Heavy habitual use, rapid opens, or late-night drain
    MODERATE_FOCUS_IMPACT,   // Noticeable use, suitable for focus hours
    PASSIVE_BACKGROUND,      // High duration, low interaction (Audio, Navigation)
    DORMANT_CLUTTER          // Unused/rarely used apps
}

data class AppInsight(
    val packageName: String,
    val appName: String,
    val importanceScore: Int, // 0 to 100 (Higher = more essential)
    val distractionScore: Int, // 0 to 100 (Higher = more distracting)
    val classification: ImportanceClassification,
    val recommendation: String,
    val suggestedMute: Boolean
)

@Singleton
class AppImportanceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pm: PackageManager by lazy { context.packageManager }

    /** Custom category resolver for testing or overrides. */
    var categoryResolver: ((String) -> Int)? = null

    private val essentialPackages = setOf(
        "com.android.dialer", "com.samsung.android.dialer", "com.google.android.dialer",
        "com.google.android.deskclock", "com.sec.android.app.clockpackage",
        "com.google.android.apps.authenticator2", "com.microsoft.authenticator"
    )

    fun evaluate(
        app: AppEntity,
        history: List<DailyUsageRecord>,
        todayOpenCount: Int = 0,
        lateNightMinutes: Int = 0
    ): AppInsight {
        val pkg = app.packageName
        val todayMinutes = (app.usageTimeMillis / (1000 * 60)).toInt()

        // 1. Essential Whitelist Check
        if (essentialPackages.contains(pkg) || isTelecomOrBankingApp(pkg)) {
            return AppInsight(
                packageName = pkg,
                appName = app.appName,
                importanceScore = 95,
                distractionScore = 5,
                classification = ImportanceClassification.ESSENTIAL_UTILITY,
                recommendation = "Essential tool. Protected from automatic muting.",
                suggestedMute = false
            )
        }

        // 2. Category Bias
        val category = getAppCategory(pkg)
        var distraction = when (category) {
            ApplicationInfo.CATEGORY_GAME -> 35.0
            ApplicationInfo.CATEGORY_SOCIAL -> 30.0
            ApplicationInfo.CATEGORY_VIDEO -> 20.0
            ApplicationInfo.CATEGORY_NEWS -> 15.0
            ApplicationInfo.CATEGORY_AUDIO -> 5.0
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> -15.0
            ApplicationInfo.CATEGORY_MAPS -> -25.0
            else -> 0.0
        }

        // 3. Historical Baseline & Trend
        val pastDays = history.filter { it.packageName == pkg }
        val avg90d = if (pastDays.isNotEmpty()) pastDays.map { it.usageMinutes }.average() else 0.0
        val recent7d = pastDays.takeLast(7)
        val avg7d = if (recent7d.isNotEmpty()) recent7d.map { it.usageMinutes }.average() else avg90d

        val trendRatio = if (avg90d > 0) (avg7d / avg90d) else 1.0

        // 4. Compulsive Check-in Intensity
        val avgSessionLength = if (todayOpenCount > 0) todayMinutes.toDouble() / todayOpenCount else 0.0
        val compulsionFactor = if (todayOpenCount > 15 && avgSessionLength < 3.0) {
            minOf(25.0, todayOpenCount * 0.75) // Frequent brief check-ins
        } else {
            0.0
        }

        // 5. Late Night Penalty (23:00 - 05:00)
        val lateNightFactor = minOf(25.0, (lateNightMinutes / 20.0) * 15.0)

        // 6. Aggregate Distraction Score (0 - 100)
        val durationWeight = minOf(35.0, (todayMinutes / 180.0) * 35.0)
        distraction += durationWeight + compulsionFactor + lateNightFactor + (if (trendRatio > 1.3) 10.0 else 0.0)
        val finalDistraction = distraction.coerceIn(0.0, 100.0).toInt()
        val finalImportance = (100 - finalDistraction).coerceIn(5, 95)

        // 7. Classification and Contextual Recommendations
        val classification: ImportanceClassification
        val recommendation: String
        val suggestedMute: Boolean

        when {
            finalDistraction >= 70 -> {
                classification = ImportanceClassification.HIGH_DISTRACTION_RISK
                suggestedMute = true
                recommendation = when {
                    lateNightMinutes >= 20 -> "Sleep disruption detected (${lateNightMinutes}m late night). Recommended for Bedtime Quiet Mode."
                    compulsionFactor >= 15 -> "Habitual checking loop detected ($todayOpenCount opens). Recommended for Notification Batching."
                    else -> "Heavy daily use (${todayMinutes}m today, ${avg90d.toInt()}m avg). Recommended for Focus Limits."
                }
            }
            category == ApplicationInfo.CATEGORY_AUDIO || category == ApplicationInfo.CATEGORY_MAPS -> {
                classification = ImportanceClassification.PASSIVE_BACKGROUND
                suggestedMute = false
                recommendation = "Passive background utility. Balanced interaction pattern."
            }
            finalDistraction >= 40 -> {
                classification = ImportanceClassification.MODERATE_FOCUS_IMPACT
                suggestedMute = false
                recommendation = "Moderate usage. Good candidate for Work/Study Quiet Hours."
            }
            todayMinutes == 0 && avg90d < 1.0 -> {
                classification = ImportanceClassification.DORMANT_CLUTTER
                suggestedMute = true
                recommendation = "Rarely opened in the past 90 days. Safe to mute notifications."
            }
            else -> {
                classification = ImportanceClassification.ESSENTIAL_UTILITY
                suggestedMute = false
                recommendation = "Healthy usage pattern."
            }
        }

        return AppInsight(
            packageName = pkg,
            appName = app.appName,
            importanceScore = finalImportance,
            distractionScore = finalDistraction,
            classification = classification,
            recommendation = recommendation,
            suggestedMute = suggestedMute
        )
    }

    private fun getAppCategory(packageName: String): Int {
        categoryResolver?.let { return it(packageName) }
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.category
        } catch (e: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
    }

    private fun isTelecomOrBankingApp(packageName: String): Boolean {
        return packageName.contains("bank") ||
               packageName.contains("finance") ||
               packageName.contains("otp") ||
               packageName.contains("auth")
    }
}
