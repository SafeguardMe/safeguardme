package com.safeguardme.app.managers

import android.util.Log
import com.safeguardme.app.data.models.RiskAssessment
import com.safeguardme.app.data.models.RiskLevel
import com.safeguardme.app.data.models.RiskSignalContext
import com.safeguardme.app.data.models.RiskSignalSource
import com.safeguardme.app.data.repositories.AIAssistantRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Scores incoming safety signals and optionally enriches them with AI feedback when available.
 */
@Singleton
class RiskAssessmentEngine @Inject constructor(
    private val aiAssistantRepository: AIAssistantRepository
) {

    suspend fun evaluate(signal: RiskSignalContext): RiskAssessment {
        val heuristicScore = heuristicScore(signal)
        var level = riskLevelForScore(heuristicScore)
        var score = heuristicScore
        var factors = defaultFactors(signal)
        var action: String? = null

        if (aiAssistantRepository.isConfigured() && heuristicScore >= AI_THRESHOLD) {
            aiAssistantRepository.assessRisk(signal)
                .onSuccess { aiResult ->
                    level = aiResult.level
                    score = aiResult.score
                    factors = aiResult.factors.ifEmpty { factors }
                    action = aiResult.recommendedAction ?: action
                }
                .onFailure { error ->
                    Log.w(TAG, "AI risk classification failed, fallback to heuristic", error)
                }
        }

        return RiskAssessment(
            timestamp = signal.timestamp,
            source = signal.source.name,
            level = level,
            score = score,
            factors = factors,
            recommendedAction = action
        )
    }

    private fun heuristicScore(signal: RiskSignalContext): Int {
        var score = when (signal.source) {
            RiskSignalSource.VOICE_TRIGGER -> 65
            RiskSignalSource.GESTURE -> 55
            RiskSignalSource.LOCATION_UPDATE -> 35
            RiskSignalSource.MANUAL_REPORT -> 70
            RiskSignalSource.NETWORK_EVENT -> 25
            RiskSignalSource.UNKNOWN -> 20
        }

        signal.shakeIntensity?.let { intensity ->
            score += (intensity * 2).roundToInt()
        }
        signal.volumeBurstCount?.let { count ->
            score += count * 5
        }
        signal.distanceFromSafeZoneMeters?.let { distance ->
            if (distance > 500) score += 10 else if (distance < 50) score -= 5
        }
        signal.batteryLevel?.let { level ->
            if (level < 15) score += 10
        }

        // Cap score between 0-100
        return score.coerceIn(0, 100)
    }

    private fun riskLevelForScore(score: Int): RiskLevel = when {
        score >= 85 -> RiskLevel.CRITICAL
        score >= 70 -> RiskLevel.HIGH
        score >= 50 -> RiskLevel.MODERATE
        score >= 25 -> RiskLevel.LOW
        else -> RiskLevel.UNKNOWN
    }

    private fun defaultFactors(signal: RiskSignalContext): List<String> {
        val factors = mutableListOf<String>()
        signal.gesture?.let { factors += "Gesture: $it" }
        signal.voiceKeyword?.let { factors += "Keyword: $it" }
        signal.shakeIntensity?.let { factors += "Shake intensity ${"%.1f".format(it)}g" }
        signal.volumeBurstCount?.let { factors += "Volume presses x$it" }
        signal.distanceFromSafeZoneMeters?.let { factors += "Safe zone distance ${it.toInt()}m" }
        signal.batteryLevel?.let { factors += "Battery $it%" }
        return if (factors.isEmpty()) listOf(signal.description) else factors
    }

    companion object {
        private const val TAG = "RiskAssessmentEngine"
        private const val AI_THRESHOLD = 60
    }
}
