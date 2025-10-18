// ===================================================================
// RECEIVER: VoiceTriggerReceiver.kt (for external apps)
// ===================================================================

// receivers/VoiceTriggerReceiver.kt
package com.safeguardme.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.safeguardme.app.services.SafetyMonitoringService

class VoiceTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "VoiceTriggerReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            "com.safeguardme.VOICE_TRIGGER_DETECTED" -> {
                val keyword = intent.getStringExtra("keyword") ?: ""
                val fullText = intent.getStringExtra("full_text") ?: ""
                val confidence = intent.getFloatExtra("confidence", 0.0f)

                Log.w(TAG, "🔔 Voice trigger received: '$keyword'")

                // Forward to SafetyMonitoringService
                val serviceIntent = Intent(context, SafetyMonitoringService::class.java).apply {
                    action = SafetyMonitoringService.ACTION_VOICE_TRIGGER_DETECTED
                    putExtra("keyword", keyword)
                    putExtra("full_text", fullText)
                    putExtra("confidence", confidence)
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (security: SecurityException) {
                    Log.e(TAG, "❌ Unable to start safety monitoring from voice trigger", security)
                }
            }

            "com.safeguardme.VOICE_DETECTION_HEALTH_REPORT" -> {
                // Handle health reports if needed
                Log.d(TAG, "📊 Voice detection health report received")
            }
        }
    }
}
