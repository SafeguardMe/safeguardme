// managers/EmergencyContactNotificationManager.kt - SMS Emergency Notifications
package com.safeguardme.app.managers

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.safeguardme.app.data.models.EmergencyContact
import com.safeguardme.app.data.repositories.EmergencyContactRepository
import com.safeguardme.app.data.repositories.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyContactNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val userRepository: UserRepository,
    private val permissionManager: PermissionManager
) {
    companion object {
        private const val TAG = "EmergencyNotificationMgr"
        private const val MAX_SMS_LENGTH = 160
        private const val RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingNotifications = mutableSetOf<String>()

    /**
     * ✅ MAIN: Notify all emergency contacts with critical alert
     */
    suspend fun notifyAllContacts(
        message: String,
        sessionId: String,
        urgencyLevel: EmergencyUrgencyLevel = EmergencyUrgencyLevel.HIGH
    ): Result<Int> {
        return try {
            Log.w(TAG, "🚨 Notifying all emergency contacts - Session: $sessionId")

            if (!permissionManager.isPermissionGranted(AppPermission.SMS_MESSAGING)) {
                Log.w(TAG, "⚠️ SMS permission not granted, cannot send emergency notifications")
                return Result.failure(SecurityException("SMS permission required for emergency notifications"))
            }

            val emergencyContacts = emergencyContactRepository.getEmergencyContacts()
                .getOrElse { emptyList() }

            if (emergencyContacts.isEmpty()) {
                Log.w(TAG, "⚠️ No emergency contacts available for notification")
                return Result.failure(IllegalStateException("No emergency contacts configured"))
            }

            val user = userRepository.getCurrentUser().firstOrNull()
            val userName = user?.fullName?.takeIf { it.isNotEmpty() } ?: "SafeguardMe User"

            val formattedMessage = formatEmergencyMessage(
                message = message,
                userName = userName,
                sessionId = sessionId,
                urgencyLevel = urgencyLevel
            )

            var successCount = 0
            var failureCount = 0

            emergencyContacts.forEach { contact ->
                try {
                    val result = sendSMSToContact(contact, formattedMessage, sessionId)
                    if (result.isSuccess) {
                        successCount++
                        Log.i(TAG, "✅ SMS sent to ${contact.name}: ${contact.phoneNumber}")
                    } else {
                        failureCount++
                        Log.e(TAG, "❌ Failed to send SMS to ${contact.name}: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    failureCount++
                    Log.e(TAG, "❌ Exception sending SMS to ${contact.name}", e)
                }
            }

            Log.i(TAG, "📊 Emergency notification complete: $successCount sent, $failureCount failed")
            Result.success(successCount)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to notify emergency contacts", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ SPECIFIC: Send emergency alert for safety mode activation
     */
    suspend fun sendEmergencyAlert(sessionId: String): Result<Unit> {
        val user = userRepository.getCurrentUser().firstOrNull()
        val userName = user?.fullName ?: "User"

        val message = "🚨 EMERGENCY: $userName has triggered emergency mode. Session: $sessionId. Time: ${getCurrentTimestamp()}"

        return notifyAllContacts(message, sessionId, EmergencyUrgencyLevel.CRITICAL)
            .map { Unit }
    }

    /**
     * ✅ LOCATION: Send location update to emergency contacts
     */
    suspend fun sendLocationUpdate(
        location: android.location.Location,
        sessionId: String
    ): Result<Unit> {
        return try {
            val user = userRepository.getCurrentUser().firstOrNull()
            val userName = user?.fullName ?: "User"

            val latitude = String.format(Locale.US, "%.5f", location.latitude)
            val longitude = String.format(Locale.US, "%.5f", location.longitude)
            val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"

            val locationMessage = buildString {
                append("📍 LOCATION UPDATE: $userName is near $latitude, $longitude. ")
                append("Accuracy: ${location.accuracy}m. Time: ${getCurrentTimestamp()}. Session: $sessionId. ")
                append("Map: $mapsUrl")
            }

            notifyAllContacts(locationMessage, sessionId, EmergencyUrgencyLevel.MEDIUM)
                .map { Unit }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send location update", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ SAFETY MODE: Notify contacts of safety mode activation
     */
    suspend fun sendSafetyModeActivationNotification(sessionId: String): Result<Unit> {
        val user = userRepository.getCurrentUser().firstOrNull()
        val userName = user?.fullName ?: "User"

        val message = "🛡️ SAFETY MODE: $userName has activated safety monitoring. Session: $sessionId. Time: ${getCurrentTimestamp()}"

        return notifyAllContacts(message, sessionId, EmergencyUrgencyLevel.LOW)
            .map { Unit }
    }

    /**
     * ✅ SAFETY MODE: Notify contacts of safety mode deactivation
     */
    suspend fun sendSafetyModeDeactivationNotification(sessionId: String): Result<Unit> {
        val user = userRepository.getCurrentUser().firstOrNull()
        val userName = user?.fullName ?: "User"

        val message = "✅ SAFE: $userName has deactivated safety mode and marked as safe. Session: $sessionId ended at ${getCurrentTimestamp()}"

        return notifyAllContacts(message, sessionId, EmergencyUrgencyLevel.LOW)
            .map { Unit }
    }

    /**
     * ✅ DISTRESS: Send distress alert based on voice detection
     */
    suspend fun sendDistressAlert(
        transcription: String,
        keywords: List<String>
    ): Result<Unit> {
        val user = userRepository.getCurrentUser().firstOrNull()
        val userName = user?.fullName ?: "User"

        val message = "🚨 DISTRESS DETECTED: $userName's device detected distress keywords: ${keywords.joinToString(", ")}. " +
                "Voice: \"$transcription\". Time: ${getCurrentTimestamp()}"

        return notifyAllContacts(message, "distress_${System.currentTimeMillis()}", EmergencyUrgencyLevel.CRITICAL)
            .map { Unit }
    }

    /**
     * ✅ ERROR: Send system error alert
     */
    suspend fun sendSystemErrorAlert(errorMessage: String): Result<Unit> {
        val user = userRepository.getCurrentUser().firstOrNull()
        val userName = user?.fullName ?: "User"

        val message = "⚠️ SYSTEM ERROR: $userName's SafeguardMe app encountered an error: $errorMessage. " +
                "Time: ${getCurrentTimestamp()}"

        return notifyAllContacts(message, "error_${System.currentTimeMillis()}", EmergencyUrgencyLevel.MEDIUM)
            .map { Unit }
    }

    /**
     * ✅ CORE: Send SMS to specific contact with retry logic
     */
    private suspend fun sendSMSToContact(
        contact: EmergencyContact,
        message: String,
        sessionId: String
    ): Result<Unit> {
        return try {
            if (!contact.canReceiveEmergencyNotifications()) {
                return Result.failure(IllegalStateException("Contact ${contact.name} cannot receive emergency notifications"))
            }

            val smsManager = SmsManager.getDefault()
            val phoneNumber = contact.phoneNumber

            // Split long messages
            val parts = if (message.length > MAX_SMS_LENGTH) {
                smsManager.divideMessage(message)
            } else {
                arrayListOf(message)
            }

            Log.d(TAG, "📱 Sending SMS to ${contact.name} (${phoneNumber}): ${parts.size} parts")

            // Send message parts
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, parts[0], null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }

            // Update contact's last contacted time
            emergencyContactRepository.updateContactVerification(contact.id, true)

            Log.i(TAG, "✅ SMS sent successfully to ${contact.name}")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send SMS to ${contact.name}: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ FORMATTING: Format emergency message with consistent structure
     */
    private fun formatEmergencyMessage(
        message: String,
        userName: String,
        sessionId: String,
        urgencyLevel: EmergencyUrgencyLevel
    ): String {
        val urgencyIcon = when (urgencyLevel) {
            EmergencyUrgencyLevel.CRITICAL -> "🚨🚨🚨"
            EmergencyUrgencyLevel.HIGH -> "🚨"
            EmergencyUrgencyLevel.MEDIUM -> "⚠️"
            EmergencyUrgencyLevel.LOW -> "ℹ️"
        }

        val timestamp = getCurrentTimestamp()

        return "$urgencyIcon $message\n\nSession: $sessionId\nTime: $timestamp\n\nSent by SafeguardMe"
    }

    /**
     * ✅ HELPER: Get current timestamp in readable format
     */
    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    /**
     * ✅ STATUS: Get emergency contact readiness status
     */
    suspend fun getEmergencyContactStatus(): EmergencyContactStatus {
        return try {
            val contacts = emergencyContactRepository.getAllContacts().getOrElse { emptyList() }
            val emergencyReady = contacts.filter { it.canReceiveEmergencyNotifications() }
            val hasSMSPermission = permissionManager.isPermissionGranted(AppPermission.SMS_MESSAGING)

            EmergencyContactStatus(
                totalContacts = contacts.size,
                emergencyReadyContacts = emergencyReady.size,
                hasSMSPermission = hasSMSPermission,
                highPriorityContacts = contacts.count { it.isHighPriority() && it.canReceiveEmergencyNotifications() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting emergency contact status", e)
            EmergencyContactStatus()
        }
    }

    /**
     * ✅ VALIDATION: Check if emergency notification system is ready
     */
    suspend fun isEmergencyNotificationReady(): Boolean {
        val status = getEmergencyContactStatus()
        return status.isReady
    }

    /**
     * ✅ TEST: Send test message to verify SMS functionality
     */
    suspend fun sendTestMessage(contactId: String): Result<Unit> {
        return try {
            val contacts = emergencyContactRepository.getAllContacts().getOrElse { emptyList() }
            val contact = contacts.find { it.id == contactId }
                ?: return Result.failure(IllegalArgumentException("Contact not found"))

            val user = userRepository.getCurrentUser().firstOrNull()
            val userName = user?.fullName ?: "User"

            val testMessage = "📱 TEST: This is a test message from $userName's SafeguardMe app. " +
                    "Your contact information is working correctly. Time: ${getCurrentTimestamp()}"

            sendSMSToContact(contact, testMessage, "test_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send test message", e)
            Result.failure(e)
        }
    }
}

/**
 * ✅ DATA: Emergency contact status information
 */
data class EmergencyContactStatus(
    val totalContacts: Int = 0,
    val emergencyReadyContacts: Int = 0,
    val hasSMSPermission: Boolean = false,
    val highPriorityContacts: Int = 0
) {
    val isReady: Boolean
        get() = emergencyReadyContacts >= 2 && hasSMSPermission

    fun getReadinessMessage(): String {
        return when {
            !hasSMSPermission -> "SMS permission required"
            emergencyReadyContacts == 0 -> "No emergency contacts configured"
            emergencyReadyContacts < 2 -> "Need at least 2 emergency contacts"
            else -> "Emergency notification system ready"
        }
    }

    fun getReadinessPercentage(): Int {
        val maxScore = 4 // SMS permission + 2 contacts + high priority contacts
        var currentScore = 0

        if (hasSMSPermission) currentScore++
        if (emergencyReadyContacts >= 1) currentScore++
        if (emergencyReadyContacts >= 2) currentScore++
        if (highPriorityContacts >= 1) currentScore++

        return (currentScore * 100) / maxScore
    }
}

/**
 * ✅ ENUM: Emergency urgency levels for message prioritization
 */
enum class EmergencyUrgencyLevel {
    LOW,        // Safety mode activation/deactivation
    MEDIUM,     // Location updates, system errors
    HIGH,       // Manual emergency activation
    CRITICAL    // Voice distress detection, immediate danger
}
