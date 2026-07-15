package dev.quietly.domain

import dev.quietly.data.db.entity.AppUsageEntity
import java.time.LocalDate

/**
 * Calculates a 0–100 importance score for each app based on 90-day behaviour,
 * category, recency, and user overrides.
 *
 * Score composition (100 pts total):
 *   30 pts — total foreground time (capped at 2 h/day average)
 *   25 pts — active days (out of 90)
 *   20 pts — session frequency (launches / active days)
 *   15 pts — recency (decays linearly over 90 days)
 *   10 pts — category base value
 *
 * Protected categories always receive a minimum score of 70 regardless of
 * actual usage, and are excluded from removal recommendations.
 */
object ImportanceEngine {

    // ── Labels ────────────────────────────────────────────────────────────────

    enum class ImportanceLabel { ESSENTIAL, USEFUL, OPTIONAL, REMOVE_CANDIDATE }

    enum class RecommendationType { REMOVE, LIMIT, PROTECT, NONE }

    // ── Protected category set ────────────────────────────────────────────────

    /**
     * Apps in these categories are considered high-importance even when rarely
     * opened (banking, authenticators, security, core communication).
     */
    private val PROTECTED_CATEGORIES = setOf(
        "Finance", "Banking", "Security", "Productivity", "Accessibility"
    )

    /**
     * Package-name prefixes that are always treated as protected regardless of
     * category metadata (e.g. authenticator apps, password managers).
     */
    private val PROTECTED_PACKAGE_PREFIXES = listOf(
        "com.google.android.gm",          // Gmail
        "com.microsoft.office.outlook",
        "com.authy",
        "com.google.android.apps.authenticator2",
        "com.microsoft.authenticator",
        "com.lastpass",
        "com.onepassword",
        "com.dashlane",
        "com.keepassdroid",
        "keepass2android",
        "com.bitwarden"
    )

    // ── Category base score ───────────────────────────────────────────────────

    private fun categoryBaseScore(category: String): Int = when (category) {
        "Finance", "Banking"       -> 10
        "Productivity"             -> 8
        "Security", "Accessibility"-> 9
        "Social"                   -> 4
        "Games"                    -> 2
        "Video", "Music"           -> 3
        "News"                     -> 4
        "Maps"                     -> 6
        else                       -> 5
    }

    // ── High-distraction categories ───────────────────────────────────────────

    private val DISTRACTION_CATEGORIES = setOf("Games", "Social", "Video")

    // ── Main scoring function ─────────────────────────────────────────────────

    data class ScoredApp(
        val packageName:  String,
        val appLabel:     String,
        val category:     String,
        val score:        Int,          // 0–100
        val label:        ImportanceLabel,
        val recommendation: RecommendationType,
        val reason:       String,
        val totalTimeMs:  Long,
        val activeDays:   Int,
        val lastSeenDaysAgo: Int,
        val isProtected:  Boolean
    )

    /**
     * Score all apps from 90-day aggregated rows.
     *
     * @param rows        List of [AppUsageEntity] already grouped/summed per package over 90 days.
     * @param perDayRows  Map of packageName -> list of daily rows (for active-day counting).
     * @param overrides   Map of packageName -> [UserOverride] from the local DB.
     * @param todayEpochDay  Reference epoch day for recency calculation.
     */
    fun score(
        rows:          List<AppUsageEntity>,
        perDayRows:    Map<String, List<AppUsageEntity>>,
        overrides:     Map<String, UserOverride> = emptyMap(),
        todayEpochDay: Int = LocalDate.now().toEpochDay().toInt()
    ): List<ScoredApp> {
        return rows.map { row ->
            val pkg      = row.packageName
            val override = overrides[pkg]

            // Active days within the 90-day window
            val dailyRows   = perDayRows[pkg] ?: emptyList()
            val activeDays  = dailyRows.size.coerceAtMost(90)

            // Recency: days since last observed session
            val lastDay       = dailyRows.maxOfOrNull { it.dateEpochDay } ?: todayEpochDay
            val daysSinceLast = (todayEpochDay - lastDay).coerceAtLeast(0)

            // Is this package protected?
            val catProtected = row.category in PROTECTED_CATEGORIES
            val pkgProtected = PROTECTED_PACKAGE_PREFIXES.any { pkg.startsWith(it) }
            val isProtected  = catProtected || pkgProtected ||
                override?.type == UserOverride.Type.ESSENTIAL

            val scored = when {
                override?.type == UserOverride.Type.ESSENTIAL ->
                    buildOverrideEssential(row, activeDays, daysSinceLast)

                override?.type == UserOverride.Type.FOCUS_DRAIN ->
                    buildOverrideFocusDrain(row, activeDays, daysSinceLast)

                override?.type == UserOverride.Type.IGNORE ->
                    buildOverrideIgnore(row, activeDays, daysSinceLast)

                isProtected ->
                    buildProtectedScore(row, activeDays, daysSinceLast)

                else ->
                    buildNormalScore(row, activeDays, daysSinceLast, todayEpochDay)
            }

            scored
        }.sortedByDescending { it.score }
    }

    // ── Score builders ────────────────────────────────────────────────────────

    private fun buildNormalScore(
        row: AppUsageEntity,
        activeDays: Int,
        daysSinceLast: Int,
        todayEpochDay: Int
    ): ScoredApp {
        // Time score: 0–30, based on average daily foreground time
        // Cap at 2 h/day average (7_200_000 ms)
        val avgDailyMs   = if (activeDays > 0) row.totalTimeMs / activeDays else 0L
        val timeScore    = ((avgDailyMs.toFloat() / 7_200_000f) * 30f).toInt().coerceIn(0, 30)

        // Active-days score: 0–25
        val dayScore     = ((activeDays.toFloat() / 90f) * 25f).toInt().coerceIn(0, 25)

        // Session frequency score: 0–20
        val avgLaunches  = if (activeDays > 0) row.launchCount.toFloat() / activeDays else 0f
        val freqScore    = ((avgLaunches / 5f) * 20f).toInt().coerceIn(0, 20)

        // Recency score: 0–15 (linear decay over 90 days)
        val recencyScore = (((90 - daysSinceLast).coerceAtLeast(0).toFloat() / 90f) * 15f)
            .toInt().coerceIn(0, 15)

        // Category base: 0–10
        val catScore     = categoryBaseScore(row.category)

        val total = (timeScore + dayScore + freqScore + recencyScore + catScore).coerceIn(0, 100)

        val label = when {
            total >= 70 -> ImportanceLabel.ESSENTIAL
            total >= 45 -> ImportanceLabel.USEFUL
            total >= 20 -> ImportanceLabel.OPTIONAL
            else        -> ImportanceLabel.REMOVE_CANDIDATE
        }

        val isDistraction = row.category in DISTRACTION_CATEGORIES && total in 35..69

        val recommendation = when {
            label == ImportanceLabel.REMOVE_CANDIDATE && daysSinceLast > 30 -> RecommendationType.REMOVE
            label == ImportanceLabel.REMOVE_CANDIDATE                       -> RecommendationType.REMOVE
            isDistraction                                                    -> RecommendationType.LIMIT
            label == ImportanceLabel.OPTIONAL && avgDailyMs > 3_600_000L   -> RecommendationType.LIMIT
            else                                                             -> RecommendationType.NONE
        }

        val reason = buildReason(
            activeDays, daysSinceLast, row.category, label,
            recommendation, avgDailyMs, isProtected = false
        )

        return ScoredApp(
            packageName       = row.packageName,
            appLabel          = row.appLabel,
            category          = row.category,
            score             = total,
            label             = label,
            recommendation    = recommendation,
            reason            = reason,
            totalTimeMs       = row.totalTimeMs,
            activeDays        = activeDays,
            lastSeenDaysAgo   = daysSinceLast,
            isProtected       = false
        )
    }

    private fun buildProtectedScore(
        row: AppUsageEntity,
        activeDays: Int,
        daysSinceLast: Int
    ): ScoredApp {
        // Protected apps get a minimum of 70; still boost if actually used
        val avgDailyMs = if (activeDays > 0) row.totalTimeMs / activeDays else 0L
        val usageBoost = ((avgDailyMs.toFloat() / 7_200_000f) * 20f).toInt().coerceIn(0, 20)
        val total      = (70 + usageBoost).coerceIn(70, 100)

        val reason = buildReason(
            activeDays, daysSinceLast, row.category,
            ImportanceLabel.ESSENTIAL, RecommendationType.PROTECT,
            avgDailyMs, isProtected = true
        )

        return ScoredApp(
            packageName     = row.packageName,
            appLabel        = row.appLabel,
            category        = row.category,
            score           = total,
            label           = ImportanceLabel.ESSENTIAL,
            recommendation  = RecommendationType.PROTECT,
            reason          = reason,
            totalTimeMs     = row.totalTimeMs,
            activeDays      = activeDays,
            lastSeenDaysAgo = daysSinceLast,
            isProtected     = true
        )
    }

    private fun buildOverrideEssential(
        row: AppUsageEntity, activeDays: Int, daysSinceLast: Int
    ): ScoredApp = ScoredApp(
        packageName     = row.packageName,
        appLabel        = row.appLabel,
        category        = row.category,
        score           = 95,
        label           = ImportanceLabel.ESSENTIAL,
        recommendation  = RecommendationType.PROTECT,
        reason          = "Manually marked as Essential.",
        totalTimeMs     = row.totalTimeMs,
        activeDays      = activeDays,
        lastSeenDaysAgo = daysSinceLast,
        isProtected     = true
    )

    private fun buildOverrideFocusDrain(
        row: AppUsageEntity, activeDays: Int, daysSinceLast: Int
    ): ScoredApp {
        val avgDailyMs = if (activeDays > 0) row.totalTimeMs / activeDays else 0L
        return ScoredApp(
            packageName     = row.packageName,
            appLabel        = row.appLabel,
            category        = row.category,
            score           = 20,
            label           = ImportanceLabel.OPTIONAL,
            recommendation  = RecommendationType.LIMIT,
            reason          = "Manually marked as Focus Drain — consider limiting usage.",
            totalTimeMs     = row.totalTimeMs,
            activeDays      = activeDays,
            lastSeenDaysAgo = daysSinceLast,
            isProtected     = false
        )
    }

    private fun buildOverrideIgnore(
        row: AppUsageEntity, activeDays: Int, daysSinceLast: Int
    ): ScoredApp = ScoredApp(
        packageName     = row.packageName,
        appLabel        = row.appLabel,
        category        = row.category,
        score           = 50,
        label           = ImportanceLabel.USEFUL,
        recommendation  = RecommendationType.NONE,
        reason          = "Excluded from suggestions by your override.",
        totalTimeMs     = row.totalTimeMs,
        activeDays      = activeDays,
        lastSeenDaysAgo = daysSinceLast,
        isProtected     = false
    )

    // ── Reason string builder ─────────────────────────────────────────────────

    private fun buildReason(
        activeDays:     Int,
        daysSinceLast:  Int,
        category:       String,
        label:          ImportanceLabel,
        recommendation: RecommendationType,
        avgDailyMs:     Long,
        isProtected:    Boolean
    ): String {
        val avgStr = formatMs(avgDailyMs)
        return when {
            isProtected && daysSinceLast > 14 ->
                "Unused for $daysSinceLast days; category: $category; protected as essential."
            isProtected ->
                "Category: $category; protected as essential — excluded from removal."
            recommendation == RecommendationType.REMOVE && activeDays == 0 ->
                "Never opened in 90 days; category: $category; safe to remove."
            recommendation == RecommendationType.REMOVE ->
                "Last opened $daysSinceLast days ago; used $activeDays day(s) in 90 days; category: $category."
            recommendation == RecommendationType.LIMIT ->
                "High daily use ($avgStr avg/day); category: $category; consider limiting instead of removing."
            label == ImportanceLabel.ESSENTIAL ->
                "Used $activeDays day(s) in 90 days; avg $avgStr/day; core app."
            label == ImportanceLabel.USEFUL ->
                "Used $activeDays day(s) in 90 days; occasional use; category: $category."
            else ->
                "Low activity — $activeDays day(s) in 90 days; category: $category."
        }
    }

    private fun formatMs(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}

/** Persisted per-app user override. */
data class UserOverride(
    val packageName: String,
    val type: Type
) {
    enum class Type { ESSENTIAL, FOCUS_DRAIN, IGNORE, EXCLUDE_SUGGESTIONS }
}
