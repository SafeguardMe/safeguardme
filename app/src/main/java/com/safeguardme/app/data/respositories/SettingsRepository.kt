// data/repositories/SettingsRepository.kt
package com.safeguardme.app.data.repositories

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SettingsRepository"

        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val ENABLE_SOUNDS_KEY = booleanPreferencesKey("enable_sounds")
        private val ALLOW_OFFLINE_MODE_KEY = booleanPreferencesKey("allow_offline_mode")
        private val HAS_SEEN_ONBOARDING_KEY = booleanPreferencesKey("has_seen_onboarding")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val AUTO_BACKUP_ENABLED_KEY = booleanPreferencesKey("auto_backup_enabled")
        private val EMERGENCY_CONTACTS_ONLY_KEY = booleanPreferencesKey("emergency_contacts_only")
        private val LAST_BACKUP_TIME_KEY = longPreferencesKey("last_backup_time")

        private val VOICE_DETECTION_ENABLED_KEY = booleanPreferencesKey("voice_detection_enabled")
        private val VOICE_DETECTION_KEYWORD_KEY = stringPreferencesKey("voice_detection_keyword")
        private val VOICE_DETECTION_SENSITIVITY_KEY = stringPreferencesKey("voice_detection_sensitivity")
        val VOICE_DETECTION_ENABLED = booleanPreferencesKey("voice_detection_enabled")
        val VOICE_DETECTION_SENSITIVITY = floatPreferencesKey("voice_detection_sensitivity")
        val VOICE_BATTERY_OPTIMIZED = booleanPreferencesKey("voice_battery_optimized")
        val VOICE_DETECTION_HISTORY = booleanPreferencesKey("voice_detection_history")
        val VOICE_BACKGROUND_PROCESSING = booleanPreferencesKey("voice_background_processing")

    }

    // Dark mode setting
    val isDarkModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[DARK_MODE_KEY] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    // Sounds setting
    val isSoundsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ENABLE_SOUNDS_KEY] ?: true }

    suspend fun setSoundsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_SOUNDS_KEY] = enabled
        }
    }

    suspend fun setVoiceDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_DETECTION_ENABLED] = enabled
        }
        Log.d("SettingsRepository", "🔊 Voice detection enabled: $enabled")
    }

    suspend fun setVoiceDetectionKeyword(keyword: String?) {
        Log.d(TAG, "🎯 Setting voice detection keyword: $keyword")
        context.dataStore.edit { preferences ->
            if (keyword != null) {
                preferences[VOICE_DETECTION_KEYWORD_KEY] = keyword
            } else {
                preferences.remove(VOICE_DETECTION_KEYWORD_KEY)
            }
        }
    }

    suspend fun setVoiceDetectionSensitivity(sensitivity: Float) {
        val clampedSensitivity = sensitivity.coerceIn(0.1f, 1.0f)
        context.dataStore.edit { preferences ->
            preferences[VOICE_DETECTION_SENSITIVITY] = clampedSensitivity
        }
        Log.d("SettingsRepository", "🎚️ Voice detection sensitivity: $clampedSensitivity")
    }

    suspend fun setVoiceDetectionHistory(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_DETECTION_HISTORY] = enabled
        }
        Log.d("SettingsRepository", "📊 Voice detection history: $enabled")
    }

    suspend fun setVoiceBackgroundProcessing(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_BACKGROUND_PROCESSING] = enabled
        }
        Log.d("SettingsRepository", "🔄 Voice background processing: $enabled")
    }

    suspend fun setVoiceBatteryOptimized(optimized: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_BATTERY_OPTIMIZED] = optimized
        }
        Log.d("SettingsRepository", "🔋 Voice battery optimized: $optimized")
    }


    suspend fun getVoiceDetectionSettings(): VoiceDetectionSettings {
        val preferences = context.dataStore.data.first()

        return VoiceDetectionSettings(
            enabled = preferences[VOICE_DETECTION_ENABLED] ?: false,
            sensitivity = preferences[VOICE_DETECTION_SENSITIVITY] ?: 0.8f,
            batteryOptimized = preferences[VOICE_BATTERY_OPTIMIZED] ?: true,
            historyEnabled = preferences[VOICE_DETECTION_HISTORY] ?: true,
            backgroundProcessing = preferences[VOICE_BACKGROUND_PROCESSING] ?: true
        )
    }

    val voiceDetectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[VOICE_DETECTION_ENABLED_KEY] ?: false
    }

    val voiceDetectionKeyword: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[VOICE_DETECTION_KEYWORD_KEY]
    }

    /*suspend fun isVoiceDetectionConfigured(): Boolean {
        return try {
            val settings = appSettings.map { it }.kotlinx.coroutines.flow.first()
            settings.voiceDetectionKeyword != null && settings.voiceDetectionKeyword.isNotBlank()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking voice detection configuration", e)
            false
        }
    }*/

    // Offline mode setting
    val isOfflineModeAllowed: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ALLOW_OFFLINE_MODE_KEY] ?: true }

    suspend fun setOfflineModeAllowed(allowed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ALLOW_OFFLINE_MODE_KEY] = allowed
        }
    }

    // Onboarding completion
    val hasSeenOnboarding: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[HAS_SEEN_ONBOARDING_KEY] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_SEEN_ONBOARDING_KEY] = completed
        }
    }

    // Biometric authentication
    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[BIOMETRIC_ENABLED_KEY] ?: false }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }

    // Emergency contacts only mode
    val isEmergencyContactsOnly: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[EMERGENCY_CONTACTS_ONLY_KEY] ?: false }

    suspend fun setEmergencyContactsOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[EMERGENCY_CONTACTS_ONLY_KEY] = enabled
        }
    }

    // Get all settings as a combined flow
    data class AppSettings(
        val darkMode: Boolean = false,
        val soundsEnabled: Boolean = true,
        val offlineModeAllowed: Boolean = true,
        val biometricEnabled: Boolean = false,
        val emergencyContactsOnly: Boolean = false,
        val voiceDetectionKeyword: String? = null,
        val voiceDetectionEnabled: Boolean = false,
        val voiceDetectionSensitivity: Any = 0.8f,
        val voiceBatteryOptimized: Boolean = true,
        val voiceDetectionHistory: Boolean = true,
        val voiceBackgroundProcessing: Boolean = true
    )

    val appSettings: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            AppSettings(
                darkMode = preferences[DARK_MODE_KEY] ?: false,
                soundsEnabled = preferences[ENABLE_SOUNDS_KEY] ?: true,
                offlineModeAllowed = preferences[ALLOW_OFFLINE_MODE_KEY] ?: true,
                biometricEnabled = preferences[BIOMETRIC_ENABLED_KEY] ?: false,
                emergencyContactsOnly = preferences[EMERGENCY_CONTACTS_ONLY_KEY] ?: false,
                voiceDetectionKeyword = preferences[VOICE_DETECTION_KEYWORD_KEY],
                voiceDetectionEnabled = preferences[VOICE_DETECTION_ENABLED] ?: false,
                voiceDetectionSensitivity = preferences[VOICE_DETECTION_SENSITIVITY] ?: 0.8f,
                voiceBatteryOptimized = preferences[VOICE_BATTERY_OPTIMIZED] ?: true,
                voiceDetectionHistory = preferences[VOICE_DETECTION_HISTORY] ?: true,
                voiceBackgroundProcessing = preferences[VOICE_BACKGROUND_PROCESSING] ?: true
            )
        }
}

/**
 * ✅ DATA CLASS: Voice detection specific settings
 */
data class VoiceDetectionSettings(
    val enabled: Boolean = false,
    val sensitivity: Float = 0.8f,
    val batteryOptimized: Boolean = true,
    val historyEnabled: Boolean = true,
    val backgroundProcessing: Boolean = true
) {
    fun getSensitivityPercentage(): Int = (sensitivity * 100).toInt()

    fun getSensitivityDescription(): String {
        return when {
            sensitivity >= 0.9f -> "Very High"
            sensitivity >= 0.7f -> "High"
            sensitivity >= 0.5f -> "Medium"
            sensitivity >= 0.3f -> "Low"
            else -> "Very Low"
        }
    }

    fun isOptimalConfiguration(): Boolean {
        return enabled && sensitivity in 0.6f..0.9f && batteryOptimized
    }

    fun getConfigurationSummary(): String {
        return buildString {
            append("Voice Detection: ${if (enabled) "ON" else "OFF"}")
            if (enabled) {
                append(" | Sensitivity: ${getSensitivityDescription()}")
                append(" | Battery: ${if (batteryOptimized) "Optimized" else "High Performance"}")
            }
        }
    }
}

/**
 * ✅ UTILITY: Voice detection settings validation
 */
object VoiceDetectionSettingsValidator {

    fun validateSensitivity(sensitivity: Float): ValidationResult {
        return when {
            sensitivity < 0.1f -> ValidationResult.Invalid("Sensitivity too low - minimum 0.1")
            sensitivity > 1.0f -> ValidationResult.Invalid("Sensitivity too high - maximum 1.0")
            sensitivity in 0.1f..0.3f -> ValidationResult.Warning("Low sensitivity may miss triggers")
            sensitivity in 0.9f..1.0f -> ValidationResult.Warning("High sensitivity may cause false triggers")
            else -> ValidationResult.Valid
        }
    }

    fun validateConfiguration(settings: VoiceDetectionSettings): List<String> {
        val issues = mutableListOf<String>()

        if (settings.enabled && settings.sensitivity < 0.5f) {
            issues.add("Low sensitivity may result in missed emergency triggers")
        }

        if (settings.enabled && !settings.batteryOptimized) {
            issues.add("High performance mode will drain battery faster")
        }

        if (settings.enabled && !settings.backgroundProcessing) {
            issues.add("Disabled background processing may reduce detection reliability")
        }

        return issues
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Warning(val message: String) : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }
}
