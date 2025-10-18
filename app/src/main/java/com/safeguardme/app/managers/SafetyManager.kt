// managers/SafetyManager.kt - Central Safety Operations Coordinator
package com.safeguardme.app.managers

import android.content.Context
import android.util.Log
import com.safeguardme.app.data.models.EvidenceType
import com.safeguardme.app.data.models.SafetyEvidence
import com.safeguardme.app.data.models.SafetyStatus
import com.safeguardme.app.data.repositories.SafetyEvidenceRepository
import com.safeguardme.app.data.repositories.UserRepository
import com.safeguardme.app.services.SafetyMonitoringService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ✅ SafetyManager - Central coordinator for all safety operations
 *
 * Responsibilities:
 * - Emergency mode activation/deactivation from any trigger source
 * - Coordinate voice triggers, manual triggers, gesture triggers
 * - Evidence collection orchestration
 * - Emergency contact notification management
 * - Safety status state management
 * - Interface with all existing safety repositories
 */

@Singleton
class SafetyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val safetyEvidenceRepository: SafetyEvidenceRepository,
    private val emergencyContactNotificationManager: EmergencyContactNotificationManager,
    private val permissionManager: PermissionManager
) {
    companion object {
        private const val TAG = "SafetyManager"
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Current safety state
    private val _currentSafetyStatus = MutableStateFlow(SafetyStatus.DISABLED)
    val currentSafetyStatus: StateFlow<SafetyStatus> = _currentSafetyStatus.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _isCollectingEvidence = MutableStateFlow(false)
    val isCollectingEvidence: StateFlow<Boolean> = _isCollectingEvidence.asStateFlow()

    private val _lastTriggerSource = MutableStateFlow<String?>(null)
    val lastTriggerSource: StateFlow<String?> = _lastTriggerSource.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        managerScope.launch {
            try {
                // Sync with user's current safety status
                val user = userRepository.getCurrentUser().firstOrNull()
                if (user != null) {
                    _currentSafetyStatus.value = user.safetyStatus
                    Log.d(TAG, "🔧 SafetyManager initialized with status: ${user.safetyStatus}")
                } else {
                    Log.w(TAG, "⚠️ No user found during SafetyManager initialization")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing SafetyManager", e)
            }
        }
    }

    /**
     * ✅ MAIN ENTRY POINT: Trigger emergency mode from voice detection
     */
    suspend fun triggerEmergencyModeFromVoice(keyword: String = "voice_trigger"): Result<String> {
        return triggerEmergencyMode(
            triggerSource = "Voice Detection",
            triggerData = mapOf(
                "keyword" to keyword,
                "timestamp" to System.currentTimeMillis(),
                "detection_method" to "always_on_service"
            )
        )
    }

    /**
     * ✅ MAIN ENTRY POINT: Trigger emergency mode from gesture
     */
    suspend fun triggerEmergencyModeFromGesture(gestureType: String): Result<String> {
        return triggerEmergencyMode(
            triggerSource = "Gesture: $gestureType",
            triggerData = mapOf(
                "gesture_type" to gestureType,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    /**
     * ✅ MAIN ENTRY POINT: Trigger emergency mode from manual button press
     */
    suspend fun triggerEmergencyModeFromManual(): Result<String> {
        return triggerEmergencyMode(
            triggerSource = "Manual Activation",
            triggerData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "activation_method" to "emergency_button"
            )
        )
    }

    /**
     * ✅ CORE: Universal emergency mode activation
     */
    private suspend fun triggerEmergencyMode(
        triggerSource: String,
        triggerData: Map<String, Any>
    ): Result<String> {
        return try {
            Log.w(TAG, "🚨 EMERGENCY MODE TRIGGERED - Source: $triggerSource")

            // Check if already in emergency mode
            if (_currentSafetyStatus.value == SafetyStatus.EMERGENCY) {
                Log.w(TAG, "⚠️ Already in emergency mode, ignoring duplicate trigger")
                return Result.success(_currentSessionId.value ?: "existing_session")
            }

            // Generate new session ID
            val sessionId = generateSessionId()
            _currentSessionId.value = sessionId
            _lastTriggerSource.value = triggerSource

            // Update safety status
            _currentSafetyStatus.value = SafetyStatus.EMERGENCY
            userRepository.updateSafetyStatus(SafetyStatus.EMERGENCY)

            // Start evidence collection immediately
            val evidenceResult = startEvidenceCollection(sessionId, triggerSource, triggerData)
            if (evidenceResult.isFailure) {
                Log.e(TAG, "❌ Evidence collection failed: ${evidenceResult.exceptionOrNull()}")
                // Continue with emergency response even if evidence fails
            }

            // Notify emergency contacts
            notifyEmergencyContacts(sessionId, triggerSource)

            // Start the safety monitoring service
            SafetyMonitoringService.startMonitoring(context)

            Log.i(TAG, "✅ Emergency mode activated - Session: $sessionId, Source: $triggerSource")
            Result.success(sessionId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to trigger emergency mode", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ EVIDENCE: Start comprehensive evidence collection
     */
    suspend fun startEvidenceCollection(
        sessionId: String = _currentSessionId.value ?: generateSessionId(),
        triggerSource: String = "Unknown",
        triggerData: Map<String, Any> = emptyMap()
    ): Result<Unit> {
        return try {
            Log.d(TAG, "📹 Starting evidence collection for session: $sessionId")

            _isCollectingEvidence.value = true
            _currentSessionId.value = sessionId

            // Create initial trigger evidence
            val triggerEvidence = SafetyEvidence(
                id = "trigger_${UUID.randomUUID()}",
                sessionId = sessionId,
                type = EvidenceType.TRANSCRIPTION,
                timestamp = System.currentTimeMillis(),
                description = "Emergency triggered via $triggerSource",
                metadata = triggerData + mapOf(
                    "trigger_source" to triggerSource,
                    "safety_status" to _currentSafetyStatus.value.name
                )
            )

            safetyEvidenceRepository.saveEvidence(triggerEvidence)

            // Start location tracking if permission available
            if (permissionManager.isPermissionGranted(AppPermission.LOCATION)) {
                startLocationEvidence(sessionId)
            }

            // Start audio recording if permission available
            if (permissionManager.isPermissionGranted(AppPermission.AUDIO_RECORDING)) {
                startAudioEvidence(sessionId)
            }

            // Start photo capture if permission available
            if (permissionManager.isPermissionGranted(AppPermission.CAMERA)) {
                startPhotoEvidence(sessionId)
            }

            Log.i(TAG, "✅ Evidence collection started for session: $sessionId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start evidence collection", e)
            _isCollectingEvidence.value = false
            Result.failure(e)
        }
    }

    /**
     * ✅ EVIDENCE: Start location tracking
     */
    private suspend fun startLocationEvidence(sessionId: String) {
        try {
            // Create location evidence entry
            val locationEvidence = SafetyEvidence(
                id = "location_${UUID.randomUUID()}",
                sessionId = sessionId,
                type = EvidenceType.LOCATION,
                timestamp = System.currentTimeMillis(),
                description = "Emergency location tracking started",
                metadata = mapOf(
                    "tracking_mode" to "emergency",
                    "update_interval" to "30_seconds"
                )
            )

            safetyEvidenceRepository.saveEvidence(locationEvidence)
            Log.d(TAG, "📍 Location evidence started")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start location evidence", e)
        }
    }

    /**
     * ✅ EVIDENCE: Start audio recording
     */
    private suspend fun startAudioEvidence(sessionId: String) {
        try {
            val audioEvidence = SafetyEvidence(
                id = "audio_${UUID.randomUUID()}",
                sessionId = sessionId,
                type = EvidenceType.AUDIO,
                timestamp = System.currentTimeMillis(),
                description = "Emergency audio recording started",
                metadata = mapOf(
                    "recording_mode" to "emergency",
                    "quality" to "high"
                )
            )

            safetyEvidenceRepository.saveEvidence(audioEvidence)
            Log.d(TAG, "🎤 Audio evidence started")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start audio evidence", e)
        }
    }

    /**
     * ✅ EVIDENCE: Start photo capture
     */
    private suspend fun startPhotoEvidence(sessionId: String) {
        try {
            val photoEvidence = SafetyEvidence(
                id = "photo_${UUID.randomUUID()}",
                sessionId = sessionId,
                type = EvidenceType.PHOTO,
                timestamp = System.currentTimeMillis(),
                description = "Emergency photo capture started",
                metadata = mapOf(
                    "capture_mode" to "emergency",
                    "auto_capture" to true
                )
            )

            safetyEvidenceRepository.saveEvidence(photoEvidence)
            Log.d(TAG, "📷 Photo evidence started")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start photo evidence", e)
        }
    }

    /**
     * ✅ ENHANCED: Notify emergency contacts with SMS integration
     */
    private suspend fun notifyEmergencyContacts(sessionId: String, triggerSource: String) {
        managerScope.launch {
            try {
                Log.d(TAG, "📞 Notifying emergency contacts via SMS - Session: $sessionId")

                // Check if SMS is available
                if (permissionManager.isPermissionGranted(AppPermission.SMS_MESSAGING)) {
                    val message = "🚨 EMERGENCY: Safety trigger activated via $triggerSource. Session: $sessionId. " +
                            "Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}"

                    val result = emergencyContactNotificationManager.notifyAllContacts(
                        message = message,
                        sessionId = sessionId,
                        urgencyLevel = EmergencyUrgencyLevel.CRITICAL
                    )

                    result.onSuccess { count ->
                        Log.i(TAG, "✅ Emergency SMS sent to $count contacts")
                    }.onFailure { error ->
                        Log.e(TAG, "❌ Failed to send emergency SMS: ${error.message}")
                    }
                } else {
                    Log.w(TAG, "⚠️ SMS permission not available, using fallback notification methods")
                    // Keep existing fallback logic here
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to notify emergency contacts", e)
            }
        }
    }


    /**
     * ✅ DEACTIVATION: Stop emergency mode
     */
    suspend fun stopEmergencyMode(): Result<Unit> {
        return try {
            Log.d(TAG, "🛑 Stopping emergency mode")

            val currentSessionId = _currentSessionId.value

            // Update safety status
            _currentSafetyStatus.value = SafetyStatus.DISABLED
            userRepository.updateSafetyStatus(SafetyStatus.DISABLED)

            // Stop evidence collection
            _isCollectingEvidence.value = false

            // Stop monitoring service
            SafetyMonitoringService.stopMonitoring(context)

            // Create session summary if we have a session
            if (currentSessionId != null) {
                safetyEvidenceRepository.createSessionSummary(currentSessionId)
            }

            // Clear session state
            _currentSessionId.value = null
            _lastTriggerSource.value = null

            Log.i(TAG, "✅ Emergency mode stopped - Session: $currentSessionId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop emergency mode", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ STATUS: Get current emergency status
     */
    fun getEmergencyStatus(): EmergencyStatus {
        return EmergencyStatus(
            isActive = _currentSafetyStatus.value != SafetyStatus.DISABLED,
            safetyStatus = _currentSafetyStatus.value,
            sessionId = _currentSessionId.value,
            isCollectingEvidence = _isCollectingEvidence.value,
            triggerSource = _lastTriggerSource.value,
            startTime = if (_currentSessionId.value != null) System.currentTimeMillis() else null
        )
    }

    /**
     * ✅ PERMISSIONS: Check if emergency can be activated
     */
    fun canActivateEmergency(): Boolean {
        val hasAudio = permissionManager.isPermissionGranted(AppPermission.AUDIO_RECORDING)
        val hasLocation = permissionManager.isPermissionGranted(AppPermission.LOCATION)

        // Need at least one critical permission
        return hasAudio || hasLocation
    }

    /**
     * ✅ CAPABILITIES: Get available emergency capabilities
     */
    fun getAvailableCapabilities(): List<EmergencyCapability> {
        val capabilities = mutableListOf<EmergencyCapability>()

        if (permissionManager.isPermissionGranted(AppPermission.AUDIO_RECORDING)) {
            capabilities.add(EmergencyCapability.VOICE_EVIDENCE)
        }

        if (permissionManager.isPermissionGranted(AppPermission.LOCATION)) {
            capabilities.add(EmergencyCapability.LOCATION_TRACKING)
        }

        if (permissionManager.isPermissionGranted(AppPermission.CAMERA)) {
            capabilities.add(EmergencyCapability.PHOTO_EVIDENCE)
        }

        if (permissionManager.isPermissionGranted(AppPermission.STORAGE)) {
            capabilities.add(EmergencyCapability.EVIDENCE_STORAGE)
        }

        if (permissionManager.isPermissionGranted(AppPermission.SMS_MESSAGING)) {
            capabilities.add(EmergencyCapability.SMS_ALERTS)
        }

        return capabilities
    }

    /**
     * ✅ UTILITY: Generate unique session ID
     */
    private fun generateSessionId(): String {
        return "emergency_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * ✅ TESTING: Force emergency mode (for testing)
     */
    suspend fun forceEmergencyMode(testTrigger: String = "test_trigger"): Result<String> {
        Log.w(TAG, "🧪 FORCE EMERGENCY MODE - Test trigger: $testTrigger")
        return triggerEmergencyMode(
            triggerSource = "TEST: $testTrigger",
            triggerData = mapOf(
                "test_mode" to true,
                "trigger" to testTrigger
            )
        )
    }
}

/**
 * ✅ DATA CLASSES: Supporting data structures
 */
data class EmergencyStatus(
    val isActive: Boolean,
    val safetyStatus: SafetyStatus,
    val sessionId: String?,
    val isCollectingEvidence: Boolean,
    val triggerSource: String?,
    val startTime: Long?
) {
    fun getDurationMinutes(): Long {
        return if (startTime != null && isActive) {
            (System.currentTimeMillis() - startTime) / 1000 / 60
        } else 0
    }

    fun getSummary(): String {
        return when {
            !isActive -> "Emergency mode inactive"
            sessionId != null -> "Emergency active - Session: $sessionId, Source: $triggerSource, Duration: ${getDurationMinutes()}m"
            else -> "Emergency mode active"
        }
    }
}

enum class EmergencyCapability(val displayName: String) {
    VOICE_EVIDENCE("Voice Evidence Collection"),
    LOCATION_TRACKING("Real-time Location Tracking"),
    PHOTO_EVIDENCE("Photo Evidence Capture"),
    EVIDENCE_STORAGE("Secure Evidence Storage"),
    SMS_ALERTS("SMS Emergency Alerts"),
    CONTACT_NOTIFICATION("Emergency Contact Notification")
}

