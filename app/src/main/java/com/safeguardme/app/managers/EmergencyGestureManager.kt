package com.safeguardme.app.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Monitors device gestures (shake and volume button patterns) and triggers emergency mode when
 * configured gestures are detected. Runs as a singleton so it can operate even when no UI screen is
 * visible, relying on shared preferences for feature flags and SafetyManager for escalation.
 */
@Singleton
class EmergencyGestureManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val safetyManager: SafetyManager
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sensorManager: SensorManager? =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gesturePrefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in observedPreferenceKeys) {
                applyCurrentConfiguration()
            }
        }

    private var gesturesEnabled = true
    private var shakeDetectionEnabled = true
    private var volumeDetectionEnabled = true
    private var powerDetectionEnabled = false

    private var monitoringActive = false
    private var volumeReceiverRegistered = false
    private var powerReceiverRegistered = false

    private var lastVolumeLevel: Int = -1
    private val volumeTimestamps = ArrayDeque<Long>()
    private var lastVolumeTrigger = 0L

    private var shakeCount = 0
    private var lastShakeTimestamp = 0L
    private var shakeWindowStart = 0L
    private var maxShakeIntensity = 0f
    private var lastShakeTrigger = 0L

    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            handleVolumeChange()
        }
    }

    private val powerTimestamps = ArrayDeque<Long>()
    private var lastPowerTrigger = 0L

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!monitoringActive || !powerDetectionEnabled) return
            val action = intent?.action ?: return
            val now = System.currentTimeMillis()

            if (action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_SCREEN_ON) {
                powerTimestamps.addLast(now)
                while (powerTimestamps.isNotEmpty() && now - powerTimestamps.first() > POWER_WINDOW_MS) {
                    powerTimestamps.removeFirst()
                }

                if (powerTimestamps.size >= POWER_REQUIRED_COUNT && (now - lastPowerTrigger) > GESTURE_COOLDOWN_MS) {
                    lastPowerTrigger = now
                    powerTimestamps.clear()
                    Log.w(TAG, "🚨 Power button gesture detected (x$POWER_REQUIRED_COUNT)")
                    triggerEmergency("Power Button (${POWER_REQUIRED_COUNT}x)")
                }
            }
        }
    }

    init {
        gesturePrefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        applyCurrentConfiguration()
    }

    private fun applyCurrentConfiguration() {
        gesturesEnabled = gesturePrefs.getBoolean(KEY_GESTURES_ENABLED, true)
        shakeDetectionEnabled = gesturesEnabled &&
            gesturePrefs.getBoolean(KEY_SHAKE_ENABLED, true)
        volumeDetectionEnabled = gesturesEnabled &&
            gesturePrefs.getBoolean(KEY_VOLUME_ENABLED, true)
        powerDetectionEnabled = gesturesEnabled &&
            gesturePrefs.getBoolean(KEY_POWER_ENABLED, false)

        Log.d(TAG, "🎭 Gesture config -> enabled: $gesturesEnabled, shake: $shakeDetectionEnabled, volume: $volumeDetectionEnabled, power: $powerDetectionEnabled")

        if (!gesturesEnabled || (!shakeDetectionEnabled && !volumeDetectionEnabled && !powerDetectionEnabled)) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        if (monitoringActive) {
            updateSensorRegistrations()
            return
        }

        monitoringActive = true
        updateSensorRegistrations()
        Log.d(TAG, "🛰️ Emergency gestures monitoring active")
    }

    private fun stopMonitoring() {
        if (!monitoringActive) return

        monitoringActive = false
        shakeCount = 0
        volumeTimestamps.clear()
        unregisterSensors()
        Log.d(TAG, "🛑 Emergency gestures monitoring stopped")
    }

    private fun updateSensorRegistrations() {
        if (!monitoringActive) return

        if (shakeDetectionEnabled && accelerometer != null) {
            sensorManager?.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )
        } else {
            sensorManager?.unregisterListener(this, accelerometer)
        }

        if (volumeDetectionEnabled) registerVolumeObserver() else unregisterVolumeObserver()
        if (powerDetectionEnabled) registerPowerReceiver() else unregisterPowerReceiver()
    }

    private fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
        unregisterVolumeObserver()
        unregisterPowerReceiver()
    }
    private fun registerVolumeObserver() {
        if (volumeReceiverRegistered) return
        runCatching {
            appContext.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver
            )
            volumeReceiverRegistered = true
            volumeTimestamps.clear()
            lastVolumeLevel = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
            Log.d(TAG, "🔊 Volume observer registered")
        }.onFailure { error ->
            Log.e(TAG, "❌ Failed to register volume observer", error)
        }
    }

    private fun unregisterVolumeObserver() {
        if (!volumeReceiverRegistered) return
        runCatching {
            appContext.contentResolver.unregisterContentObserver(volumeObserver)
            volumeReceiverRegistered = false
            volumeTimestamps.clear()
            Log.d(TAG, "🔇 Volume observer unregistered")
        }.onFailure { error ->
            Log.e(TAG, "❌ Failed to unregister volume observer", error)
        }
    }

    private fun handleVolumeChange() {
        if (!monitoringActive || !volumeDetectionEnabled) return
        val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
        if (lastVolumeLevel >= 0 && currentVolume == lastVolumeLevel) return
        if (lastVolumeLevel >= 0 && currentVolume > lastVolumeLevel) {
            trackVolumeIncrease(System.currentTimeMillis())
        }
        lastVolumeLevel = currentVolume
    }

    private fun registerPowerReceiver() {
        if (powerReceiverRegistered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(powerReceiver, filter)
            }
            powerReceiverRegistered = true
            powerTimestamps.clear()
            Log.d(TAG, "⚡ Power receiver registered")
        }.onFailure { error ->
            Log.e(TAG, "❌ Failed to register power receiver", error)
        }
    }

    private fun unregisterPowerReceiver() {
        if (!powerReceiverRegistered) return
        runCatching {
            appContext.unregisterReceiver(powerReceiver)
            powerReceiverRegistered = false
            powerTimestamps.clear()
            Log.d(TAG, "⚡ Power receiver unregistered")
        }.onFailure { error ->
            Log.e(TAG, "❌ Failed to unregister power receiver", error)
        }
    }

    private fun trackVolumeIncrease(timestamp: Long) {
        volumeTimestamps.addLast(timestamp)
        while (volumeTimestamps.isNotEmpty() && timestamp - volumeTimestamps.first() > VOLUME_WINDOW_MS) {
            volumeTimestamps.removeFirst()
        }

        if (volumeTimestamps.size >= VOLUME_REQUIRED_COUNT && (timestamp - lastVolumeTrigger) > GESTURE_COOLDOWN_MS) {
            lastVolumeTrigger = timestamp
            volumeTimestamps.clear()
            Log.w(TAG, "🚨 Volume gesture detected (x$VOLUME_REQUIRED_COUNT)")
            triggerEmergency("Volume Buttons (3x)")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!monitoringActive || !shakeDetectionEnabled) return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val acceleration = calculateAcceleration(event.values)
        val now = System.currentTimeMillis()

        if (acceleration > SHAKE_THRESHOLD) {
            if (shakeCount == 0 || now - lastShakeTimestamp > SHAKE_RESET_MS) {
                shakeCount = 1
                shakeWindowStart = now
                maxShakeIntensity = acceleration
            } else {
                shakeCount += 1
                maxShakeIntensity = max(maxShakeIntensity, acceleration)
            }
            lastShakeTimestamp = now

            if (shakeCount >= SHAKE_REQUIRED_COUNT && (now - lastShakeTrigger) > GESTURE_COOLDOWN_MS) {
                lastShakeTrigger = now
                val duration = now - shakeWindowStart
                Log.w(TAG, "🚨 Shake gesture detected (count=$shakeCount, intensity=${String.format("%.2f", maxShakeIntensity)})")
                triggerEmergency("Phone Shake")
                resetShakeTracking()
            }
        } else if (shakeCount > 0 && now - lastShakeTimestamp > SHAKE_RESET_MS) {
            resetShakeTracking()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not required
    }

    private fun resetShakeTracking() {
        shakeCount = 0
        maxShakeIntensity = 0f
        shakeWindowStart = 0L
    }

    private fun calculateAcceleration(values: FloatArray): Float {
        val x = values.getOrNull(0) ?: 0f
        val y = values.getOrNull(1) ?: 0f
        val z = values.getOrNull(2) ?: 0f
        val force = sqrt(x * x + y * y + z * z)
        return force - SensorManager.GRAVITY_EARTH
    }

    private fun triggerEmergency(gestureLabel: String) {
        scope.launch {
            runCatching {
                safetyManager.triggerEmergencyModeFromGesture(gestureLabel)
            }.onFailure { error ->
                Log.e(TAG, "❌ Failed to trigger emergency from $gestureLabel", error)
            }
        }
    }

    companion object {
        private const val TAG = "EmergencyGestureMgr"

        internal const val PREFS_NAME = "gesture_settings"
        const val KEY_GESTURES_ENABLED = "gestures_enabled"
        const val KEY_VOLUME_ENABLED = "volume_gesture_enabled"
        const val KEY_SHAKE_ENABLED = "shake_gesture_enabled"
        const val KEY_POWER_ENABLED = "power_gesture_enabled"

        private val observedPreferenceKeys = setOf(
            KEY_GESTURES_ENABLED,
            KEY_VOLUME_ENABLED,
            KEY_SHAKE_ENABLED,
            KEY_POWER_ENABLED
        )

        private const val SHAKE_THRESHOLD = 15f
        private const val SHAKE_REQUIRED_COUNT = 3
        private const val SHAKE_RESET_MS = 600L
        private const val VOLUME_REQUIRED_COUNT = 3
        private const val VOLUME_WINDOW_MS = 2000L
        private const val POWER_REQUIRED_COUNT = 5
        private const val POWER_WINDOW_MS = 5000L
        private const val GESTURE_COOLDOWN_MS = 5000L
    }
}
