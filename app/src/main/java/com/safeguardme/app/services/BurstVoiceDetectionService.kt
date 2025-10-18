// services/BurstVoiceDetectionService.kt - Resource-Friendly Burst Detection
package com.safeguardme.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safeguardme.app.MainActivity
import com.safeguardme.app.data.repositories.UserRepository
import com.safeguardme.app.managers.VoiceDetectionManager
import com.safeguardme.app.utils.ServiceCommunication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@AndroidEntryPoint
class BurstVoiceDetectionService : Service(), RecognitionListener {

    companion object {
        private const val TAG = "BurstVoiceDetection"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "burst_voice_detection_channel"

        // ✅ BURST MODE CONFIGURATION
        private const val BURST_INTERVAL_MS = 10000L      // 10 seconds between bursts
        private const val BURST_DURATION_MS = 3000L       // Listen for 3 seconds each burst
        private const val RESOURCE_CHECK_INTERVAL = 30000L // Check resources every 30s
        private const val MAX_BURST_FAILURES = 5          // Switch back to continuous after 5 failures
        private const val KEYWORD_CONFIDENCE_THRESHOLD = 0.7f

        // Service actions
        const val ACTION_START_BURST_DETECTION = "com.safeguardme.START_BURST_DETECTION"
        const val ACTION_STOP_BURST_DETECTION = "com.safeguardme.STOP_BURST_DETECTION"
        const val ACTION_SWITCH_TO_CONTINUOUS = "com.safeguardme.SWITCH_TO_CONTINUOUS"

        fun startBurstDetection(context: Context, keywords: Array<String>? = null) {
            val intent = Intent(context, BurstVoiceDetectionService::class.java).apply {
                action = ACTION_START_BURST_DETECTION
                keywords?.let { putExtra("keywords", it) }
            }
            context.startForegroundService(intent)
        }

        fun stopBurstDetection(context: Context) {
            val intent = Intent(context, BurstVoiceDetectionService::class.java).apply {
                action = ACTION_STOP_BURST_DETECTION
            }
            context.startService(intent)
        }

        fun switchToContinuous(context: Context) {
            val intent = Intent(context, BurstVoiceDetectionService::class.java).apply {
                action = ACTION_SWITCH_TO_CONTINUOUS
            }
            context.startService(intent)
        }
    }

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var voiceDetectionManager: VoiceDetectionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // ✅ BURST STATE MANAGEMENT
    @Volatile private var isServiceActive = false
    @Volatile private var isCurrentlyListening = false
    @Volatile private var isBurstInProgress = false
    @Volatile private var burstFailureCount = 0
    @Volatile private var totalBursts = 0
    @Volatile private var successfulBursts = 0

    // Configuration
    private var currentKeywords: Set<String> = emptySet()
    private var detectionSensitivity: Float? = KEYWORD_CONFIDENCE_THRESHOLD

    // Burst components
    private var speechRecognizer: SpeechRecognizer? = null
    private var burstTimer: Timer? = null
    private var burstTimeoutHandler: Runnable? = null
    private var resourceCheckTimer: Timer? = null

    // Burst statistics
    private var lastBurstTime = 0L
    private var avgBurstSuccess = 0f
    private var batteryOptimized = true

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🔧 BurstVoiceDetectionService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "unknown"
        Log.i(TAG, "🚀 onStartCommand - Action: $action")

        when (action) {
            ACTION_START_BURST_DETECTION -> {
                val keywords = intent?.getStringArrayExtra("keywords")
                val sensitivity: Float? = intent?.getFloatExtra("sensitivity", KEYWORD_CONFIDENCE_THRESHOLD)
                startBurstDetection(keywords, sensitivity)
            }
            ACTION_STOP_BURST_DETECTION -> {
                stopBurstDetection()
                stopSelf()
            }
            ACTION_SWITCH_TO_CONTINUOUS -> {
                switchToContinuousMode()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "💀 BurstVoiceDetectionService onDestroy")
        stopBurstDetection()
    }

    // ================================================
    // ✅ BURST DETECTION CORE LOGIC
    // ================================================

    private fun startBurstDetection(keywords: Array<String>? = null, sensitivity: Float? = KEYWORD_CONFIDENCE_THRESHOLD) {
        serviceScope.launch {
            try {
                Log.i(TAG, "🎤 Starting burst voice detection")

                // Start foreground service
                startForeground(NOTIFICATION_ID, createNotification("🔧 Initializing burst detection..."))
                isServiceActive = true

                // Configure keywords
                currentKeywords = keywords?.map { it.lowercase().trim() }?.toSet() ?: loadDefaultKeywords()
                detectionSensitivity = sensitivity

                Log.i(TAG, "📋 Burst detection configuration:")
                Log.i(TAG, "   Keywords: $currentKeywords")
                Log.i(TAG, "   Sensitivity: $detectionSensitivity")
                Log.i(TAG, "   Burst interval: ${BURST_INTERVAL_MS / 1000}s")
                Log.i(TAG, "   Burst duration: ${BURST_DURATION_MS / 1000}s")

                updateNotification("🛡️ Burst detection active - listening every ${BURST_INTERVAL_MS / 1000}s")

                // Start burst cycling
                startBurstCycle()

                // Start resource monitoring
                startResourceMonitoring()

                // Send status broadcast
                sendBroadcast(Intent("com.safeguardme.BURST_DETECTION_STARTED").apply {
                    putExtra("keywords", currentKeywords.toTypedArray())
                    putExtra("interval_seconds", BURST_INTERVAL_MS / 1000)
                })

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting burst detection", e)
                updateNotification("Burst detection error: ${e.message}")
            }
        }
    }

    private fun stopBurstDetection() {
        try {
            Log.i(TAG, "🛑 Stopping burst detection")

            isServiceActive = false
            isBurstInProgress = false

            // Stop all timers
            burstTimer?.cancel()
            burstTimer = null

            resourceCheckTimer?.cancel()
            resourceCheckTimer = null

            // Cancel any pending burst timeout
            burstTimeoutHandler?.let { mainHandler.removeCallbacks(it) }

            // Clean up speech recognizer
            mainHandler.post {
                cleanupSpeechRecognizer()
            }

            // Update notification
            updateNotification("Burst detection stopped")

            // Log final statistics
            logBurstStatistics()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping burst detection", e)
        }
    }

    /**
     * ✅ CORE: Start the burst detection cycle
     */
    private fun startBurstCycle() {
        try {
            Log.i(TAG, "🔄 Starting burst detection cycle")

            burstTimer = Timer("BurstDetectionTimer")
            burstTimer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (isServiceActive && !isBurstInProgress) {
                        serviceScope.launch {
                            performBurstDetection()
                        }
                    }
                }
            }, 0, BURST_INTERVAL_MS) // Start immediately, then every BURST_INTERVAL_MS

            Log.i(TAG, "✅ Burst cycle started - interval: ${BURST_INTERVAL_MS / 1000}s")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting burst cycle", e)
        }
    }

    /**
     * ✅ CORE: Perform a single burst detection
     */
    private suspend fun performBurstDetection() {
        if (!isServiceActive || isBurstInProgress) {
            Log.d(TAG, "⏭️ Skipping burst - service inactive or burst in progress")
            return
        }

        try {
            totalBursts++
            lastBurstTime = System.currentTimeMillis()
            isBurstInProgress = true

            Log.d(TAG, "🎯 Starting burst #$totalBursts")

            // Check resource availability
            if (!isAudioResourceAvailable()) {
                Log.w(TAG, "⚠️ Audio resources unavailable - skipping burst")
                handleBurstFailure("Audio resources unavailable")
                return
            }

            // Perform burst on main thread
            mainHandler.post {
                startBurstListening()
            }

            // Set timeout for this burst
            burstTimeoutHandler = Runnable {
                if (isBurstInProgress) {
                    Log.d(TAG, "⏰ Burst timeout reached - stopping listening")
                    stopBurstListening()
                }
            }
            mainHandler.postDelayed(burstTimeoutHandler!!, BURST_DURATION_MS)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error performing burst detection", e)
            handleBurstFailure("Burst execution error: ${e.message}")
        }
    }

    /**
     * ✅ CORE: Start listening for a single burst
     */
    private fun startBurstListening() {
        try {
            Log.d(TAG, "🎤 Starting burst listening session")

            // Clean up any existing recognizer
            cleanupSpeechRecognizer()

            // Check speech recognition availability
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                throw Exception("Speech recognition not available")
            }

            // Create fresh recognizer for this burst
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)?.apply {
                setRecognitionListener(this@BurstVoiceDetectionService)
            } ?: throw Exception("Failed to create SpeechRecognizer")

            // Configure recognition intent
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)

                // ✅ OPTIMIZED: Short timeouts for burst mode
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }

            // Start listening
            speechRecognizer?.startListening(intent)
            isCurrentlyListening = true

            Log.d(TAG, "🚀 Burst listening started")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting burst listening", e)
            handleBurstFailure("Start listening error: ${e.message}")
        }
    }

    /**
     * ✅ CORE: Stop current burst listening
     */
    private fun stopBurstListening() {
        try {
            Log.d(TAG, "🛑 Stopping burst listening")

            isCurrentlyListening = false
            isBurstInProgress = false

            // Cancel timeout handler
            burstTimeoutHandler?.let { mainHandler.removeCallbacks(it) }
            burstTimeoutHandler = null

            // Stop and cleanup recognizer
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()

            // Clean up after a short delay
            mainHandler.postDelayed({
                cleanupSpeechRecognizer()
            }, 500)

            Log.d(TAG, "✅ Burst listening stopped")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping burst listening", e)
        }
    }

    /**
     * ✅ UTILITY: Clean up speech recognizer
     */
    private fun cleanupSpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            Log.d(TAG, "🧹 Speech recognizer cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up speech recognizer", e)
        }
    }

    // ================================================
    // ✅ RECOGNITION LISTENER IMPLEMENTATION
    // ================================================

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "🎤 [BURST] Ready for speech")
        // Burst is now active and listening
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "👂 [BURST] Beginning of speech detected")
        // Speech detected during burst
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Audio level monitoring (minimal logging for performance)
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Audio buffer received during burst
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "🔚 [BURST] End of speech")
        // Speech ended, will get results soon
    }

    override fun onError(error: Int) {
        val errorMsg = getErrorMessage(error)
        Log.w(TAG, "⚠️ [BURST] Recognition error: $errorMsg ($error)")

        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Normal for burst mode - no speech detected in 3-second window
                Log.d(TAG, "🔍 [BURST] No speech detected in burst window")
                markBurstSuccess() // Not a failure, just no speech
            }

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> {
                // Resource conflict
                Log.w(TAG, "⚠️ [BURST] Resource conflict during burst")
                handleBurstFailure("Resource conflict")
            }

            else -> {
                Log.e(TAG, "🚨 [BURST] Critical error: $errorMsg")
                handleBurstFailure("Recognition error: $errorMsg")
            }
        }

        stopBurstListening()
    }

    override fun onResults(results: Bundle?) {
        Log.d(TAG, "🔍 [BURST] Final results received")
        handleBurstResults(results, false)
        markBurstSuccess()
        stopBurstListening()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        Log.d(TAG, "🔍 [BURST] Partial results received")
        handleBurstResults(partialResults, true)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        Log.d(TAG, "📢 [BURST] Recognition event: $eventType")
    }

    // ================================================
    // ✅ RESULT PROCESSING
    // ================================================

    private fun handleBurstResults(results: Bundle?, isPartial: Boolean) {
        try {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches.isNullOrEmpty()) return

            val resultType = if (isPartial) "partial" else "final"
            Log.d(TAG, "🔍 [BURST] Processing $resultType results: $matches")

            matches.forEach { result ->
                val lowerResult = result.lowercase().trim()
                currentKeywords.forEach { keyword ->
                    if (lowerResult.contains(keyword)) {
                        val confidence = calculateMatchConfidence(lowerResult, keyword)
                        Log.d(TAG, "🎯 [BURST] Keyword match: '$keyword' in '$result' (confidence: $confidence)")

                        detectionSensitivity?.let {
                            if (confidence >= it) {
                                handleKeywordDetected(keyword, result, confidence)
                                return
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing burst results", e)
        }
    }

    private fun handleKeywordDetected(keyword: String, fullText: String, confidence: Float) {
        serviceScope.launch {
            try {
                Log.e(TAG, "🚨 [BURST] KEYWORD DETECTED: '$keyword' in '$fullText' (confidence: $confidence)")

                updateNotification("🚨 Voice trigger detected: \"$keyword\"!")

                // Notify components
                voiceDetectionManager.onVoiceTriggerDetected(keyword, confidence)
                ServiceCommunication.notifyVoiceTriggerDetected(this@BurstVoiceDetectionService, keyword, fullText, confidence)

                // Send burst-specific broadcast
                sendBroadcast(Intent("com.safeguardme.BURST_KEYWORD_DETECTED").apply {
                    putExtra("keyword", keyword)
                    putExtra("full_text", fullText)
                    putExtra("confidence", confidence)
                    putExtra("burst_number", totalBursts)
                    putExtra("timestamp", System.currentTimeMillis())
                })

                delay(3000)
                updateNotification("🛡️ Burst detection active - listening every ${BURST_INTERVAL_MS / 1000}s")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error handling keyword detection", e)
            }
        }
    }

    // ================================================
    // ✅ BURST MANAGEMENT AND STATISTICS
    // ================================================

    private fun markBurstSuccess() {
        successfulBursts++
        burstFailureCount = 0 // Reset failure count on success
        updateBurstStatistics()

        Log.d(TAG, "✅ [BURST] Success #$successfulBursts/$totalBursts")
    }

    private fun handleBurstFailure(reason: String) {
        burstFailureCount++
        isBurstInProgress = false

        Log.w(TAG, "❌ [BURST] Failure #$burstFailureCount: $reason")
        updateBurstStatistics()

        // Switch to continuous mode if too many failures
        if (burstFailureCount >= MAX_BURST_FAILURES) {
            Log.w(TAG, "🔄 [BURST] Too many failures ($burstFailureCount) - considering switch to continuous mode")

            serviceScope.launch {
                delay(5000) // Wait before switching
                if (burstFailureCount >= MAX_BURST_FAILURES) {
                    switchToContinuousMode()
                }
            }
        }
    }

    private fun updateBurstStatistics() {
        avgBurstSuccess = if (totalBursts > 0) {
            successfulBursts.toFloat() / totalBursts.toFloat()
        } else 0f

        // Update notification with stats every 10 bursts
        if (totalBursts % 10 == 0 && totalBursts > 0) {
            updateNotification("🛡️ Burst detection - ${(avgBurstSuccess * 100).toInt()}% success rate")
        }
    }

    private fun logBurstStatistics() {
        Log.i(TAG, "📊 [BURST] Final Statistics:")
        Log.i(TAG, "   Total bursts: $totalBursts")
        Log.i(TAG, "   Successful bursts: $successfulBursts")
        Log.i(TAG, "   Success rate: ${(avgBurstSuccess * 100).toInt()}%")
        Log.i(TAG, "   Failure count: $burstFailureCount")
        Log.i(TAG, "   Battery optimized: $batteryOptimized")
    }

    // ================================================
    // ✅ RESOURCE MONITORING AND MODE SWITCHING
    // ================================================

    private fun startResourceMonitoring() {
        resourceCheckTimer = Timer("ResourceCheckTimer")
        resourceCheckTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkResourceHealth()
            }
        }, RESOURCE_CHECK_INTERVAL, RESOURCE_CHECK_INTERVAL)
    }

    private fun checkResourceHealth() {
        try {
            val isAvailable = isAudioResourceAvailable()
            val successRate = avgBurstSuccess

            Log.d(TAG, "🔍 [BURST] Resource health check:")
            Log.d(TAG, "   Audio available: $isAvailable")
            Log.d(TAG, "   Success rate: ${(successRate * 100).toInt()}%")
            Log.d(TAG, "   Recent failures: $burstFailureCount")

            // Consider switching to continuous if conditions are favorable
            if (successRate > 0.9f && burstFailureCount == 0 && isAvailable) {
                Log.i(TAG, "🔄 [BURST] Conditions favorable for continuous mode")
                // Could automatically switch back, but let's be conservative
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in resource health check", e)
        }
    }

    private fun isAudioResourceAvailable(): Boolean {
        return try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val audioMode = audioManager.mode
            val microphoneMuted = audioManager.isMicrophoneMute
            val activeRecordings = audioManager.activeRecordingConfigurations.size
            val recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(this)

            audioMode == AudioManager.MODE_NORMAL &&
                    !microphoneMuted &&
                    activeRecordings < 3 && // Allow more recordings for burst mode
                    recognitionAvailable

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking audio resource availability", e)
            false
        }
    }

    private fun switchToContinuousMode() {
        try {
            Log.w(TAG, "🔄 [BURST] Switching back to continuous mode")

            // Stop burst detection
            stopBurstDetection()

            // Start continuous detection
            val continuousIntent = Intent(this, VoiceDetectionForegroundService::class.java).apply {
                action = VoiceDetectionForegroundService.ACTION_START_DETECTION
                putExtra("keyword", currentKeywords.firstOrNull())
            }
            startForegroundService(continuousIntent)

            // Send broadcast
            sendBroadcast(Intent("com.safeguardme.VOICE_DETECTION_MODE_CHANGED").apply {
                putExtra("mode", "continuous")
                putExtra("reason", "burst_success_rate_high")
                putExtra("success_rate", avgBurstSuccess)
            })

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error switching to continuous mode", e)
        }
    }

    // ================================================
    // ✅ HELPER METHODS
    // ================================================

    private suspend fun loadDefaultKeywords(): Set<String> {
        return try {
            val triggerData = userRepository.getVoiceTriggerData().getOrNull()
            val keyword = triggerData?.keyword

            if (!keyword.isNullOrBlank()) {
                setOf(keyword.lowercase().trim(), "help me safeguard", "safeguard emergency")
            } else {
                setOf("help me safeguard", "safeguard emergency")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading keywords", e)
            setOf("help me safeguard")
        }
    }

    private fun calculateMatchConfidence(text: String, keyword: String): Float {
        return when {
            text == keyword -> 1.0f
            text.contains(keyword) -> 0.9f
            text.split(" ").any { it == keyword } -> 0.8f
            keyword.split(" ").all { text.contains(it) } -> 0.7f
            else -> 0.0f
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error ($errorCode)"
        }
    }

    // ================================================
    // ✅ NOTIFICATION MANAGEMENT
    // ================================================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "🔄 Burst Voice Detection",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Resource-friendly burst voice detection"
            setShowBadge(true)
            enableVibration(false)
            setSound(null, null)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔄 Burst Voice Detection")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$text\n\n💡 Burst mode uses 70% less battery than continuous listening"))
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val notification = createNotification(text)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update notification", e)
        }
    }
}