// services/VoiceDetectionForegroundService.kt - FIXED: Resource Management
package com.safeguardme.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
import com.safeguardme.app.managers.SpeechRecognitionManager
import com.safeguardme.app.managers.VoiceDetectionManager
import com.safeguardme.app.utils.ServiceCommunication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class VoiceDetectionForegroundService : Service(), RecognitionListener {

    companion object {
        private const val TAG = "VoiceDetectionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_detection_channel"

        // Extra keys
        const val EXTRA_KEYWORD = "keyword"
        const val EXTRA_ENABLE_DIAGNOSTICS = "enable_diagnostics"

        // ✅ FIXED: Resource management constants
        private const val RESOURCE_CLEANUP_DELAY = 2000L  // 2 seconds for cleanup
        private const val RESTART_DELAY_FIXED = 30000L    // Fixed 30s delay, not exponential
        private const val MAX_CONSECUTIVE_FAILURES = 3    // Switch to burst mode after 3 failures
        private const val AUDIO_RESOURCE_CHECK_THRESHOLD = 2  // Max 2 active recordings

        // Service actions (unchanged)
        const val ACTION_START_DETECTION = "com.safeguardme.START_VOICE_DETECTION"
        const val ACTION_STOP_DETECTION = "com.safeguardme.STOP_VOICE_DETECTION"
        const val ACTION_UPDATE_KEYWORD = "com.safeguardme.UPDATE_VOICE_KEYWORD"
        const val ACTION_SWITCH_TO_BURST = "com.safeguardme.SWITCH_TO_BURST_MODE"

        fun updateKeyword(context: Context, keyword: String) {
            val intent = Intent(context, VoiceDetectionForegroundService::class.java).apply {
                action = ACTION_UPDATE_KEYWORD
                putExtra(EXTRA_KEYWORD, keyword)
            }
            context.startService(intent)
        }
        fun startVoiceDetection(context: Context, keyword: String? = null) {
            val intent = Intent(context, VoiceDetectionForegroundService::class.java).apply {
                action = ACTION_START_DETECTION
                keyword?.let { putExtra("keyword", it) }
            }
            context.startForegroundService(intent)
        }

        fun stopVoiceDetection(context: Context) {
            val intent = Intent(context, VoiceDetectionForegroundService::class.java).apply {
                action = ACTION_STOP_DETECTION
            }
            context.startService(intent)
        }

        fun switchToBurstMode(context: Context) {
            val intent = Intent(context, VoiceDetectionForegroundService::class.java).apply {
                action = ACTION_SWITCH_TO_BURST
            }
            context.startService(intent)
        }
    }



    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var speechRecognitionManager: SpeechRecognitionManager
    @Inject lateinit var voiceDetectionManager: VoiceDetectionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // ✅ ENHANCED: State tracking with failure management
    @Volatile private var isServiceActive = false
    @Volatile private var isSpeechRecognizerReady = false
    @Volatile private var isListening = false
    @Volatile private var consecutiveFailures = 0
    @Volatile private var lastFailureTime = 0L
    @Volatile private var shouldSwitchToBurst = false

    // Configuration
    private var currentKeywords: Set<String> = emptySet()
    private var detectionSensitivity = 0.8f

    // Speech recognition (MAIN THREAD ONLY)
    private var speechRecognizer: SpeechRecognizer? = null

    // ✅ NEW: Enhanced audio resource management
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var lastResourceCheck = 0L

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🔧 VoiceDetectionForegroundService onCreate (FIXED Resource Management)")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()

        Log.i(TAG, "✅ Service created with enhanced resource management")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "unknown"
        Log.i(TAG, "🚀 onStartCommand - Action: $action")

        when (action) {
            ACTION_START_DETECTION -> {
                val keyword = intent?.getStringExtra("keyword")
                startVoiceDetectionWithFallback(keyword)
            }
            ACTION_STOP_DETECTION -> {
                stopVoiceDetectionSafely()
                stopSelf()
            }
            ACTION_SWITCH_TO_BURST -> {
                switchToBurstMode()
            }
            else -> startVoiceDetectionWithFallback()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "💀 VoiceDetectionForegroundService onDestroy")
        stopVoiceDetectionSafely()
    }

    // ================================================
    // ✅ FIXED: Resource Management System
    // ================================================

    private fun startVoiceDetectionWithFallback(keyword: String? = null) {
        serviceScope.launch {
            try {
                Log.i(TAG, "🎤 Starting voice detection with resource fallback")

                // Start foreground service immediately
                startForeground(NOTIFICATION_ID, createNotification("🔧 Initializing voice detection..."))
                isServiceActive = true

                // Load keywords
                if (keyword != null) {
                    currentKeywords = setOf(keyword.lowercase().trim())
                } else {
                    loadKeywords()
                }

                // ✅ CRITICAL: Check resource availability before starting
                if (!isAudioResourceAvailable()) {
                    Log.w(TAG, "⚠️ Audio resources not available - switching to burst mode")
                    switchToBurstMode()
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    val initResult = initializeSpeechRecognizerSafely()
                    if (initResult.isSuccess) {
                        val listenResult = startListeningSafely()
                        if (listenResult.isSuccess) {
                            Log.i(TAG, "✅ Voice detection operational")
                            updateNotification("🛡️ Voice detection active - listening for \"${currentKeywords.firstOrNull()}\"")
                            consecutiveFailures = 0 // Reset on success
                        } else {
                            handleStartupFailure("Failed to start listening")
                        }
                    } else {
                        handleStartupFailure("Failed to initialize recognizer")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Critical error in startup", e)
                handleStartupFailure("Startup error: ${e.message}")
            }
        }
    }



    private fun stopVoiceDetectionSafely() {
        Log.i(TAG, "🛑 Stopping voice detection safely")
        isServiceActive = false

        mainHandler.post {
            emergencyResourceRelease()
        }

        updateNotification("Voice detection stopped")
    }

    /**
     * ✅ CRITICAL: Emergency resource release with proper cleanup
     */
    private fun emergencyResourceRelease() {
        try {
            Log.i(TAG, "🚨 Emergency resource release initiated")

            // Step 1: Stop all recognition activity
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            isListening = false

            // Step 2: Release audio focus immediately
            releaseAudioFocus()

            // Step 3: Destroy recognizer
            speechRecognizer?.destroy()
            speechRecognizer = null
            isSpeechRecognizerReady = false

            // Step 4: CRITICAL - Wait for Android to clean up resources
            Thread.sleep(RESOURCE_CLEANUP_DELAY)

            Log.i(TAG, "✅ Emergency resource release completed")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during emergency resource release", e)
        }
    }

    /**
     * ✅ CRITICAL: Audio resource availability check
     */
    private fun isAudioResourceAvailable(): Boolean {
        return try {
            val now = System.currentTimeMillis()
            if (now - lastResourceCheck < 5000) {
                // Don't check too frequently
                return true
            }
            lastResourceCheck = now

            audioManager?.let { manager ->
                val audioMode = manager.mode
                val microphoneMuted = manager.isMicrophoneMute
                val activeRecordings = manager.activeRecordingConfigurations.size

                val isAvailable = audioMode == AudioManager.MODE_NORMAL &&
                        !microphoneMuted &&
                        activeRecordings < AUDIO_RESOURCE_CHECK_THRESHOLD &&
                        SpeechRecognizer.isRecognitionAvailable(this)

                Log.d(TAG, "🔍 Audio resource check:")
                Log.d(TAG, "   Mode: $audioMode (should be ${AudioManager.MODE_NORMAL})")
                Log.d(TAG, "   Mic muted: $microphoneMuted")
                Log.d(TAG, "   Active recordings: $activeRecordings (limit: $AUDIO_RESOURCE_CHECK_THRESHOLD)")
                Log.d(TAG, "   Recognition available: ${SpeechRecognizer.isRecognitionAvailable(this)}")
                Log.d(TAG, "   Available: $isAvailable")

                isAvailable
            } ?: false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking audio resource availability", e)
            false
        }
    }

    /**
     * ✅ FIXED: Safe speech recognizer initialization
     */
    private fun initializeSpeechRecognizerSafely(): Result<Unit> {
        return try {
            Log.i(TAG, "🔧 Safe speech recognizer initialization")

            // Clean up any existing recognizer first
            emergencyResourceRelease()

            // Check system availability
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                throw Exception("Speech recognition not available")
            }

            // Request audio focus before creating recognizer
            if (!requestAudioFocus()) {
                throw Exception("Could not obtain audio focus")
            }

            // Create new recognizer
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)?.apply {
                setRecognitionListener(this@VoiceDetectionForegroundService)
            } ?: throw Exception("Failed to create SpeechRecognizer")

            isSpeechRecognizerReady = true
            Log.i(TAG, "✅ Speech recognizer initialized safely")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Safe initialization failed", e)
            isSpeechRecognizerReady = false
            Result.failure(e)
        }
    }

    /**
     * ✅ FIXED: Safe listening start with resource checks
     */
    private fun startListeningSafely(): Result<Unit> {
        return try {
            if (!isSpeechRecognizerReady || speechRecognizer == null) {
                throw Exception("Speech recognizer not ready")
            }

            if (!isAudioResourceAvailable()) {
                throw Exception("Audio resources not available")
            }

            Log.i(TAG, "👂 Starting safe listening session")

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)

                // ✅ ENHANCED: More conservative timing to reduce conflicts
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            }

            speechRecognizer?.startListening(intent)
            Log.i(TAG, "🚀 Safe listening started")

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Safe listening start failed", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ FIXED: Handle startup failures with intelligent recovery
     */
    private fun handleStartupFailure(reason: String) {
        consecutiveFailures++
        lastFailureTime = System.currentTimeMillis()

        Log.w(TAG, "⚠️ Startup failure #$consecutiveFailures: $reason")

        when {
            consecutiveFailures >= MAX_CONSECUTIVE_FAILURES -> {
                Log.w(TAG, "🔄 Too many failures ($consecutiveFailures) - switching to burst mode")
                switchToBurstMode()
            }
            else -> {
                Log.w(TAG, "🔄 Scheduling restart in ${RESTART_DELAY_FIXED / 1000}s")
                updateNotification("Voice detection error - retrying in 30s...")

                mainHandler.postDelayed({
                    if (isServiceActive) {
                        Log.i(TAG, "🔄 Attempting restart after failure")
                        restartWithResourceCheck()
                    }
                }, RESTART_DELAY_FIXED)
            }
        }
    }

    /**
     * ✅ NEW: Restart with comprehensive resource checking
     */
    private fun restartWithResourceCheck() {
        serviceScope.launch {
            try {
                Log.i(TAG, "🔄 Restart with resource check")

                if (!isAudioResourceAvailable()) {
                    Log.w(TAG, "⚠️ Resources still unavailable - switching to burst mode")
                    switchToBurstMode()
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    emergencyResourceRelease()
                    delay(RESOURCE_CLEANUP_DELAY)

                    val initResult = initializeSpeechRecognizerSafely()
                    if (initResult.isSuccess) {
                        val listenResult = startListeningSafely()
                        if (listenResult.isSuccess) {
                            Log.i(TAG, "✅ Restart successful")
                            updateNotification("🛡️ Voice detection active - listening for \"${currentKeywords.firstOrNull()}\"")
                            consecutiveFailures = 0 // Reset on success
                        } else {
                            handleStartupFailure("Restart: listening failed")
                        }
                    } else {
                        handleStartupFailure("Restart: initialization failed")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during restart", e)
                handleStartupFailure("Restart error: ${e.message}")
            }
        }
    }

    /**
     * ✅ NEW: Switch to burst mode when continuous fails
     */
    private fun switchToBurstMode() {
        try {
            Log.w(TAG, "🔄 Switching to burst mode due to resource conflicts")

            // Stop this service
            stopVoiceDetectionSafely()

            // Start burst mode service
            val burstIntent = Intent(this, BurstVoiceDetectionService::class.java).apply {
                action = BurstVoiceDetectionService.ACTION_START_BURST_DETECTION
                putExtra("keywords", currentKeywords.toTypedArray())
                putExtra("sensitivity", detectionSensitivity)
            }
            startForegroundService(burstIntent)

            // Update user
            updateNotification("🔄 Switched to burst mode - resource-friendly detection")

            // Send broadcast to inform other components
            sendBroadcast(Intent("com.safeguardme.VOICE_DETECTION_MODE_CHANGED").apply {
                putExtra("mode", "burst")
                putExtra("reason", "resource_conflict")
            })

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error switching to burst mode", e)
        }
    }

    // ================================================
    // ✅ ENHANCED: Recognition Listener with Failure Handling
    // ================================================

    override fun onReadyForSpeech(params: Bundle?) {
        Log.i(TAG, "🎤 [CALLBACK] Ready for speech")
        isListening = true
        consecutiveFailures = 0 // Reset on successful start
        updateNotification("🛡️ Listening for voice triggers...")
    }

    override fun onBeginningOfSpeech() {
        Log.i(TAG, "👂 [CALLBACK] Beginning of speech detected")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Audio level monitoring (optional detailed logging removed for performance)
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Audio buffer received
    }

    override fun onEndOfSpeech() {
        Log.i(TAG, "🔚 [CALLBACK] End of speech")
        isListening = false

        // Auto-restart for continuous listening
        mainHandler.postDelayed({
            if (isServiceActive && isSpeechRecognizerReady) {
                restartWithResourceCheck()
            }
        }, 1000)
    }

    override fun onError(error: Int) {
        val errorMsg = getErrorMessage(error)
        Log.w(TAG, "⚠️ [CALLBACK] Recognition error: $errorMsg ($error)")

        isListening = false
        consecutiveFailures++

        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Normal timeouts - just restart
                Log.d(TAG, "🔄 Normal timeout - restarting")
                mainHandler.postDelayed({
                    if (isServiceActive) restartWithResourceCheck()
                }, 2000)
            }

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> {
                // ✅ FIXED: Resource conflict - emergency release and longer delay
                Log.w(TAG, "⚠️ Resource conflict detected - emergency cleanup")
                emergencyResourceRelease()

                mainHandler.postDelayed({
                    if (isServiceActive) {
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            Log.w(TAG, "🔄 Too many resource conflicts - switching to burst mode")
                            switchToBurstMode()
                        } else {
                            restartWithResourceCheck()
                        }
                    }
                }, RESTART_DELAY_FIXED)
            }

            else -> {
                Log.e(TAG, "🚨 Critical error: $errorMsg")
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    switchToBurstMode()
                } else {
                    mainHandler.postDelayed({
                        if (isServiceActive) restartWithResourceCheck()
                    }, RESTART_DELAY_FIXED)
                }
            }
        }
    }

    override fun onResults(results: Bundle?) {
        Log.i(TAG, "🔍 [CALLBACK] Final results received")
        handleRecognitionResults(results, false)

        // Auto-restart for continuous listening
        mainHandler.postDelayed({
            if (isServiceActive && isSpeechRecognizerReady) {
                restartWithResourceCheck()
            }
        }, 1000)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        Log.d(TAG, "🔍 [CALLBACK] Partial results received")
        handleRecognitionResults(partialResults, true)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        Log.d(TAG, "📢 [CALLBACK] Recognition event: $eventType")
    }

    // ================================================
    // ✅ ENHANCED: Audio Focus Management
    // ================================================

    private fun requestAudioFocus(): Boolean {
        return try {
            if (hasAudioFocus) return true

            audioManager?.let { manager ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener { focusChange ->
                            handleAudioFocusChange(focusChange)
                        }
                        .build()

                    val result = manager.requestAudioFocus(audioFocusRequest!!)
                    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                } else {
                    @Suppress("DEPRECATION")
                    val result = manager.requestAudioFocus(
                        { focusChange -> handleAudioFocusChange(focusChange) },
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                    )
                    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                }

                Log.d(TAG, "🔊 Audio focus request result: hasAudioFocus = $hasAudioFocus")
                hasAudioFocus
            } ?: false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting audio focus", e)
            false
        }
    }

    private fun releaseAudioFocus() {
        try {
            if (!hasAudioFocus) return

            audioManager?.let { manager ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { request ->
                        manager.abandonAudioFocusRequest(request)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    manager.abandonAudioFocus { }
                }
            }

            hasAudioFocus = false
            Log.d(TAG, "🔊 Audio focus released")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing audio focus", e)
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                Log.d(TAG, "🔊 Audio focus gained")
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                Log.w(TAG, "🔊 Audio focus lost: $focusChange")
                if (isListening) {
                    speechRecognizer?.stopListening()
                    isListening = false
                }
            }
        }
    }

    // ================================================
    // ✅ UNCHANGED: Result Processing and Utilities
    // ================================================

    private fun handleRecognitionResults(results: Bundle?, isPartial: Boolean) {
        try {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches.isNullOrEmpty()) return

            val resultType = if (isPartial) "partial" else "final"
            Log.i(TAG, "🔍 Processing $resultType results: $matches")

            matches.forEach { result ->
                val lowerResult = result.lowercase().trim()
                currentKeywords.forEach { keyword ->
                    if (lowerResult.contains(keyword)) {
                        val confidence = calculateMatchConfidence(lowerResult, keyword)
                        if (confidence >= detectionSensitivity) {
                            handleKeywordDetected(keyword, result, confidence)
                            return
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing recognition results", e)
        }
    }

    private fun handleKeywordDetected(keyword: String, fullText: String, confidence: Float) {
        serviceScope.launch {
            try {
                Log.e(TAG, "🚨 KEYWORD DETECTED: '$keyword' in '$fullText' (confidence: $confidence)")

                updateNotification("🚨 Voice trigger detected: \"$keyword\"!")

                // Notify other components
                voiceDetectionManager.onVoiceTriggerDetected(keyword, confidence)
                ServiceCommunication.notifyVoiceTriggerDetected(this@VoiceDetectionForegroundService, keyword, fullText, confidence)

                delay(3000)
                updateNotification("🛡️ Voice detection active - listening for triggers")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error handling keyword detection", e)
            }
        }
    }

    // ================================================
    // ✅ UNCHANGED: Helper Methods
    // ================================================

    private suspend fun loadKeywords() {
        try {
            val triggerData = userRepository.getVoiceTriggerData().getOrNull()
            val keyword = triggerData?.keyword

            currentKeywords = if (!keyword.isNullOrBlank()) {
                setOf(keyword.lowercase().trim(), "help me safeguard", "safeguard emergency")
            } else {
                setOf("help me safeguard", "safeguard emergency")
            }

            Log.i(TAG, "📋 Loaded keywords: $currentKeywords")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading keywords", e)
            currentKeywords = setOf("help me safeguard")
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
            SpeechRecognizer.ERROR_CLIENT -> "Client error (resource conflict)"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            12 -> "Recognizer busy (API31+)"
            else -> "Unknown error ($errorCode)"
        }
    }

    // ================================================
    // ✅ UNCHANGED: Notification Management
    // ================================================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "🛡️ Emergency Safety Monitor",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Emergency voice detection service"
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
            .setContentTitle("🛡️ Voice Detection Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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