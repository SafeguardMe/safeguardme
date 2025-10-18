// utils/ServiceCommunication.kt - Inter-Service Communication
package com.safeguardme.app.utils

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ✅ Service Communication Utility
 * Handles communication between VoiceDetectionForegroundService and SafetyMonitoringService
 */
object ServiceCommunication {
    private const val TAG = "ServiceCommunication"

    /**
     * Send voice trigger detected event from VoiceDetectionForegroundService
     * to SafetyMonitoringService
     */
    fun notifyVoiceTriggerDetected(
        context: Context,
        keyword: String,
        fullText: String,
        confidence: Float
    ) {
        try {
            Log.i(TAG, "🔗 Sending voice trigger to SafetyMonitoringService: '$keyword'")

            // Broadcast to SafetyMonitoringService
            val broadcastIntent = Intent("com.safeguardme.VOICE_TRIGGER_DETECTED").apply {
                putExtra("keyword", keyword)
                putExtra("full_text", fullText)
                putExtra("confidence", confidence)
                putExtra("timestamp", System.currentTimeMillis())
                setPackage(context.packageName) // Keep it internal
            }

            context.sendBroadcast(broadcastIntent)

            // Also send directly to SafetyMonitoringService if it's running
            val serviceIntent = Intent(context, com.safeguardme.app.services.SafetyMonitoringService::class.java).apply {
                action = "VOICE_TRIGGER_DETECTED"
                putExtra("keyword", keyword)
                putExtra("full_text", fullText)
                putExtra("confidence", confidence)
                putExtra("timestamp", System.currentTimeMillis())
            }

            context.startService(serviceIntent)

            Log.d(TAG, "✅ Voice trigger notification sent successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending voice trigger notification", e)
        }
    }

    /**
     * Send emergency escalation request to SafetyMonitoringService
     */
    fun requestEmergencyEscalation(
        context: Context,
        trigger: String,
        details: String? = null
    ) {
        try {
            Log.w(TAG, "🚨 Requesting emergency escalation: $trigger")

            val intent = Intent(context, com.safeguardme.app.services.SafetyMonitoringService::class.java).apply {
                action = "EMERGENCY_ESCALATION"
                putExtra("trigger", trigger)
                putExtra("details", details)
                putExtra("timestamp", System.currentTimeMillis())
            }

            context.startService(intent)

            Log.w(TAG, "🚨 Emergency escalation request sent")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting emergency escalation", e)
        }
    }

    /**
     * Check if SafetyMonitoringService is running
     */
    fun isSafetyMonitoringServiceRunning(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)

            runningServices.any {
                it.service.className == com.safeguardme.app.services.SafetyMonitoringService::class.java.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking SafetyMonitoringService status", e)
            false
        }
    }

    /**
     * Start SafetyMonitoringService if it's not running
     */
    fun ensureSafetyMonitoringServiceRunning(context: Context) {
        if (!isSafetyMonitoringServiceRunning(context)) {
            Log.i(TAG, "🛡️ Starting SafetyMonitoringService")
            com.safeguardme.app.services.SafetyMonitoringService.startMonitoring(context)
        }
    }
}