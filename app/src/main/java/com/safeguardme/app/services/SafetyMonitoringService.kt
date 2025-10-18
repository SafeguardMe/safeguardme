// services/SafetyMonitoringService.kt - Main Background Safety Service
package com.safeguardme.app.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.safeguardme.app.MainActivity
import com.safeguardme.app.managers.SpeechRecognitionManager
import com.safeguardme.app.R
import com.safeguardme.app.data.models.SafetyEvidence
import com.safeguardme.app.data.models.SafetyStatus
import com.safeguardme.app.data.repositories.SafetyEvidenceRepository
import com.safeguardme.app.data.repositories.UserRepository
import com.safeguardme.app.managers.AudioRecordingManager
import com.safeguardme.app.managers.CameraManager
import com.safeguardme.app.managers.EmergencyContactNotificationManager
import com.safeguardme.app.managers.LocationTrackingManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SafetyMonitoringService : Service() {

    @Inject lateinit var audioRecordingManager: AudioRecordingManager
    @Inject lateinit var locationTrackingManager: LocationTrackingManager
    @Inject lateinit var cameraManager: CameraManager
    @Inject lateinit var emergencyContactNotificationManager: EmergencyContactNotificationManager
    @Inject lateinit var safetyEvidenceRepository: SafetyEvidenceRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var speechRecognitionManager: SpeechRecognitionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitoringJob: Job? = null
    private var currentSessionId: String? = null

    companion object {
        private const val TAG = "SafetyMonitoringService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "safety_monitoring_channel"

        // Monitoring intervals
        private const val LOCATION_INTERVAL_MS = 7000L // 7 seconds
        private const val PHOTO_INTERVAL_MS = 8000L // 8 seconds
        private const val TRANSCRIPTION_INTERVAL_MS = 30000L // 30 seconds

        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        const val ACTION_EMERGENCY_ESCALATION = "EMERGENCY_ESCALATION"
        const val ACTION_VOICE_TRIGGER_DETECTED = "VOICE_TRIGGER_DETECTED"

        fun startMonitoring(context: Context) {
            val intent = Intent(context, SafetyMonitoringService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            context.startForegroundService(intent)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, SafetyMonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }

        fun escalateToEmergency(context: Context) {
            val intent = Intent(context, SafetyMonitoringService::class.java).apply {
                action = ACTION_EMERGENCY_ESCALATION
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🛡️ SafetyMonitoringService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 SafetyMonitoringService command: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_MONITORING -> startSafetyMonitoring()
            ACTION_STOP_MONITORING -> stopSafetyMonitoring()
            ACTION_EMERGENCY_ESCALATION -> escalateToEmergency()
            ACTION_VOICE_TRIGGER_DETECTED -> handleVoiceTrigger(intent)
        }

        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSafetyMonitoring() {
        if (monitoringJob?.isActive == true) {
            Log.w(TAG, "⚠️ Safety monitoring already active")
            return
        }

        Log.i(TAG, "🛡️ Starting comprehensive safety monitoring")

        currentSessionId = generateSessionId()
        startForeground(NOTIFICATION_ID, createNotification("Safety Mode Active", "Monitoring your safety..."))

        monitoringJob = serviceScope.launch()  @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO) {
            try {
                // Start all monitoring components
                startAudioRecording()
                startLocationTracking()
                startPeriodicPhotoCapture()
                notifyEmergencyContactsOfActivation()

                // Keep service alive and monitor
                while (true) {
                    checkAndProcessEvidence()
                    delay(5000) // General health check every 5 seconds
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in safety monitoring", e)
                handleMonitoringError(e)
            }
        }
    }

    private fun stopSafetyMonitoring() {
        Log.i(TAG, "🛑 Stopping safety monitoring")

        monitoringJob?.cancel()

        serviceScope.launch {
            try {
                // Stop all monitoring components
                audioRecordingManager.stopRecording()
                locationTrackingManager.stopTracking()
                cameraManager.stopPeriodicCapture()

                // Final data processing
                processAndUploadFinalEvidence()
                notifyEmergencyContactsOfDeactivation()

                // Update user status
                userRepository.updateSafetyStatus(SafetyStatus.DISABLED)

                Log.i(TAG, "✅ Safety monitoring stopped successfully")
                stopForeground(true)
                stopSelf()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping safety monitoring", e)
            }
        }
    }

    private fun handleVoiceTrigger(intent: Intent) {
        val keyword = intent.getStringExtra("keyword") ?: ""
        val fullText = intent.getStringExtra("full_text") ?: ""
        val confidence = intent.getFloatExtra("confidence", 0f)

        Log.d(TAG, "🎙️ Voice trigger forwarded: keyword='$keyword' confidence=$confidence")

        // Currently just logs; integration with speech manager or evidence capture can be added here.
    }

    private fun escalateToEmergency() {
        Log.w(TAG, "🚨 ESCALATING TO EMERGENCY MODE")

        serviceScope.launch {
            try {
                // Update notification
                updateNotification("🚨 EMERGENCY MODE", "Emergency services contacted")

                // Immediate emergency actions
                emergencyContactNotificationManager.sendEmergencyAlert(currentSessionId ?: "unknown")
                captureEmergencyEvidence()

                // Update user status
                userRepository.updateSafetyStatus(SafetyStatus.EMERGENCY)

                Log.w(TAG, "🚨 Emergency escalation completed")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in emergency escalation", e)
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun startAudioRecording() {
        try {
            Log.d(TAG, "🎤 Starting background audio recording")

            val audioFile = audioRecordingManager.startContinuousRecording(currentSessionId!!)
            audioFile?.let {
                Log.i(TAG, "✅ Audio recording started: ${it.name}")

                // Start periodic transcription
                startPeriodicTranscription()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start audio recording", e)
        }
    }

    private suspend fun startLocationTracking() {
        try {
            Log.d(TAG, "📍 Starting location tracking")

            locationTrackingManager.startTracking(
                intervalMs = LOCATION_INTERVAL_MS,
                onLocationUpdate = { location ->
                    serviceScope.launch {
                        saveLocationEvidence(location)
                    }
                }
            )

            Log.i(TAG, "✅ Location tracking started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start location tracking", e)
        }
    }

    private fun startPeriodicPhotoCapture() {
        serviceScope.launch {
            try {
                Log.d(TAG, "📷 Starting periodic photo capture")

                while (monitoringJob?.isActive == true) {
                    captureAndSavePhoto()
                    delay(PHOTO_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in periodic photo capture", e)
            }
        }
    }

    private fun startPeriodicTranscription() {
        serviceScope.launch {
            try {
                Log.d(TAG, "📝 Starting periodic audio transcription")

                while (monitoringJob?.isActive == true) {
                    transcribeRecentAudio()
                    delay(TRANSCRIPTION_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in periodic transcription", e)
            }
        }
    }

    private suspend fun captureAndSavePhoto() {
        try {
            val photoFile = cameraManager.capturePhoto(currentSessionId!!)
            photoFile?.let { file ->
                val evidence = SafetyEvidence.createPhotoEvidence(
                    sessionId = currentSessionId!!,
                    filePath = file.absolutePath,
                    timestamp = System.currentTimeMillis()
                )

                safetyEvidenceRepository.saveEvidence(evidence)
                Log.d(TAG, "📷 Photo captured and saved: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to capture photo", e)
        }
    }

    private suspend fun transcribeRecentAudio() {
        try {
            val recentAudioFile = audioRecordingManager.getRecentAudioSegment(30) // Last 30 seconds
            recentAudioFile?.let { file ->

                val transcriptionResult = speechRecognitionManager.transcribeAudioFile(file)
                transcriptionResult.onSuccess { result ->

                    val evidence = SafetyEvidence.createTranscriptionEvidence(
                        sessionId = currentSessionId!!,
                        transcription = result.primaryText,
                        confidence = result.confidence,
                        timestamp = System.currentTimeMillis()
                    )

                    safetyEvidenceRepository.saveEvidence(evidence)
                    Log.d(TAG, "📝 Audio transcribed: ${result.primaryText}")

                    // Check for distress keywords
                    checkForDistressKeywords(result.primaryText)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to transcribe audio", e)
        }
    }

    private suspend fun saveLocationEvidence(location: android.location.Location) {
        try {
            val evidence = SafetyEvidence.createLocationEvidence(
                sessionId = currentSessionId!!,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                timestamp = System.currentTimeMillis()
            )

            safetyEvidenceRepository.saveEvidence(evidence)
            Log.d(TAG, "📍 Location saved: ${location.latitude}, ${location.longitude}")

            // Send location to emergency contacts periodically
            emergencyContactNotificationManager.sendLocationUpdate(location, currentSessionId!!)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save location evidence", e)
        }
    }

    private suspend fun checkForDistressKeywords(transcription: String) {
        val distressKeywords = listOf("help", "emergency", "danger", "scared", "hurt", "call police", "911")
        val lowerTranscription = transcription.lowercase()

        val foundKeywords = distressKeywords.filter { lowerTranscription.contains(it) }

        if (foundKeywords.isNotEmpty()) {
            Log.w(TAG, "🚨 Distress keywords detected: $foundKeywords")

            // Auto-escalate to emergency if distress detected
            emergencyContactNotificationManager.sendDistressAlert(transcription, foundKeywords)

            // Consider auto-escalation to emergency mode
            val currentStatus = userRepository.observeCurrentUserProfile().first()?.safetyStatus
            if (currentStatus == SafetyStatus.ENABLED) {
                userRepository.updateSafetyStatus(SafetyStatus.EMERGENCY)
                updateNotification("🚨 DISTRESS DETECTED", "Emergency contacts notified")
            }
        }
    }

    private suspend fun notifyEmergencyContactsOfActivation() {
        try {
            emergencyContactNotificationManager.sendSafetyModeActivationNotification(currentSessionId!!)
            Log.i(TAG, "📱 Emergency contacts notified of safety mode activation")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to notify emergency contacts", e)
        }
    }

    private suspend fun notifyEmergencyContactsOfDeactivation() {
        try {
            emergencyContactNotificationManager.sendSafetyModeDeactivationNotification(currentSessionId!!)
            Log.i(TAG, "📱 Emergency contacts notified of safety mode deactivation")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to notify emergency contacts of deactivation", e)
        }
    }

    private suspend fun captureEmergencyEvidence() {
        try {
            // Capture immediate photo
            captureAndSavePhoto()

            // Get current location
            val location = locationTrackingManager.getCurrentLocation()
            location?.let { saveLocationEvidence(it) }

            // Process any pending audio
            transcribeRecentAudio()

            Log.i(TAG, "🚨 Emergency evidence captured")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to capture emergency evidence", e)
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun checkAndProcessEvidence() {
        try {
            // Check storage space
            if (isStorageSpaceLow()) {
                compressOldEvidence()
            }

            // Upload pending evidence if connected
            if (isNetworkAvailable()) {
                uploadPendingEvidence()
            }

            // Health check on monitoring components
            if (!audioRecordingManager.isRecording()) {
                Log.w(TAG, "⚠️ Audio recording stopped unexpectedly, restarting...")
                startAudioRecording()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in evidence processing check", e)
        }
    }

    private suspend fun processAndUploadFinalEvidence() {
        try {
            // Stop and finalize audio recording
            audioRecordingManager.finalizeRecording()

            // Upload all evidence for this session
            uploadPendingEvidence()

            // Create session summary
            createSessionSummary()

            Log.i(TAG, "✅ Final evidence processing completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in final evidence processing", e)
        }
    }

    private suspend fun createSessionSummary() {
        try {
            val summary = safetyEvidenceRepository.createSessionSummary(currentSessionId!!).getOrNull()
            Log.i(TAG, "📊 Session summary created: ${summary?.evidenceCount} items")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create session summary", e)
        }
    }

    // Helper methods
    private fun generateSessionId(): String {
        return "safety_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    private fun isStorageSpaceLow(): Boolean {
        // Check if available storage is less than 100MB
        val availableBytes = filesDir.freeSpace
        return availableBytes < 100 * 1024 * 1024 // 100MB
    }

    private suspend fun compressOldEvidence() {
        try {
            safetyEvidenceRepository.compressOldEvidence()
            Log.d(TAG, "🗜️ Old evidence compressed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to compress old evidence", e)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }

    private suspend fun uploadPendingEvidence() {
        try {
            safetyEvidenceRepository.uploadPendingEvidence()
            Log.d(TAG, "☁️ Pending evidence uploaded")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload pending evidence", e)
        }
    }

    private fun handleMonitoringError(error: Exception) {
        Log.e(TAG, "🚨 Critical monitoring error", error)

        // Try to save error state and notify emergency contacts
        serviceScope.launch {
            try {
                emergencyContactNotificationManager.sendSystemErrorAlert(error.message ?: "Unknown error")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to send error alert", e)
            }
        }
    }

    // Notification management
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Safety Monitoring",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for active safety monitoring"
            setSound(null, null) // Silent notifications
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_security) // You'll need this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "💀 SafetyMonitoringService destroyed")
        monitoringJob?.cancel()
    }
}
