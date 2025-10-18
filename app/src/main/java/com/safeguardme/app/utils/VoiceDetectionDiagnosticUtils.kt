// utils/VoiceDetectionDiagnosticUtils.kt - Testing and Diagnostic Tools
package com.safeguardme.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ✅ Voice Detection Diagnostic Utils
 * Provides testing and diagnostic tools for voice detection system
 */
object VoiceDetectionDiagnosticUtils {
    private const val TAG = "VoiceDiagnosticUtils"
    private const val DIVIDER = "\n"

    /**
     * ✅ Send diagnostic commands to service
     */
    fun requestDiagnosticReport(context: Context) {
        Log.i(TAG, "📊 Requesting diagnostic report")
        context.sendBroadcast(Intent("com.safeguardme.VOICE_DETECTION_DIAGNOSTIC_REQUEST"))
    }

    fun forceServiceRestart(context: Context) {
        Log.w(TAG, "🔄 Forcing service restart")
        context.sendBroadcast(Intent("com.safeguardme.VOICE_DETECTION_FORCE_RESTART"))
    }

    fun enableDiagnosticMode(context: Context) {
        Log.i(TAG, "🔬 Enabling diagnostic mode")
        context.sendBroadcast(Intent("com.safeguardme.VOICE_DETECTION_ENABLE_DIAGNOSTICS"))
    }

    fun testKeywordDetection(context: Context, keyword: String = "test") {
        Log.i(TAG, "🧪 Testing keyword detection: $keyword")
        context.sendBroadcast(Intent("com.safeguardme.VOICE_DETECTION_TEST_KEYWORD").apply {
            putExtra("keyword", keyword)
        })
    }

    /**
     * ✅ Register to receive diagnostic broadcasts
     */
    fun registerDiagnosticListener(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        onHealthReport: (VoiceDetectionHealthReport) -> Unit = {},
        onAudioDiagnostic: (AudioDiagnosticResult) -> Unit = {},
        onTriggerDetected: (VoiceTriggerEvent) -> Unit = {},
        onCriticalAlert: () -> Unit = {}
    ): BroadcastReceiver {

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.safeguardme.VOICE_DETECTION_ENHANCED_HEALTH" -> {
                        try {
                            val healthReport = VoiceDetectionHealthReport.fromIntent(intent)
                            Log.i(TAG, "📊 Health report received: $healthReport")
                            onHealthReport(healthReport)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error parsing health report", e)
                        }
                    }

                    "com.safeguardme.AUDIO_DIAGNOSTIC_RESULT" -> {
                        try {
                            val diagnosticResult = AudioDiagnosticResult.fromIntent(intent)
                            Log.i(TAG, "🔍 Audio diagnostic received: $diagnosticResult")
                            onAudioDiagnostic(diagnosticResult)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error parsing audio diagnostic", e)
                        }
                    }

                    "com.safeguardme.VOICE_TRIGGER_DETECTED" -> {
                        try {
                            val triggerEvent = VoiceTriggerEvent.fromIntent(intent)
                            Log.w(TAG, "🚨 Voice trigger detected: $triggerEvent")
                            onTriggerDetected(triggerEvent)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error parsing trigger event", e)
                        }
                    }

                    "com.safeguardme.CRITICAL_NOTIFICATION_SUPPRESSION" -> {
                        Log.e(TAG, "🚨 CRITICAL: Notification suppression detected")
                        onCriticalAlert()
                    }

                    "com.safeguardme.VOICE_DETECTION_START_FAILED" -> {
                        val errorMessage = intent.getStringExtra("error_message") ?: "Unknown error"
                        Log.e(TAG, "❌ Voice detection start failed: $errorMessage")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.safeguardme.VOICE_DETECTION_ENHANCED_HEALTH")
            addAction("com.safeguardme.AUDIO_DIAGNOSTIC_RESULT")
            addAction("com.safeguardme.VOICE_TRIGGER_DETECTED")
            addAction("com.safeguardme.CRITICAL_NOTIFICATION_SUPPRESSION")
            addAction("com.safeguardme.VOICE_DETECTION_START_FAILED")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {

            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

        }

        // Auto-unregister when lifecycle ends
        lifecycleOwner.lifecycleScope.launch {
            try {
                lifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        try {
                            context.unregisterReceiver(receiver)
                            Log.d(TAG, "🧹 Diagnostic receiver unregistered")
                        } catch (e: Exception) {
                            Log.w(TAG, "⚠️ Error unregistering receiver", e)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error setting up lifecycle observer", e)
            }
        }

        return receiver
    }

    /**
     * ✅ Comprehensive voice detection system test
     */
    suspend fun runComprehensiveSystemTest(
        context: Context,
        onProgress: (String) -> Unit = {},
        onResult: (SystemTestResult) -> Unit = {}
    ) {
        Log.i(TAG, "🧪 Starting comprehensive system test")

        val testResults = mutableListOf<TestStep>()

        try {
            // Test 1: Service availability
            onProgress("Testing service availability...")
            delay(500)

            requestDiagnosticReport(context)
            delay(2000)

            testResults.add(TestStep("Service Communication", true, "Diagnostic request sent"))

            // Test 2: Audio permissions
            onProgress("Checking audio permissions...")
            delay(500)

            val hasAudioPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            testResults.add(TestStep("Audio Permission", hasAudioPermission,
                if (hasAudioPermission) "Audio permission granted" else "Audio permission missing"))

            // Test 3: Speech recognition availability
            onProgress("Testing speech recognition...")
            delay(500)

            val speechAvailable = android.speech.SpeechRecognizer.isRecognitionAvailable(context)
            testResults.add(TestStep("Speech Recognition", speechAvailable,
                if (speechAvailable) "Speech recognition available" else "Speech recognition not available"))

            // Test 4: Test keyword detection
            onProgress("Testing keyword detection...")
            delay(500)

            testKeywordDetection(context, "diagnostic_test")
            delay(1000)

            testResults.add(TestStep("Keyword Test", true, "Test keyword sent"))

            // Test 5: Force restart test
            onProgress("Testing recovery mechanisms...")
            delay(500)

            forceServiceRestart(context)
            delay(3000)

            testResults.add(TestStep("Recovery Test", true, "Force restart completed"))

            // Test 6: Enable diagnostic mode
            onProgress("Enabling diagnostic mode...")
            delay(500)

            enableDiagnosticMode(context)
            delay(1000)

            testResults.add(TestStep("Diagnostic Mode", true, "Diagnostic mode enabled"))

            onProgress("Test completed")

            val result = SystemTestResult(
                success = testResults.all { it.passed },
                steps = testResults,
                timestamp = System.currentTimeMillis(),
                summary = "Completed ${testResults.size} tests, ${testResults.count { it.passed }} passed"
            )

            Log.i(TAG, "✅ System test completed: $result")
            onResult(result)

        } catch (e: Exception) {
            Log.e(TAG, "❌ System test failed", e)
            testResults.add(TestStep("Test Execution", false, "Error: ${e.message}"))

            onResult(SystemTestResult(
                success = false,
                steps = testResults,
                timestamp = System.currentTimeMillis(),
                summary = "Test failed: ${e.message}"
            ))
        }
    }

    /**
     * ✅ Monitor voice detection health continuously
     */
    fun startHealthMonitoring(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        intervalMs: Long = 10000,
        onHealthUpdate: (VoiceDetectionHealthReport) -> Unit
    ) {
        var latestReport: VoiceDetectionHealthReport? = null

        val receiver = registerDiagnosticListener(context, lifecycleOwner,
            onHealthReport = { report ->
                latestReport = report
                onHealthUpdate(report)
            }
        )

        lifecycleOwner.lifecycleScope.launch {
            while (true) {
                try {
                    requestDiagnosticReport(context)
                    delay(intervalMs)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in health monitoring", e)
                    delay(intervalMs * 2) // Back off on error
                }
            }
        }

        Log.i(TAG, "🔄 Health monitoring started (interval: ${intervalMs}ms)")
    }

    /**
     * ✅ Generate detailed diagnostic report
     */
    fun generateDiagnosticReport(
        healthReport: VoiceDetectionHealthReport?,
        audioReport: AudioDiagnosticResult?
    ): String {
        val builder = StringBuilder()

        builder.appendLine("🔬 VOICE DETECTION DIAGNOSTIC REPORT")
        builder.appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
        builder.appendLine(DIVIDER)
        //builder.appendLine("=".repeat(50))

        // Health Report Section
        builder.appendLine("\n📊 SYSTEM HEALTH:")
        if (healthReport != null) {
            builder.appendLine("   Status: ${if (healthReport.isHealthy) "✅ Healthy" else "❌ Unhealthy"}")
            builder.appendLine("   Service Active: ${healthReport.isServiceActive}")
            builder.appendLine("   Listening: ${healthReport.isListening}")
            builder.appendLine("   Initialized: ${healthReport.isInitialized}")
            builder.appendLine("   Speech Recognizer: ${healthReport.hasSpeechRecognizer}")
            builder.appendLine("   Keywords: ${healthReport.keywords.joinToString(", ")}")
            builder.appendLine("   Sensitivity: ${healthReport.sensitivity}")
            builder.appendLine("   Consecutive Failures: ${healthReport.consecutiveFailures}")
            builder.appendLine("   Audio Detected: ${healthReport.audioDetectedThisSession}")
            builder.appendLine("   Time Since Audio: ${healthReport.timeSinceLastAudio / 1000}s")
            builder.appendLine("   Functional Tests: P${healthReport.functionalTestsPassed}/F${healthReport.functionalTestsFailed}")
        } else {
            builder.appendLine("   ❌ No health report available")
        }

        // Audio Report Section
        builder.appendLine("\n🔊 AUDIO DIAGNOSTICS:")
        if (audioReport != null) {
            builder.appendLine("   Audio Permission: ${audioReport.audioPermission}")
            builder.appendLine("   Audio Mode: ${audioReport.audioMode}")
            builder.appendLine("   Microphone Muted: ${audioReport.microphoneMuted}")
            builder.appendLine("   Recognition Available: ${audioReport.recognitionAvailable}")
            builder.appendLine("   Test Recognizer: ${audioReport.testRecognizerCreation}")
            builder.appendLine("   Active Recordings: ${audioReport.activeRecordings}")
        } else {
            builder.appendLine("   ❌ No audio diagnostics available")
        }

        // Recommendations
        builder.appendLine("\n💡 RECOMMENDATIONS:")
        val issues = mutableListOf<String>()

        healthReport?.let { health ->
            if (!health.isHealthy) issues.add("System unhealthy - check service status")
            if (!health.isListening) issues.add("Not listening - may need restart")
            if (health.consecutiveFailures >= 3) issues.add("High failure rate - check audio setup")
            if (health.timeSinceLastAudio > 60000) issues.add("No audio input detected - test microphone")
            if (health.functionalTestsFailed > health.functionalTestsPassed) issues.add("Functional tests failing - check system resources")
        }

        audioReport?.let { audio ->
            if (!audio.audioPermission) issues.add("Grant audio recording permission")
            if (audio.microphoneMuted) issues.add("Unmute microphone")
            if (!audio.recognitionAvailable) issues.add("Speech recognition not available on device")
            if (audio.activeRecordings > 1) issues.add("Multiple apps using microphone - may cause conflicts")
        }

        if (issues.isEmpty()) {
            builder.appendLine("   ✅ No issues detected")
        } else {
            issues.forEach { issue ->
                builder.appendLine("   ⚠️ $issue")
            }
        }

        builder.appendLine(DIVIDER)
        //builder.appendLine("=".repeat(50))

        return builder.toString()
    }
}

// ================================================
// DATA CLASSES FOR DIAGNOSTIC RESULTS
// ================================================

data class VoiceDetectionHealthReport(
    val isHealthy: Boolean,
    val isListening: Boolean,
    val isServiceActive: Boolean,
    val isInitialized: Boolean,
    val hasSpeechRecognizer: Boolean,
    val keywords: Array<String>,
    val sensitivity: Float,
    val restartAttempts: Int,
    val consecutiveFailures: Int,
    val lastAudioInput: Long,
    val audioDetectedThisSession: Boolean,
    val timeSinceLastAudio: Long,
    val recognitionEventCount: Int,
    val diagnosticEventCount: Int,
    val functionalTestsPassed: Int,
    val functionalTestsFailed: Int,
    val diagnosticMode: Boolean,
    val timestamp: Long
) {
    companion object {
        fun fromIntent(intent: Intent): VoiceDetectionHealthReport {
            return VoiceDetectionHealthReport(
                isHealthy = intent.getBooleanExtra("is_healthy", false),
                isListening = intent.getBooleanExtra("is_listening", false),
                isServiceActive = intent.getBooleanExtra("is_service_active", false),
                isInitialized = intent.getBooleanExtra("is_initialized", false),
                hasSpeechRecognizer = intent.getBooleanExtra("has_speech_recognizer", false),
                keywords = intent.getStringArrayExtra("keywords") ?: emptyArray(),
                sensitivity = intent.getFloatExtra("sensitivity", 0.8f),
                restartAttempts = intent.getIntExtra("restart_attempts", 0),
                consecutiveFailures = intent.getIntExtra("consecutive_failures", 0),
                lastAudioInput = intent.getLongExtra("last_audio_input", 0),
                audioDetectedThisSession = intent.getBooleanExtra("audio_detected_this_session", false),
                timeSinceLastAudio = intent.getLongExtra("time_since_last_audio", 0),
                recognitionEventCount = intent.getIntExtra("recognition_event_count", 0),
                diagnosticEventCount = intent.getIntExtra("diagnostic_event_count", 0),
                functionalTestsPassed = intent.getIntExtra("functional_tests_passed", 0),
                functionalTestsFailed = intent.getIntExtra("functional_tests_failed", 0),
                diagnosticMode = intent.getBooleanExtra("diagnostic_mode", false),
                timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceDetectionHealthReport
        return keywords.contentEquals(other.keywords) &&
                isHealthy == other.isHealthy &&
                timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        return keywords.contentHashCode() + isHealthy.hashCode() + timestamp.hashCode()
    }

    fun getHealthSummary(): String {
        return when {
            !isServiceActive -> "Service not active"
            !isInitialized -> "Not initialized"
            !isListening -> "Not listening"
            consecutiveFailures >= 5 -> "High failure rate"
            timeSinceLastAudio > 120000 -> "No recent audio"
            else -> "Healthy"
        }
    }
}

data class AudioDiagnosticResult(
    val audioPermission: Boolean,
    val audioMode: Int,
    val microphoneMuted: Boolean,
    val recognitionAvailable: Boolean,
    val testRecognizerCreation: Boolean,
    val activeRecordings: Int,
    val consecutiveFailures: Int,
    val lastAudioInput: Long,
    val timestamp: Long
) {
    companion object {
        fun fromIntent(intent: Intent): AudioDiagnosticResult {
            val data = intent.getBundleExtra("diagnostic_data")
            return AudioDiagnosticResult(
                audioPermission = data?.getBoolean("audio_permission") ?: false,
                audioMode = data?.getInt("audio_mode") ?: 0,
                microphoneMuted = data?.getBoolean("microphone_muted") ?: false,
                recognitionAvailable = data?.getBoolean("recognition_available") ?: false,
                testRecognizerCreation = data?.getBoolean("test_recognizer_creation") ?: false,
                activeRecordings = data?.getInt("active_recordings") ?: 0,
                consecutiveFailures = data?.getInt("consecutive_failures") ?: 0,
                lastAudioInput = data?.getLong("last_audio_input") ?: 0,
                timestamp = data?.getLong("timestamp") ?: System.currentTimeMillis()
            )
        }
    }
}

data class VoiceTriggerEvent(
    val keyword: String,
    val fullText: String,
    val confidence: Float,
    val timestamp: Long
) {
    companion object {
        fun fromIntent(intent: Intent): VoiceTriggerEvent {
            return VoiceTriggerEvent(
                keyword = intent.getStringExtra("keyword") ?: "",
                fullText = intent.getStringExtra("full_text") ?: "",
                confidence = intent.getFloatExtra("confidence", 0.0f),
                timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            )
        }
    }

    fun getFormattedTime(): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}

data class TestStep(
    val name: String,
    val passed: Boolean,
    val details: String
)

data class SystemTestResult(
    val success: Boolean,
    val steps: List<TestStep>,
    val timestamp: Long,
    val summary: String
) {
    fun getPassedCount(): Int = steps.count { it.passed }
    fun getTotalCount(): Int = steps.size
    fun getSuccessRate(): Float = if (steps.isEmpty()) 0f else getPassedCount().toFloat() / getTotalCount()
}

// ================================================
// ADB TESTING COMMANDS
// ================================================

/**
 * ✅ ADB Commands for Testing Voice Detection
 *
 * Copy these commands to test your voice detection system:
 *
 * # Request diagnostic report
 * adb shell am broadcast -a com.safeguardme.VOICE_DETECTION_DIAGNOSTIC_REQUEST
 *
 * # Force service restart
 * adb shell am broadcast -a com.safeguardme.VOICE_DETECTION_FORCE_RESTART
 *
 * # Enable diagnostic mode
 * adb shell am broadcast -a com.safeguardme.VOICE_DETECTION_ENABLE_DIAGNOSTICS
 *
 * # Test keyword detection
 * adb shell am broadcast -a com.safeguardme.VOICE_DETECTION_TEST_KEYWORD --es keyword "emergency"
 *
 * # Start voice detection service with diagnostics
 * adb shell am startforegroundservice -n com.safeguardme.app/.services.VoiceDetectionForegroundService \
 *   -a com.safeguardme.START_VOICE_DETECTION --ez enable_diagnostics true
 *
 * # Monitor logs
 * adb logcat -s VoiceDetectionService:* VoiceDiagnosticUtils:*
 *
 * # Check service status
 * adb shell dumpsys activity services com.safeguardme.app
 */