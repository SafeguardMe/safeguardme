package com.safeguardme.app.data.models

/**
 * Snapshot of signals that feed the adaptive risk assessment engine.
 */
data class RiskSignalContext(
    val timestamp: Long = System.currentTimeMillis(),
    val source: RiskSignalSource,
    val description: String,
    val gesture: String? = null,
    val voiceKeyword: String? = null,
    val shakeIntensity: Float? = null,
    val volumeBurstCount: Int? = null,
    val locationAccuracyMeters: Float? = null,
    val distanceFromSafeZoneMeters: Float? = null,
    val batteryLevel: Int? = null,
    val additionalMetadata: Map<String, Any?> = emptyMap()
)

/**
 * Enum describing high-level signal sources that can influence safety risk.
 */
enum class RiskSignalSource {
    VOICE_TRIGGER,
    GESTURE,
    LOCATION_UPDATE,
    NETWORK_EVENT,
    MANUAL_REPORT,
    UNKNOWN
}
