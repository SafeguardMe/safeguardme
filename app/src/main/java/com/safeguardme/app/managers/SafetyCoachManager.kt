package com.safeguardme.app.managers

import android.util.Log
import com.safeguardme.app.data.models.RiskAssessment
import com.safeguardme.app.data.models.RiskSignalContext
import com.safeguardme.app.data.models.RiskSignalSource
import com.safeguardme.app.data.models.SafetyCoachPlan
import com.safeguardme.app.data.models.SafetyEvidence
import com.safeguardme.app.data.models.SafetySession
import com.safeguardme.app.data.repositories.AIAssistantRepository
import com.safeguardme.app.data.repositories.SafetyEvidenceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyCoachManager @Inject constructor(
    private val aiAssistantRepository: AIAssistantRepository,
    private val safetyEvidenceRepository: SafetyEvidenceRepository,
    private val riskAssessmentEngine: RiskAssessmentEngine
) {

    suspend fun generateAftercarePlan(session: SafetySession): Result<SafetyCoachOutcome> {
        return runCatching {
            val evidence = safetyEvidenceRepository
                .getEvidenceForSession(session.id)
                .getOrThrow()

            val plan = createCoachPlan(session, evidence)
            val risks = buildRiskAssessments(evidence)

            SafetyCoachOutcome(plan, risks)
        }
    }

    private suspend fun createCoachPlan(
        session: SafetySession,
        evidence: List<SafetyEvidence>
    ): SafetyCoachPlan {
        return if (aiAssistantRepository.isConfigured()) {
            aiAssistantRepository.generateSafetyCoachPlan(session, evidence)
                .onFailure {
                    Log.w(TAG, "AI coach plan failed, using fallback", it)
                }
                .getOrElse { fallbackPlan(session, evidence) }
        } else {
            fallbackPlan(session, evidence)
        }
    }

    private fun fallbackPlan(
        session: SafetySession,
        evidence: List<SafetyEvidence>
    ): SafetyCoachPlan {
        val summary = buildString {
            append("Session lasted approximately ")
            append(session.getDurationMinutes())
            append(" minutes with ")
            append(evidence.size)
            append(" evidence items collected.")
        }

        val actions = listOf(
            "Check in with a trusted contact and confirm you are safe.",
            "Review any evidence captured and secure items you wish to keep.",
            "If you feel unsafe, call 10111/112 or your emergency contact list immediately."
        )

        val followUps = listOf(
            "Would you like to schedule a wellbeing check-in later today?",
            "Do you need help contacting a support service or shelter?"
        )

        val recovery = listOf(
            "Take three slow breaths and notice something comforting around you.",
            "Write down how you are feeling right now in a private journal.",
            "Drink a glass of water and, if safe, move to a calming environment."
        )

        return SafetyCoachPlan(
            summary = summary,
            immediateActions = actions,
            followUpMessages = followUps,
            nextCheckInAt = null,
            recoveryPrompts = recovery,
            riskFactors = listOf("Fallback plan"),
            riskScore = 40
        )
    }

    private suspend fun buildRiskAssessments(
        evidence: List<SafetyEvidence>
    ): List<RiskAssessment> {
        val recentEvidence = evidence.sortedBy { it.timestamp }.takeLast(MAX_RISK_EVIDENCE)
        val assessments = mutableListOf<RiskAssessment>()

        for (item in recentEvidence) {
            val signal = toRiskSignal(item) ?: continue
            runCatching {
                val result = riskAssessmentEngine.evaluate(signal)
                assessments += result
            }.onFailure {
                Log.w(TAG, "Risk evaluation failed for ${item.id}", it)
            }
        }

        return assessments
    }

    private fun toRiskSignal(evidence: SafetyEvidence): RiskSignalContext? {
        return when (evidence.type) {
            com.safeguardme.app.data.models.EvidenceType.VOICE_TRIGGER,
            com.safeguardme.app.data.models.EvidenceType.TRANSCRIPTION -> {
                RiskSignalContext(
                    timestamp = evidence.timestamp,
                    source = RiskSignalSource.VOICE_TRIGGER,
                    description = "Voice evidence: ${evidence.keyword ?: "keyword"}",
                    voiceKeyword = evidence.keyword,
                    additionalMetadata = mapOf(
                        "confidence" to evidence.confidence,
                        "transcription" to evidence.transcription
                    )
                )
            }
            com.safeguardme.app.data.models.EvidenceType.DISTRESS_DETECTION -> {
                RiskSignalContext(
                    timestamp = evidence.timestamp,
                    source = RiskSignalSource.MANUAL_REPORT,
                    description = "Distress detection: ${evidence.distressKeywords?.joinToString()}",
                    voiceKeyword = evidence.distressKeywords?.firstOrNull(),
                    additionalMetadata = mapOf(
                        "keywords" to evidence.distressKeywords,
                        "severity" to evidence.distressLevel?.name
                    )
                )
            }
            com.safeguardme.app.data.models.EvidenceType.LOCATION -> {
                RiskSignalContext(
                    timestamp = evidence.timestamp,
                    source = RiskSignalSource.LOCATION_UPDATE,
                    description = "Location captured with accuracy ${evidence.accuracy}",
                    locationAccuracyMeters = evidence.accuracy,
                    additionalMetadata = mapOf(
                        "latitude" to evidence.latitude,
                        "longitude" to evidence.longitude
                    )
                )
            }
            com.safeguardme.app.data.models.EvidenceType.PHOTO -> {
                RiskSignalContext(
                    timestamp = evidence.timestamp,
                    source = RiskSignalSource.GESTURE,
                    description = "Photo evidence captured",
                    additionalMetadata = mapOf(
                        "fileSize" to evidence.fileSize,
                        "priority" to evidence.priority.name
                    )
                )
            }
            else -> null
        }
    }

    data class SafetyCoachOutcome(
        val plan: SafetyCoachPlan,
        val riskAssessments: List<RiskAssessment>
    )

    companion object {
        private const val TAG = "SafetyCoachManager"
        private const val MAX_RISK_EVIDENCE = 12
    }
}
