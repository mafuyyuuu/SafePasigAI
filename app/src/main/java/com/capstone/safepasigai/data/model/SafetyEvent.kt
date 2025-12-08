package com.capstone.safepasigai.data.model

/**
 * Safety event for history tracking.
 */
data class SafetyEvent(
    val id: String = "",
    val type: SafetyEventType = SafetyEventType.ESCORT_STARTED,
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val wasCancelled: Boolean = false
) {
    constructor() : this("")
}

enum class SafetyEventType {
    ESCORT_STARTED,
    ESCORT_ENDED,
    SOS_TRIGGERED,
    SOS_CANCELLED,
    FALL_DETECTED,
    VOICE_DETECTED,
    ARRIVED_HOME,
    LOCATION_SHARED
}
