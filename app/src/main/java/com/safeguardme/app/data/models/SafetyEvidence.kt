// data/models/SafetyEvidence.kt - ALIGNED VERSION
package com.safeguardme.app.data.models

import com.google.firebase.firestore.PropertyName
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.util.UUID

/**
 * ✅ ALIGNED: Core safety evidence data model with consistent state management
 */
data class SafetyEvidence(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val type: EvidenceType,
    val timestamp: Long = System.currentTimeMillis(),

    // Location data - PRIMARY SOURCE
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,

    // File data
    val filePath: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,

    // Audio/Voice data
    val transcription: String? = null,
    val confidence: Float? = null,
    val keyword: String? = null,
    val fullText: String? = null,

    // Distress detection
    val distressKeywords: List<String>? = null,
    val distressLevel: DistressLevel? = null,

    // Status tracking - REALISTIC DEFAULTS
    val isUploaded: Boolean = false,
    val uploadTimestamp: Long? = null,
    val notes: String? = null,
    val uploadStatus: EvidenceUploadStatus = EvidenceUploadStatus.PENDING,
    val uploadedAt: Long? = null,
    val priority: EvidencePriority = EvidencePriority.NORMAL,
    val verified: Boolean = false,
    val verifiedAt: Long? = null,
    val verifiedBy: String? = null,

    // Metadata for additional context (NO DUPLICATION)
    val metadata: Map<String, Any> = emptyMap(),
    val localPath: String? = null,
    val firebaseStorageUrl: String? = null,
    val description: String
) {

    fun toJson(): String {
        return gson.toJson(this)
    }

    fun fromJson(json: String): SafetyEvidence {
        return gson.fromJson(json, SafetyEvidence::class.java)
    }



    /**
     * ✅ ALIGNED: Validation ensures data integrity
     */
    fun validate(): EvidenceValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Required field validation
        if (sessionId.isBlank()) errors.add("Session ID is required")
        if (description.isBlank()) warnings.add("Description is empty")

        // Type-specific validation
        when (type) {
            EvidenceType.LOCATION -> {
                if (latitude == null || longitude == null) {
                    errors.add("Location evidence requires coordinates")
                }
            }
            EvidenceType.PHOTO -> {
                if (filePath.isNullOrBlank()) {
                    errors.add("Photo evidence requires file path")
                }
            }
            EvidenceType.VOICE_TRIGGER -> {
                if (keyword.isNullOrBlank()) {
                    errors.add("Voice trigger evidence requires keyword")
                }
            }
            EvidenceType.AUDIO -> {
                if (filePath.isNullOrBlank()) {
                    errors.add("Audio evidence requires file path")
                }
            }
            else -> { /* Other types may have different requirements */ }
        }

        // Status consistency validation
        if (verified && verifiedAt == null) {
            warnings.add("Evidence marked verified without timestamp")
        }
        if (isUploaded && uploadedAt == null) {
            warnings.add("Evidence marked uploaded without timestamp")
        }
        if (uploadStatus == EvidenceUploadStatus.COMPLETED && !isUploaded) {
            errors.add("Upload status/flag mismatch")
        }

        return EvidenceValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * ✅ ALIGNED: Safe state transitions
     */
    fun markAsUploaded(uploadUrl: String): SafetyEvidence {
        return this.copy(
            isUploaded = true,
            uploadStatus = EvidenceUploadStatus.COMPLETED,
            uploadedAt = System.currentTimeMillis(),
            firebaseStorageUrl = uploadUrl
        )
    }

    fun markAsVerified(verifierName: String): SafetyEvidence {
        return this.copy(
            verified = true,
            verifiedAt = System.currentTimeMillis(),
            verifiedBy = verifierName
        )
    }

    fun markUploadFailed(errorMessage: String): SafetyEvidence {
        return this.copy(
            uploadStatus = EvidenceUploadStatus.FAILED,
            metadata = metadata + ("uploadError" to errorMessage)
        )
    }

    /**
     * ✅ ALIGNED: Clean Firestore mapping without duplication
     */
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "sessionId" to sessionId,
            "type" to type.name,
            "timestamp" to timestamp,
            "latitude" to latitude,
            "longitude" to longitude,
            "accuracy" to accuracy,
            "filePath" to filePath,
            "fileName" to fileName,
            "fileSize" to fileSize,
            "transcription" to transcription,
            "confidence" to confidence,
            "keyword" to keyword,
            "fullText" to fullText,
            "distressKeywords" to distressKeywords,
            "distressLevel" to distressLevel?.name,
            "isUploaded" to isUploaded,
            "uploadTimestamp" to uploadTimestamp,
            "notes" to notes,
            "uploadStatus" to uploadStatus.name,
            "uploadedAt" to uploadedAt,
            "priority" to priority.name,
            "verified" to verified,
            "verifiedAt" to verifiedAt,
            "verifiedBy" to verifiedBy,
            "metadata" to metadata,
            "localPath" to localPath,
            "firebaseStorageUrl" to firebaseStorageUrl,
            "description" to description
        ).filterValues { it != null } as Map<String, Any>
    }

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun fromFirestoreMap(map: Map<String, Any>, documentId: String): SafetyEvidence {
            return SafetyEvidence(
                id = map["id"] as? String ?: documentId,
                sessionId = map["sessionId"] as? String ?: "",
                type = EvidenceType.valueOf(map["type"] as? String ?: "SYSTEM_LOG"),
                timestamp = map["timestamp"] as? Long ?: 0L,
                latitude = map["latitude"] as? Double,
                longitude = map["longitude"] as? Double,
                accuracy = map["accuracy"] as? Float,
                filePath = map["filePath"] as? String,
                fileName = map["fileName"] as? String,
                fileSize = map["fileSize"] as? Long,
                transcription = map["transcription"] as? String,
                confidence = map["confidence"] as? Float,
                keyword = map["keyword"] as? String,
                fullText = map["fullText"] as? String,
                distressKeywords = map["distressKeywords"] as? List<String>,
                distressLevel = (map["distressLevel"] as? String)?.let { DistressLevel.valueOf(it) },
                isUploaded = map["isUploaded"] as? Boolean ?: false,
                uploadTimestamp = map["uploadTimestamp"] as? Long,
                notes = map["notes"] as? String,
                uploadStatus = (map["uploadStatus"] as? String)?.let {
                    EvidenceUploadStatus.valueOf(it)
                } ?: EvidenceUploadStatus.PENDING,
                uploadedAt = map["uploadedAt"] as? Long,
                priority = (map["priority"] as? String)?.let {
                    EvidencePriority.valueOf(it)
                } ?: EvidencePriority.NORMAL,
                verified = map["verified"] as? Boolean ?: false,
                verifiedAt = map["verifiedAt"] as? Long,
                verifiedBy = map["verifiedBy"] as? String,
                metadata = map["metadata"] as? Map<String, Any> ?: emptyMap(),
                localPath = map["localPath"] as? String,
                firebaseStorageUrl = map["firebaseStorageUrl"] as? String,
                description = map["description"] as? String ?: ""
            )
        }

        fun  fromJson(json: String): SafetyEvidence {
            return gson.fromJson(json, SafetyEvidence::class.java)
        }

        /**
         * ✅ ALIGNED: Factory methods with realistic states
         */
        fun createLocationEvidence(
            sessionId: String,
            latitude: Double,
            longitude: Double,
            accuracy: Float,
            timestamp: Long = System.currentTimeMillis(),
            address: String? = null,
            description: String = "Location captured during safety monitoring"
        ): SafetyEvidence {
            return SafetyEvidence(
                sessionId = sessionId,
                type = EvidenceType.LOCATION,
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                // ✅ FIXED: No duplication in metadata
                metadata = buildMap {
                    address?.let { put("address", it) }
                    put("mapsUrl", "https://maps.google.com/?q=$latitude,$longitude")
                    put("captureMethod", "automatic_location_tracking")
                },
                priority = EvidencePriority.HIGH,
                description = description
                // ✅ FIXED: Realistic defaults - not uploaded/verified yet
            )
        }

        fun createUserInputEvidence(
            sessionId: String,
            inputType: String,
            inputData: String,
            timestamp: Long = System.currentTimeMillis(),
            description: String = "User input during safety monitoring"
        ): SafetyEvidence {
            return SafetyEvidence(
                sessionId = sessionId,
                type = EvidenceType.USER_INPUT,
                timestamp = timestamp,
                metadata = mapOf(
                    "inputType" to inputType,
                    "inputData" to inputData
                ),
                priority = EvidencePriority.NORMAL,
                description = description

            )

        }

        fun createPhotoEvidence(
            sessionId: String,
            filePath: String,
            timestamp: Long = System.currentTimeMillis(),
            description: String = "Photo captured during safety monitoring"
        ): SafetyEvidence {
            val file = File(filePath)
            return SafetyEvidence(
                sessionId = sessionId,
                type = EvidenceType.PHOTO,
                timestamp = timestamp,
                filePath = filePath,
                fileName = file.name,
                fileSize = if (file.exists()) file.length() else null,
                metadata = mapOf(
                    "captureMethod" to "automatic_safety_monitoring",
                    "fileExists" to file.exists()
                ),
                priority = EvidencePriority.HIGH,
                description = description
                // ✅ FIXED: Realistic defaults
            )
        }

        fun createVoiceTriggerEvidence(
            sessionId: String,
            keyword: String,
            fullText: String,
            confidence: Float,
            timestamp: Long,
            description: String = "Voice trigger detected: \"$keyword\""
        ): SafetyEvidence {
            return SafetyEvidence(
                sessionId = sessionId,
                type = EvidenceType.VOICE_TRIGGER,
                timestamp = timestamp,
                keyword = keyword,
                fullText = fullText,
                confidence = confidence,
                transcription = fullText,
                metadata = mapOf(
                    "triggerMethod" to "voice_recognition",
                    "confidenceLevel" to when {
                        confidence >= 0.9f -> "high"
                        confidence >= 0.7f -> "medium"
                        else -> "low"
                    }
                ),
                priority = EvidencePriority.CRITICAL,
                description = description
                // ✅ FIXED: Realistic defaults
            )
        }

        fun createTranscriptionEvidence(
            sessionId: String,
            transcription: String,
            confidence: Float,
            timestamp: Long,
            description: String = "Transcription captured"
        ): SafetyEvidence {
            return SafetyEvidence(
                sessionId = sessionId,
                type = EvidenceType.TRANSCRIPTION,
                timestamp = timestamp,
                transcription = transcription,
                confidence = confidence,
                metadata = mapOf(
                    "confidenceLevel" to confidence,
                    "source" to "automatic_transcription"
                ),
                priority = EvidencePriority.HIGH,
                description = description
            )
        }

        fun createDistressEvidence(
            sessionId: String,
            transcription: String,
            keywords: List<String>,
            timestamp: Long,
            description: String = "Distress keywords detected: ${keywords.joinToString(", ")}"
        ): SafetyEvidence {
            val distressLevel = calculateDistressLevel(keywords)

            return SafetyEvidence(
                sessionId = sessionId,
                type = EvidenceType.DISTRESS_DETECTION,
                timestamp = timestamp,
                transcription = transcription,
                distressKeywords = keywords,
                distressLevel = distressLevel,
                confidence = 1.0f,
                metadata = mapOf(
                    "keywordCount" to keywords.size,
                    "severityAnalysis" to distressLevel.name,
                    "detectionMethod" to "keyword_analysis"
                ),
                priority = when (distressLevel) {
                    DistressLevel.HIGH -> EvidencePriority.CRITICAL
                    DistressLevel.MEDIUM -> EvidencePriority.HIGH
                    else -> EvidencePriority.NORMAL
                },
                description = description
                // ✅ FIXED: Realistic defaults
            )
        }


        fun createRiskAssessmentEvidence(
            sessionId: String,
            assessment: com.safeguardme.app.data.models.RiskAssessment,
            description: String = "Risk ${assessment.level.name} (${assessment.score}) detected by ${assessment.source}"
        ): SafetyEvidence {
            return SafetyEvidence(
                sessionId = sessionId,
                type = EvidenceType.SYSTEM_LOG,
                timestamp = assessment.timestamp,
                priority = when (assessment.level) {
                    com.safeguardme.app.data.models.RiskLevel.CRITICAL -> EvidencePriority.CRITICAL
                    com.safeguardme.app.data.models.RiskLevel.HIGH -> EvidencePriority.HIGH
                    com.safeguardme.app.data.models.RiskLevel.MODERATE -> EvidencePriority.NORMAL
                    com.safeguardme.app.data.models.RiskLevel.LOW -> EvidencePriority.NORMAL
                    com.safeguardme.app.data.models.RiskLevel.UNKNOWN -> EvidencePriority.LOW
                },
                metadata = mapOf(
                    "riskLevel" to assessment.level.name,
                    "riskScore" to assessment.score,
                    "factors" to assessment.factors,
                    "recommendedAction" to assessment.recommendedAction,
                    "source" to assessment.source
                ) as Map<String, Any>,
                description = description
            )
        }

        fun createAudioEvidence(
            sessionId: String,
            filePath: String,
            timestamp: Long = System.currentTimeMillis(),
            duration: Long? = null,
            format: String = "wav",
            description: String = "Audio recording captured"
        ): SafetyEvidence {
            return SafetyEvidence(
                sessionId = sessionId,
                type = EvidenceType.AUDIO,
                timestamp = timestamp,
                filePath = filePath,
                fileName = File(filePath).name,
                fileSize = try { File(filePath).length() } catch (e: Exception) { null },
                metadata = mapOf(
                    "duration" to (duration ?: 0L),
                    "format" to format,
                    "sampleRate" to 44100,
                    "channels" to 1,
                    "recordingMethod" to "background_continuous"
                ),
                priority = EvidencePriority.CRITICAL,
                description = description
                // ✅ FIXED: Realistic defaults
            )
        }

        private fun calculateDistressLevel(keywords: List<String>): DistressLevel {
            val highUrgencyKeywords = listOf("911", "call police", "help me", "emergency")
            val mediumUrgencyKeywords = listOf("danger", "scared", "hurt")

            return when {
                keywords.any { it.lowercase() in highUrgencyKeywords.map { k -> k.lowercase() } } -> DistressLevel.HIGH
                keywords.any { it.lowercase() in mediumUrgencyKeywords.map { k -> k.lowercase() } } -> DistressLevel.MEDIUM
                keywords.isNotEmpty() -> DistressLevel.LOW
                else -> DistressLevel.NONE
            }
        }
    }

    // ✅ ALIGNED: Consistent convenience methods
    fun isLocationEvidence(): Boolean = type == EvidenceType.LOCATION
    fun isPhotoEvidence(): Boolean = type == EvidenceType.PHOTO
    fun isVoiceEvidence(): Boolean = type == EvidenceType.VOICE_TRIGGER || type == EvidenceType.TRANSCRIPTION
    fun isDistressEvidence(): Boolean = type == EvidenceType.DISTRESS_DETECTION

    fun hasHighDistress(): Boolean = distressLevel == DistressLevel.HIGH
    fun hasAnyDistress(): Boolean = distressLevel != null && distressLevel != DistressLevel.NONE

    fun needsUpload(): Boolean = !isUploaded && uploadStatus != EvidenceUploadStatus.FAILED
    fun canBeVerified(): Boolean = isUploaded && !verified

    @PropertyName("firestore_timestamp")
    fun getFirestoreTimestamp(): Long = timestamp
}

/**
 * ✅ ALIGNED: Clear upload status enum
 */
enum class EvidenceUploadStatus {
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED,
    RETRYING
}

/**
 * ✅ ALIGNED: Enhanced validation result
 */
data class EvidenceValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    fun hasErrors(): Boolean = errors.isNotEmpty()
    fun hasWarnings(): Boolean = warnings.isNotEmpty()
    fun getErrorMessage(): String = errors.joinToString("; ")
    fun getWarningMessage(): String = warnings.joinToString("; ")
}

// Keep existing enums unchanged
enum class EvidenceType {
    LOCATION, PHOTO, AUDIO, TRANSCRIPTION, SENSOR, SYSTEM_LOG,
    USER_INPUT, DISTRESS_DETECTION, EMERGENCY_ESCALATION, VOICE_TRIGGER
}

enum class EvidencePriority {
    CRITICAL, HIGH, NORMAL, LOW
}

enum class DistressLevel {
    NONE, LOW, MEDIUM, HIGH
}
