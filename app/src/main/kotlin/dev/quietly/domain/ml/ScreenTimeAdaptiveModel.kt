package dev.quietly.domain.ml

import dev.quietly.data.db.entity.CalibrationSampleEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Rich phone telemetry for a calendar day, extracted directly from Android UsageEvents.
 */
data class DayTelemetry(
    val epochDay: Int,
    val rawTotalMs: Long,
    val totalLaunches: Int = 0,
    val sessionCount: Int = 0,
    val longestSessionMs: Long = 0L,
    val mediaTimeMs: Long = 0L,
    val passiveSessionTimeMs: Long = 0L, // Unbroken continuous sessions > 25 mins
    val hourlyDistributionMs: List<Long> = List(24) { 0L },
    val appTelemetries: Map<String, AppTelemetry> = emptyMap()
)

data class AppTelemetry(
    val packageName: String,
    val rawMs: Long,
    val launches: Int = 0,
    val sessionCount: Int = 0,
    val longestSessionMs: Long = 0L,
    val isMediaApp: Boolean = false,
    val hourlyMs: List<Long> = emptyList()
)

/**
 * Learned parameters of the Adaptive On-Device Machine Learning Model.
 *
 * Rather than assuming all days are identical with a single flat slope, this model
 * distinguishes between high-usage days vs moderate days, active interactive checking
 * vs passive idle screen-on leakage, and media playback vs static foreground apps.
 */
data class CalibrationModelWeights(
    val dayGroundTruths: Map<Int, Long> = emptyMap(), // Exact anchored ground truths per epochDay
    val appGroundTruths: Map<String, Long> = emptyMap(), // Exact anchored ground truths per "epochDay:pkg"
    val passiveGhostRetentionRate: Double = 0.85,     // Retention factor for unbroken passive sessions (> 25 min)
    val activeSessionRetentionRate: Double = 0.98,    // Retention factor for active interactive sessions
    val mediaRetentionRate: Double = 0.96,            // Retention factor for video/audio streaming apps
    val sampleCount: Int = 0,
    val confidence: Float = 0f,
    val description: String = "Standard Android Telemetry Engine",
    val daysLearnedCount: Int = 0
)

object ScreenTimeAdaptiveModel {

    /**
     * Trains the multivariate model using user feedback samples and phone telemetry context.
     */
    fun train(
        samples: List<CalibrationSampleEntity>,
        telemetriesByDay: Map<Int, DayTelemetry> = emptyMap()
    ): CalibrationModelWeights {
        if (samples.isEmpty()) {
            return CalibrationModelWeights()
        }

        // 1. Build exact ground-truth anchor maps (protects each calibrated day individually)
        val dayAnchors = mutableMapOf<Int, Long>()
        val appAnchors = mutableMapOf<String, Long>()

        // Take latest sample per day/app
        val sortedSamples = samples.sortedBy { it.timestamp }
        for (s in sortedSamples) {
            if (s.packageName.isNullOrBlank()) {
                dayAnchors[s.epochDay] = s.userActualDurationMs
            } else {
                appAnchors["${s.epochDay}:${s.packageName}"] = s.userActualDurationMs
            }
        }

        // 2. Multi-feature parameter learning
        // We evaluate user adjustments on days with different profiles (e.g. 7h active vs 12h with passive sessions)
        var totalPassiveObserved = 0.0
        var totalPassiveExpected = 0.0
        var totalActiveObserved = 0.0
        var totalActiveExpected = 0.0
        var totalMediaObserved = 0.0
        var totalMediaExpected = 0.0

        // Prior regularization (3 virtual anchors assuming 95% active fidelity and 85% passive retention)
        totalActiveObserved += 3 * 3600_000.0
        totalActiveExpected += 3 * 3600_000.0 * 0.98

        totalPassiveObserved += 2 * 3600_000.0
        totalPassiveExpected += 2 * 3600_000.0 * 0.85

        totalMediaObserved += 2 * 3600_000.0
        totalMediaExpected += 2 * 3600_000.0 * 0.96

        val daySamples = samples.filter { it.packageName.isNullOrBlank() }

        for (sample in daySamples) {
            val raw = sample.rawDurationMs.toDouble()
            val actual = sample.userActualDurationMs.toDouble()
            if (raw <= 0.0 || actual < 0.0) continue

            val telemetry = telemetriesByDay[sample.epochDay]
            val passiveMs = (telemetry?.passiveSessionTimeMs?.toDouble() ?: (raw * 0.3)).coerceIn(0.0, raw)
            val mediaMs = (telemetry?.mediaTimeMs?.toDouble() ?: (raw * 0.2)).coerceIn(0.0, raw)
            val activeMs = (raw - passiveMs).coerceAtLeast(0.0)

            // When actual < raw, user noticed inflation. Inflation primarily lives in passive unbroken screen-on.
            val delta = actual - raw
            if (delta < 0.0) {
                // User dialed down: attribute 75% of discount to passive idle leakage, 25% to general scaling
                val passiveDiscount = min(passiveMs, abs(delta) * 0.75)
                val remainingDiscount = abs(delta) - passiveDiscount

                totalPassiveObserved += passiveMs
                totalPassiveExpected += max(0.0, passiveMs - passiveDiscount)

                totalActiveObserved += activeMs
                totalActiveExpected += max(0.0, activeMs - remainingDiscount)
            } else {
                // User dialed up: boost active sessions
                totalActiveObserved += activeMs
                totalActiveExpected += (activeMs + delta)

                totalPassiveObserved += passiveMs
                totalPassiveExpected += passiveMs
            }

            totalMediaObserved += mediaMs
            totalMediaExpected += mediaMs * (actual / max(raw, 1.0)).coerceIn(0.7, 1.3)
        }

        val passiveRate = (totalPassiveExpected / max(totalPassiveObserved, 1.0)).coerceIn(0.25, 1.25)
        val activeRate = (totalActiveExpected / max(totalActiveObserved, 1.0)).coerceIn(0.70, 1.30)
        val mediaRate = (totalMediaExpected / max(totalMediaObserved, 1.0)).coerceIn(0.60, 1.25)

        val uniqueDaysCalibrated = dayAnchors.size
        val confidence = min(1.0f, (uniqueDaysCalibrated / 4.0f) + (samples.size / 8.0f))

        val desc = when {
            dayAnchors.isEmpty() -> "Phone Telemetry Active"
            dayAnchors.size == 1 -> "1 day anchored: learning distinction between active and passive days"
            passiveRate < 0.75 -> "Model active: Prunes passive desk/pocket idle on long days; keeps active days sharp"
            else -> "Fine-tuned across ${dayAnchors.size} days: Day-aware telemetry & app-specific calibration"
        }

        return CalibrationModelWeights(
            dayGroundTruths = dayAnchors,
            appGroundTruths = appAnchors,
            passiveGhostRetentionRate = passiveRate,
            activeSessionRetentionRate = activeRate,
            mediaRetentionRate = mediaRate,
            sampleCount = samples.size,
            confidence = confidence,
            description = desc,
            daysLearnedCount = uniqueDaysCalibrated
        )
    }

    /**
     * Calibrates total screen time for a day based on its unique telemetry characteristics.
     * If the day has a direct ground-truth anchor, that exact ground-truth is returned.
     */
    fun predictCalibratedDuration(
        rawMs: Long,
        epochDay: Int,
        telemetry: DayTelemetry?,
        weights: CalibrationModelWeights,
        maxCapMs: Long = 86_400_000L
    ): Long {
        if (rawMs <= 0L) return 0L

        // 1. Direct Anchor: User explicitly calibrated this day
        weights.dayGroundTruths[epochDay]?.let { anchor ->
            return anchor.coerceIn(0L, maxCapMs)
        }

        if (weights.sampleCount == 0) {
            return min(rawMs, maxCapMs)
        }

        // 2. Contextual Telemetry Calibration
        val passiveMs = (telemetry?.passiveSessionTimeMs ?: (rawMs * 0.25).toLong()).coerceIn(0L, rawMs)
        val mediaMs = (telemetry?.mediaTimeMs ?: 0L).coerceIn(0L, rawMs)
        val activeMs = (rawMs - passiveMs).coerceAtLeast(0L)

        val calibratedActive = activeMs * weights.activeSessionRetentionRate
        val calibratedPassive = passiveMs * weights.passiveGhostRetentionRate

        val predicted = (calibratedActive + calibratedPassive).roundToLong()
        return predicted.coerceIn(0L, maxCapMs)
    }

    /**
     * Calibrates individual apps on a day by intelligently adjusting screen-on vs screen-off times.
     * Apps with long passive sessions (e.g. phone left open) receive appropriate idle pruning,
     * while actively launched apps are preserved.
     */
    fun calibrateAppUsages(
        rawApps: List<Pair<String, Long>>,
        epochDay: Int,
        telemetry: DayTelemetry?,
        weights: CalibrationModelWeights,
        maxDayMs: Long = 86_400_000L
    ): Map<String, Long> {
        if (rawApps.isEmpty()) return emptyMap()

        val rawTotal = rawApps.sumOf { it.second }
        if (rawTotal <= 0L) return rawApps.toMap()

        // 1. Target calibrated total for this specific day
        val targetTotal = predictCalibratedDuration(rawTotal, epochDay, telemetry, weights, maxDayMs)

        // 2. Check for app-specific ground-truth anchors first
        val result = mutableMapOf<String, Long>()
        var remainingTarget = targetTotal
        var unanchoredRawSum = 0L

        val unanchoredApps = mutableListOf<Pair<String, Long>>()

        for ((pkg, duration) in rawApps) {
            val key = "$epochDay:$pkg"
            val appAnchor = weights.appGroundTruths[key]
            if (appAnchor != null) {
                val clamped = appAnchor.coerceIn(0L, maxDayMs)
                result[pkg] = clamped
                remainingTarget -= clamped
            } else {
                unanchoredApps.add(pkg to duration)
                unanchoredRawSum += duration
            }
        }

        if (unanchoredApps.isEmpty()) {
            return result
        }

        remainingTarget = remainingTarget.coerceAtLeast(0L)
        if (unanchoredRawSum <= 0L) {
            unanchoredApps.forEach { (pkg, d) -> result[pkg] = d }
            return result
        }

        // 3. When day total is already accurate (or difference is under 1 minute), preserve exact app times!
        val adjustmentNeeded = remainingTarget - unanchoredRawSum
        if (abs(adjustmentNeeded) < 60_000L) {
            for ((pkg, duration) in unanchoredApps) {
                result[pkg] = duration
            }
            return result
        }

        // 4. Intelligently attribute adjustments based on phone telemetry
        if (adjustmentNeeded < 0) {
            // Screen time was overcounted: Identify apps that caused ghost idle leakage
            val totalPruneNeeded = abs(adjustmentNeeded)
            val idleSuspectCapacities = mutableMapOf<String, Long>()

            for ((pkg, duration) in unanchoredApps) {
                val appTel = telemetry?.appTelemetries?.get(pkg)
                val isMedia = appTel?.isMediaApp ?: false
                val longestSession = appTel?.longestSessionMs ?: 0L
                val launches = appTel?.launches ?: 1

                // Apps with continuous unbroken sessions and few launches (and not video/audio) are idle leakage
                if (!isMedia && longestSession > 20 * 60_000L && launches <= 3) {
                    val potentialIdle = (longestSession - 10 * 60_000L).coerceAtLeast(0L)
                    idleSuspectCapacities[pkg] = min(potentialIdle, (duration * 0.75).toLong())
                }
            }

            val totalIdleCap = idleSuspectCapacities.values.sum()
            if (totalIdleCap > 0) {
                // Prune from idle-leaking apps first, protecting actively used apps!
                val pruneFromIdle = min(totalPruneNeeded, totalIdleCap)
                val remainingPrune = totalPruneNeeded - pruneFromIdle

                for ((pkg, rawDuration) in unanchoredApps) {
                    val idleCap = idleSuspectCapacities[pkg] ?: 0L
                    val idlePrune = if (totalIdleCap > 0) (idleCap.toDouble() / totalIdleCap * pruneFromIdle).roundToLong() else 0L
                    val residualPrune = if (remainingPrune > 0 && unanchoredRawSum > 0) {
                        (rawDuration.toDouble() / unanchoredRawSum * remainingPrune).roundToLong()
                    } else 0L

                    val finalDuration = (rawDuration - idlePrune - residualPrune).coerceAtLeast(if (rawDuration > 0) 1L else 0L)
                    result[pkg] = min(finalDuration, maxDayMs)
                }
            } else {
                // No specific idle suspect: Scale proportionally while preserving active apps with short checks (< 5 min)
                val sizableApps = unanchoredApps.filter { it.second > 5 * 60_000L }
                val sizableSum = sizableApps.sumOf { it.second }
                val poolSum = if (sizableSum > 0) sizableSum else unanchoredRawSum

                for ((pkg, rawDuration) in unanchoredApps) {
                    val isSizable = rawDuration > 5 * 60_000L || sizableSum <= 0
                    val pruneAmount = if (isSizable) {
                        (rawDuration.toDouble() / poolSum * totalPruneNeeded).roundToLong()
                    } else 0L
                    val finalDuration = (rawDuration - pruneAmount).coerceAtLeast(if (rawDuration > 0) 1L else 0L)
                    result[pkg] = min(finalDuration, maxDayMs)
                }
            }
        } else {
            // Screen time was undercounted: Attribute addition to apps with active launches
            val boostNeeded = adjustmentNeeded
            val totalLaunches = unanchoredApps.sumOf { (pkg, _) ->
                telemetry?.appTelemetries?.get(pkg)?.launches ?: 1
            }.coerceAtLeast(1)

            for ((pkg, rawDuration) in unanchoredApps) {
                val appLaunches = telemetry?.appTelemetries?.get(pkg)?.launches ?: 1
                val boostAmount = (appLaunches.toDouble() / totalLaunches * boostNeeded).roundToLong()
                result[pkg] = min(rawDuration + boostAmount, maxDayMs)
            }
        }

        return result
    }
}
