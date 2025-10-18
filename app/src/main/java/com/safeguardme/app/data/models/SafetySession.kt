// data/models/SafetySession.kt - ALIGNED VERSION
package com.safeguardme.app.data.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ✅ ALIGNED: Safety session data model with proper type safety and state management
 */
data class SafetySession(
    val id: String,
    val userId: String,
    val startTime: Long,
    val endTime: Long,
    val evidenceCount: Int,
    val locationCount: Int,
    val photoCount: Int,
    val audioCount: Int,
    val transcriptionCount: Int,
    val status: SessionStatus, // ✅ FIXED: Enum for type safety
    val evidenceIds: List<String> = emptyList(), // ✅ FIXED: Proper type parameters
    val triggerMethod: TriggerMethod? = null, // ✅ FIXED: Enum for type safety
    val emergencyContacted: Boolean = false,
    val emergencyContactedAt: Long? = null, // ✅ ADDED: When emergency was contacted
    val summary: String? = null,
    val coachSummary: String? = null,
    val coachActions: List<String> = emptyList(),
    val checkInMessages: List<String> = emptyList(),
    val nextCheckInAt: Long? = null,
    val recoveryPrompts: List<String> = emptyList(),
    val riskLevel: RiskLevel = RiskLevel.UNKNOWN,
    val riskScore: Int = 0,
    val riskFactors: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap() // ✅ FIXED: Proper type parameters
) {

    /**
     * ✅ ALIGNED: Safe duration calculations with validation
     */
    fun getDurationMs(): Long {
        return when {
            endTime <= 0 -> System.currentTimeMillis() - startTime // Session still active
            endTime < startTime -> 0L // Invalid state
            else -> endTime - startTime
        }
    }

    fun getDurationMinutes(): Long = getDurationMs() / (60 * 1000)
    fun getDurationSeconds(): Long = getDurationMs() / 1000

    fun getFormattedDuration(): String {
        val totalSeconds = getDurationSeconds()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * ✅ ALIGNED: Enhanced formatting with proper null handling
     */
    fun getFormattedStartTime(): String {
        return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(startTime))
    }

    fun getFormattedEndTime(): String {
        return if (endTime > 0) {
            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(endTime))
        } else {
            "Ongoing"
        }
    }

    /**
     * ✅ ALIGNED: Session state validation
     */
    fun validate(): SessionValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Basic validation
        if (userId.isBlank()) errors.add("User ID is required")
        if (startTime <= 0) errors.add("Start time must be positive")
        if (endTime > 0 && endTime < startTime) errors.add("End time cannot be before start time")

        // Evidence count validation
        val totalCounted = locationCount + photoCount + audioCount + transcriptionCount
        if (evidenceCount != totalCounted) {
            warnings.add("Evidence count mismatch: declared=$evidenceCount, calculated=$totalCounted")
        }

        if (evidenceIds.size != evidenceCount) {
            warnings.add("Evidence ID count mismatch: IDs=${evidenceIds.size}, count=$evidenceCount")
        }

        // Status consistency validation
        when (status) {
            SessionStatus.ACTIVE -> {
                if (endTime > 0) warnings.add("Active session has end time set")
            }
            SessionStatus.COMPLETED, SessionStatus.INTERRUPTED, SessionStatus.ERROR -> {
                if (endTime <= 0) warnings.add("Ended session missing end time")
            }

            SessionStatus.UNKNOWN -> {
                warnings.add("Unknown session status")
            }
        }

        // Emergency contact validation
        if (emergencyContacted && emergencyContactedAt == null) {
            warnings.add("Emergency contacted but no timestamp recorded")
        }

        return SessionValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * ✅ ALIGNED: Safe state transitions
     */
    fun complete(endTime: Long = System.currentTimeMillis(), summary: String? = null): SafetySession {
        return this.copy(
            status = SessionStatus.COMPLETED,
            endTime = endTime,
            summary = summary ?: this.summary,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun interrupt(reason: String, endTime: Long = System.currentTimeMillis()): SafetySession {
        return this.copy(
            status = SessionStatus.INTERRUPTED,
            endTime = endTime,
            summary = "Interrupted: $reason",
            updatedAt = System.currentTimeMillis()
        )
    }

    fun markError(errorMessage: String, endTime: Long = System.currentTimeMillis()): SafetySession {
        return this.copy(
            status = SessionStatus.ERROR,
            endTime = endTime,
            summary = "Error: $errorMessage",
            updatedAt = System.currentTimeMillis()
        )
    }

    fun markEmergencyContacted(): SafetySession {
        return this.copy(
            emergencyContacted = true,
            emergencyContactedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun addEvidence(evidenceId: String, evidenceType: EvidenceType): SafetySession {
        val newEvidenceIds = evidenceIds + evidenceId
        val newCounts = when (evidenceType) {
            EvidenceType.LOCATION -> copy(locationCount = locationCount + 1)
            EvidenceType.PHOTO -> copy(photoCount = photoCount + 1)
            EvidenceType.AUDIO -> copy(audioCount = audioCount + 1)
            EvidenceType.TRANSCRIPTION, EvidenceType.VOICE_TRIGGER -> copy(transcriptionCount = transcriptionCount + 1)
            else -> this
        }

        return newCounts.copy(
            evidenceIds = newEvidenceIds,
            evidenceCount = newEvidenceIds.size,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * ✅ ALIGNED: Clean serialization
     */
    fun toJson(): String {
        return gson.toJson(this)
    }

    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "startTime" to startTime,
            "endTime" to endTime,
            "evidenceCount" to evidenceCount,
            "locationCount" to locationCount,
            "photoCount" to photoCount,
            "audioCount" to audioCount,
            "transcriptionCount" to transcriptionCount,
            "status" to status.name,
            "evidenceIds" to evidenceIds,
            "triggerMethod" to triggerMethod?.name,
            "emergencyContacted" to emergencyContacted,
            "emergencyContactedAt" to emergencyContactedAt,
            "summary" to summary,
            "coachSummary" to coachSummary,
            "coachActions" to coachActions,
            "checkInMessages" to checkInMessages,
            "nextCheckInAt" to nextCheckInAt,
            "recoveryPrompts" to recoveryPrompts,
            "riskLevel" to riskLevel.name,
            "riskScore" to riskScore,
            "riskFactors" to riskFactors,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "metadata" to metadata
        ).filterValues { it != null } as Map<String, Any>
    }

    /**
     * ✅ ALIGNED: Convenience methods
     */
    fun isActive(): Boolean = status == SessionStatus.ACTIVE
    fun isCompleted(): Boolean = status == SessionStatus.COMPLETED
    fun hasEmergency(): Boolean = emergencyContacted
    fun hasEvidence(): Boolean = evidenceCount > 0

    fun getEvidenceSummary(): String {
        val parts = mutableListOf<String>()
        if (locationCount > 0) parts.add("$locationCount location")
        if (photoCount > 0) parts.add("$photoCount photo")
        if (audioCount > 0) parts.add("$audioCount audio")
        if (transcriptionCount > 0) parts.add("$transcriptionCount transcription")

        return if (parts.isEmpty()) "No evidence" else parts.joinToString(", ")
    }

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun fromJson(json: String): SafetySession {
            return gson.fromJson(json, SafetySession::class.java)
        }

        fun fromFirestoreMap(map: Map<String, Any>, documentId: String): SafetySession {
            return SafetySession(
                id = map["id"] as? String ?: documentId,
                userId = map["userId"] as? String ?: "",
                startTime = map["startTime"] as? Long ?: 0L,
                endTime = map["endTime"] as? Long ?: 0L,
                evidenceCount = (map["evidenceCount"] as? Long)?.toInt() ?: 0,
                locationCount = (map["locationCount"] as? Long)?.toInt() ?: 0,
                photoCount = (map["photoCount"] as? Long)?.toInt() ?: 0,
                audioCount = (map["audioCount"] as? Long)?.toInt() ?: 0,
                transcriptionCount = (map["transcriptionCount"] as? Long)?.toInt() ?: 0,
                status = (map["status"] as? String)?.let {
                    try { SessionStatus.valueOf(it) } catch (e: Exception) { SessionStatus.UNKNOWN }
                } ?: SessionStatus.UNKNOWN,
                evidenceIds = map["evidenceIds"] as? List<String> ?: emptyList(),
                triggerMethod = (map["triggerMethod"] as? String)?.let {
                    try { TriggerMethod.valueOf(it) } catch (e: Exception) { null }
                },
                emergencyContacted = map["emergencyContacted"] as? Boolean ?: false,
                emergencyContactedAt = map["emergencyContactedAt"] as? Long,
                summary = (map["summary"] as? String)?.takeIf { it.isNotBlank() },
                coachSummary = (map["coachSummary"] as? String)?.takeIf { it.isNotBlank() },
                coachActions = (map["coachActions"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                checkInMessages = (map["checkInMessages"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                nextCheckInAt = map["nextCheckInAt"] as? Long,
                recoveryPrompts = (map["recoveryPrompts"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                riskLevel = (map["riskLevel"] as? String)?.let {
                    runCatching { RiskLevel.valueOf(it) }.getOrDefault(RiskLevel.UNKNOWN)
                } ?: RiskLevel.UNKNOWN,
                riskScore = (map["riskScore"] as? Long)?.toInt() ?: 0,
                riskFactors = (map["riskFactors"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                createdAt = map["createdAt"] as? Long ?: System.currentTimeMillis(),
                updatedAt = map["updatedAt"] as? Long ?: System.currentTimeMillis(),
                metadata = map["metadata"] as? Map<String, Any> ?: emptyMap()
            )
        }

        /**
         * ✅ ALIGNED: Factory methods for different session types
         */
        fun createActiveSession(
            userId: String,
            triggerMethod: TriggerMethod,
            startTime: Long = System.currentTimeMillis()
        ): SafetySession {
            return SafetySession(
                id = generateSessionId(),
                userId = userId,
                startTime = startTime,
                endTime = 0L, // Active session
                evidenceCount = 0,
                locationCount = 0,
                photoCount = 0,
                audioCount = 0,
                transcriptionCount = 0,
                status = SessionStatus.ACTIVE,
                triggerMethod = triggerMethod,
                metadata = mapOf(
                    "triggerTimestamp" to startTime,
                    "deviceInfo" to getDeviceInfo()
                )
            )
        }

        fun createEmergencySession(
            userId: String,
            triggerMethod: TriggerMethod,
            startTime: Long = System.currentTimeMillis()
        ): SafetySession {
            return createActiveSession(userId, triggerMethod, startTime).copy(
                emergencyContacted = true,
                emergencyContactedAt = startTime,
                metadata = mapOf(
                    "triggerTimestamp" to startTime,
                    "emergencyLevel" to "HIGH",
                    "autoEscalated" to true,
                    "deviceInfo" to getDeviceInfo()
                )
            )
        }

        private fun generateSessionId(): String {
            return "session_${System.currentTimeMillis()}_${UUID.randomUUID().toString().takeLast(8)}"
        }

        private fun getDeviceInfo(): Map<String, String> {
            return mapOf(
                "manufacturer" to android.os.Build.MANUFACTURER,
                "model" to android.os.Build.MODEL,
                "osVersion" to android.os.Build.VERSION.RELEASE,
                "apiLevel" to android.os.Build.VERSION.SDK_INT.toString()
            )
        }
    }
}

/**
 * ✅ ALIGNED: Type-safe session status enum
 */
enum class SessionStatus {
    ACTIVE,      // Session currently running
    COMPLETED,   // Session ended normally
    INTERRUPTED, // Session ended due to interruption
    ERROR,       // Session ended due to error
    UNKNOWN      // Unknown state (for backward compatibility)
}

/**
 * Risk severity levels used for adaptive alerts and recovery guidance.
 */
enum class RiskLevel {
    UNKNOWN,
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}

/**
 * Summary returned by the AI safety coach.
 */
data class SafetyCoachPlan(
    val summary: String,
    val immediateActions: List<String>,
    val followUpMessages: List<String>,
    val nextCheckInAt: Long?,
    val recoveryPrompts: List<String>,
    val riskLevel: RiskLevel = RiskLevel.UNKNOWN,
    val riskScore: Int = 0,
    val riskFactors: List<String> = emptyList()
)

/**
 * Lightweight risk snapshot used in session timeline analytics.
 */
data class RiskAssessment(
    val timestamp: Long,
    val source: String,
    val level: RiskLevel,
    val score: Int,
    val factors: List<String> = emptyList(),
    val recommendedAction: String? = null
)




/**
 * ✅ ALIGNED: Session validation result
 */
data class SessionValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    fun hasErrors(): Boolean = errors.isNotEmpty()
    fun hasWarnings(): Boolean = warnings.isNotEmpty()
    fun getErrorMessage(): String = errors.joinToString("; ")
    fun getWarningMessage(): String = warnings.joinToString("; ")
}

/**
 * ✅ ALIGNED: Session statistics for reporting
 */
data class SessionStatistics(
    val totalSessions: Int,
    val activeSessions: Int,
    val completedSessions: Int,
    val emergencySessions: Int,
    val averageDurationMinutes: Double,
    val totalEvidenceCollected: Int,
    val lastSessionTime: Long?
) {
    fun getCompletionRate(): Double {
        return if (totalSessions > 0) {
            completedSessions.toDouble() / totalSessions.toDouble()
        } else 0.0
    }

    fun getEmergencyRate(): Double {
        return if (totalSessions > 0) {
            emergencySessions.toDouble() / totalSessions.toDouble()
        } else 0.0
    }
}
