package com.capstone.safepasigai.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.capstone.safepasigai.data.model.SafetyEvent
import com.capstone.safepasigai.data.model.SafetyEventType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing safety history.
 */
class SafetyHistoryRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "safety_history"
        private const val KEY_EVENTS = "events_list"
        private const val MAX_EVENTS = 100 // Keep last 100 events
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Get all safety events, sorted by timestamp descending.
     */
    fun getEvents(): List<SafetyEvent> {
        val json = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        val type = object : TypeToken<List<SafetyEvent>>() {}.type
        return try {
            val events: List<SafetyEvent> = gson.fromJson(json, type) ?: emptyList()
            events.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get recent events (last N).
     */
    fun getRecentEvents(count: Int = 10): List<SafetyEvent> {
        return getEvents().take(count)
    }

    /**
     * Add a new safety event.
     */
    fun addEvent(event: SafetyEvent): Boolean {
        val events = getEvents().toMutableList()
        
        // Add with generated ID
        val newEvent = event.copy(
            id = System.currentTimeMillis().toString(),
            timestamp = System.currentTimeMillis()
        )
        events.add(0, newEvent)
        
        // Trim to max size
        val trimmedEvents = events.take(MAX_EVENTS)
        
        return saveEvents(trimmedEvents)
    }

    /**
     * Record escort started event.
     */
    fun recordEscortStarted(location: String = "", lat: Double = 0.0, lng: Double = 0.0) {
        addEvent(SafetyEvent(
            type = SafetyEventType.ESCORT_STARTED,
            title = "Smart Escort Started",
            description = "Monitoring activated",
            location = location,
            latitude = lat,
            longitude = lng
        ))
    }

    /**
     * Record escort ended event.
     */
    fun recordEscortEnded(location: String = "", lat: Double = 0.0, lng: Double = 0.0) {
        addEvent(SafetyEvent(
            type = SafetyEventType.ESCORT_ENDED,
            title = "Smart Escort Ended",
            description = "Monitoring stopped",
            location = location,
            latitude = lat,
            longitude = lng
        ))
    }

    /**
     * Record SOS triggered event.
     */
    fun recordSOSTriggered(reason: String, location: String = "", lat: Double = 0.0, lng: Double = 0.0) {
        addEvent(SafetyEvent(
            type = SafetyEventType.SOS_TRIGGERED,
            title = "SOS Alert Sent",
            description = reason,
            location = location,
            latitude = lat,
            longitude = lng
        ))
    }

    /**
     * Record SOS cancelled event.
     */
    fun recordSOSCancelled() {
        addEvent(SafetyEvent(
            type = SafetyEventType.SOS_CANCELLED,
            title = "SOS Cancelled",
            description = "User cancelled the alert",
            wasCancelled = true
        ))
    }

    /**
     * Record fall detected event.
     */
    fun recordFallDetected(location: String = "", lat: Double = 0.0, lng: Double = 0.0) {
        addEvent(SafetyEvent(
            type = SafetyEventType.FALL_DETECTED,
            title = "Fall Detected",
            description = "Motion sensors triggered",
            location = location,
            latitude = lat,
            longitude = lng
        ))
    }

    /**
     * Record voice detected event.
     */
    fun recordVoiceDetected(keyword: String, location: String = "", lat: Double = 0.0, lng: Double = 0.0) {
        addEvent(SafetyEvent(
            type = SafetyEventType.VOICE_DETECTED,
            title = "Voice Alert: $keyword",
            description = "Distress keyword detected",
            location = location,
            latitude = lat,
            longitude = lng
        ))
    }

    /**
     * Record arrived home event.
     */
    fun recordArrivedHome() {
        addEvent(SafetyEvent(
            type = SafetyEventType.ARRIVED_HOME,
            title = "Arrived Home",
            description = "Safe arrival confirmed"
        ))
    }

    private fun saveEvents(events: List<SafetyEvent>): Boolean {
        return try {
            val json = gson.toJson(events)
            prefs.edit().putString(KEY_EVENTS, json).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all history.
     */
    fun clearHistory() {
        prefs.edit().remove(KEY_EVENTS).apply()
    }
}
