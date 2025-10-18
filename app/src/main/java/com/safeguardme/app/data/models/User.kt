// data/models/User.kt - FIXED for Firebase Compatibility
package com.safeguardme.app.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * ✅ FIXED: Added @IgnoreExtraProperties to handle unknown fields gracefully
 * ✅ FIXED: Made timestamps flexible to handle both Long and Timestamp types
 * ✅ FIXED: Added missing fields that Firebase was complaining about
 */
@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val email: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val profilePhotoUrl: String? = null,
    val isEmailVerified: Boolean = false,

    // ✅ FIXED: Add emailVerified field that Firebase was looking for
    val emailVerified: Boolean = false, // Map to isEmailVerified

    val lastLoginAt: Long = 0L,
    val isActive: Boolean = true,

    // ✅ FIXED: Add valid field that Firebase was looking for
    val valid: Boolean = true,

    val triggerKeyword: String? = null,
    val voiceAudioUrl: String? = null,
    val transcriptionData: Map<String, Any>? = null,
    val triggerUpdatedAt: Long? = null,
    val triggerDeletedAt: Long? = null,

    // ✅ FIXED: Add audioFileSize that Firebase was looking for
    val audioFileSize: Long? = null,

    // ✅ FIXED: Add profileSecurityLevel that Firebase was looking for
    val profileSecurityLevel: String? = null,

    // ✅ CRITICAL FIX: Use nullable Any to handle both Long and Timestamp
    // Firebase will automatically convert between them
    val createdAt: Any? = null, // Can be Long or Timestamp
    val lastActiveAt: Any? = null, // Can be Long or Timestamp

    // Safety-specific fields
    val safetyStatus: SafetyStatus = SafetyStatus.DISABLED,
    val emergencyContacts: List<String> = emptyList(), // Contact IDs

    // Additional profile fields
    val isProfileComplete: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),

    // Emergency settings
    val emergencySettings: EmergencySettings = EmergencySettings()
) {
    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_PHONE_LENGTH = 20
        const val MAX_TRIGGER_KEYWORD_LENGTH = 50
        const val MIN_TRIGGER_KEYWORD_LENGTH = 3
    }

    /**
     * ✅ NEW: Safe timestamp conversion helpers
     */
    @get:Exclude
    val createdAtTimestamp: Timestamp?
        get() = when (createdAt) {
            is Timestamp -> createdAt
            is Long -> if (createdAt > 0) Timestamp(createdAt / 1000, ((createdAt % 1000) * 1000000).toInt()) else null
            is Number -> Timestamp(createdAt.toLong() / 1000, ((createdAt.toLong() % 1000) * 1000000).toInt())
            else -> null
        }

    @get:Exclude
    val lastActiveAtTimestamp: Timestamp?
        get() = when (lastActiveAt) {
            is Timestamp -> lastActiveAt
            is Long -> if (lastActiveAt > 0) Timestamp(lastActiveAt / 1000, ((lastActiveAt % 1000) * 1000000).toInt()) else null
            is Number -> Timestamp(lastActiveAt.toLong() / 1000, ((lastActiveAt.toLong() % 1000) * 1000000).toInt())
            else -> null
        }

    @get:Exclude
    val createdAtMillis: Long
        get() = when (createdAt) {
            is Timestamp -> createdAt.toDate().time
            is Long -> createdAt
            is Number -> createdAt.toLong()
            else -> 0L
        }

    @get:Exclude
    val lastActiveAtMillis: Long
        get() = when (lastActiveAt) {
            is Timestamp -> lastActiveAt.toDate().time
            is Long -> lastActiveAt
            is Number -> lastActiveAt.toLong()
            else -> 0L
        }

    /**
     * ✅ FIXED: Use emailVerified as fallback for isEmailVerified
     */
    @get:Exclude
    val effectiveEmailVerified: Boolean
        get() = isEmailVerified || emailVerified

    /**
     * ✅ NEW: Check if user has configured voice trigger
     */
    fun hasVoiceTrigger(): Boolean {
        return !triggerKeyword.isNullOrBlank() && !voiceAudioUrl.isNullOrBlank()
    }

    /**
     * ✅ NEW: Get voice trigger summary
     */
    fun getVoiceTriggerSummary(): String {
        return when {
            triggerKeyword.isNullOrBlank() -> "No voice trigger configured"
            voiceAudioUrl.isNullOrBlank() -> "Keyword set: '$triggerKeyword' (no audio)"
            else -> "Voice trigger active: '$triggerKeyword'"
        }
    }

    /**
     * ✅ NEW: Check if voice trigger is verified via transcription
     */
    fun isVoiceTriggerVerified(): Boolean {
        val matchData = transcriptionData?.get("matchResult") as? Map<String, Any>
        return matchData?.get("isMatch") as? Boolean == true
    }

    /**
     * ✅ FIXED: Updated validation to handle nullable triggerKeyword properly
     */
    fun isValid(): Boolean {
        return uid.isNotBlank() &&
                email.isNotBlank() &&
                fullName.isNotBlank() &&
                fullName.length <= MAX_NAME_LENGTH &&
                (triggerKeyword?.length ?: 0) >= MIN_TRIGGER_KEYWORD_LENGTH &&
                (triggerKeyword?.length ?: 0) <= MAX_TRIGGER_KEYWORD_LENGTH
    }

    /**
     * ✅ FIXED: Enhanced sanitization
     */
    fun sanitized(): User {
        return this.copy(
            email = email.trim().lowercase(),
            fullName = fullName.trim(),
            phoneNumber = phoneNumber.trim(),
            triggerKeyword = triggerKeyword?.trim()?.lowercase(),
            emailVerified = effectiveEmailVerified, // Sync the fields
            valid = isValid()
        )
    }

    /**
     * Check if user profile is complete for emergency use
     */
    fun isEmergencyReady(): Boolean {
        return isValid() &&
                phoneNumber.isNotBlank() &&
                emergencyContacts.isNotEmpty() &&
                hasCompletedOnboarding
    }

    /**
     * Get user's first name for display
     */
    fun getFirstName(): String {
        return fullName.split(" ").firstOrNull()?.trim() ?: "User"
    }

    /**
     * Get display name with fallback
     */
    fun getDisplayName(): String {
        return when {
            fullName.isNotBlank() -> fullName
            email.isNotBlank() -> email.substringBefore("@")
            else -> "User"
        }
    }
}

/**
 * Safety status enum
 */
enum class SafetyStatus {
    DISABLED,
    ENABLED,
    EMERGENCY
}

/**
 * Notification preferences for emergency app
 */
data class NotificationPreferences(
    val enablePushNotifications: Boolean = true,
    val enableSMSAlerts: Boolean = true,
    val enableEmailAlerts: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val emergencyAlertsAlwaysEnabled: Boolean = true // Never silence emergency alerts
)

/**
 * Emergency-specific settings
 */
data class EmergencySettings(
    val autoLocationSharing: Boolean = true,
    val emergencyCallEnabled: Boolean = true,
    val panicButtonEnabled: Boolean = true,
    val voiceActivationEnabled: Boolean = false,
    val emergencyRecordingEnabled: Boolean = false,
    val automaticCheck: Boolean = true,
    val checkInFrequencyMinutes: Int = 60,
    val maxResponseTimeMinutes: Int = 5,
    val volumeGestureEnabled: Boolean = true,
    val shakeGestureEnabled: Boolean = true,
    val powerGestureEnabled: Boolean = false
)

/**
 * ✅ FIXED: User creation helper with proper timestamp handling
 */
object UserFactory {

    /**
     * Create user from Firebase Auth with proper timestamp handling
     */
    fun fromFirebaseAuth(
        uid: String,
        email: String,
        displayName: String? = null,
        photoUrl: String? = null,
        isEmailVerified: Boolean = false
    ): User {
        return User(
            uid = uid,
            email = email,
            fullName = displayName ?: "",
            profilePhotoUrl = photoUrl,
            isEmailVerified = isEmailVerified,
            emailVerified = isEmailVerified, // Sync both fields
            // Use current timestamp in millis for compatibility
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis(),
            valid = true
        )
    }

    /**
     * Create user for manual registration
     */
    fun createNew(
        uid: String,
        email: String,
        fullName: String,
        phoneNumber: String = ""
    ): User {
        val currentTime = System.currentTimeMillis()
        return User(
            uid = uid,
            email = email,
            fullName = fullName,
            phoneNumber = phoneNumber,
            createdAt = currentTime,
            lastActiveAt = currentTime,
            valid = true
        ).sanitized()
    }

    /**
     * Create user with emergency defaults
     */
    fun createEmergencyUser(
        uid: String,
        email: String,
        fullName: String,
        phoneNumber: String,
        triggerKeyword: String = "help me"
    ): User {
        val currentTime = System.currentTimeMillis()
        return User(
            uid = uid,
            email = email,
            fullName = fullName,
            phoneNumber = phoneNumber,
            triggerKeyword = triggerKeyword,
            emergencySettings = EmergencySettings(
                autoLocationSharing = true,
                emergencyCallEnabled = true,
                panicButtonEnabled = true
            ),
            createdAt = currentTime,
            lastActiveAt = currentTime,
            valid = true
        ).sanitized()
    }
}

/**
 * Extension functions for User operations
 */

/**
 * ✅ FIXED: Update last active timestamp helper
 */
fun User.withUpdatedActivity(): User {
    return this.copy(lastActiveAt = System.currentTimeMillis())
}

/**
 * Toggle safety status helper
 */
fun User.toggleSafetyStatus(): User {
    val newStatus = when (safetyStatus) {
        SafetyStatus.DISABLED -> SafetyStatus.ENABLED
        SafetyStatus.ENABLED -> SafetyStatus.DISABLED
        SafetyStatus.EMERGENCY -> SafetyStatus.DISABLED
    }
    return this.copy(safetyStatus = newStatus)
}

/**
 * Add emergency contact helper
 */
fun User.addEmergencyContact(contactId: String): User {
    return if (!emergencyContacts.contains(contactId)) {
        this.copy(emergencyContacts = emergencyContacts + contactId)
    } else {
        this
    }
}

/**
 * Remove emergency contact helper
 */
fun User.removeEmergencyContact(contactId: String): User {
    return this.copy(emergencyContacts = emergencyContacts - contactId)
}

/**
 * Complete onboarding helper
 */
fun User.completeOnboarding(): User {
    return this.copy(
        hasCompletedOnboarding = true,
        isProfileComplete = isValid() && phoneNumber.isNotBlank()
    )
}

enum class SecurityLevel {
    STANDARD,
    HIGH_SECURITY,
    MAXIMUM_PROTECTION
}
