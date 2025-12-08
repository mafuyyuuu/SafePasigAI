package com.capstone.safepasigai.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Repository for app settings/preferences.
 */
class SettingsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "app_settings"
        
        // Safety Settings
        private const val KEY_FALL_DETECTION = "fall_detection_enabled"
        private const val KEY_VOICE_DETECTION = "voice_detection_enabled"
        private const val KEY_SOS_COUNTDOWN = "sos_countdown_seconds"
        private const val KEY_LOCATION_TRACKING = "location_tracking_enabled"
        private const val KEY_AUTO_SHARE_LOCATION = "auto_share_location"
        
        // Defaults
        const val DEFAULT_COUNTDOWN = 5
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Listener for settings changes
    private var settingsChangeListener: SettingsChangeListener? = null
    
    interface SettingsChangeListener {
        fun onFallDetectionChanged(enabled: Boolean)
        fun onVoiceDetectionChanged(enabled: Boolean)
        fun onCountdownChanged(seconds: Int)
    }
    
    fun setSettingsChangeListener(listener: SettingsChangeListener?) {
        settingsChangeListener = listener
    }

    // ==================== FALL DETECTION ====================
    
    fun isFallDetectionEnabled(): Boolean {
        return prefs.getBoolean(KEY_FALL_DETECTION, true)
    }
    
    fun setFallDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FALL_DETECTION, enabled).apply()
        settingsChangeListener?.onFallDetectionChanged(enabled)
    }

    // ==================== VOICE DETECTION ====================
    
    fun isVoiceDetectionEnabled(): Boolean {
        return prefs.getBoolean(KEY_VOICE_DETECTION, true)
    }
    
    fun setVoiceDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_DETECTION, enabled).apply()
        settingsChangeListener?.onVoiceDetectionChanged(enabled)
    }

    // ==================== SOS COUNTDOWN ====================
    
    fun getSOSCountdown(): Int {
        return prefs.getInt(KEY_SOS_COUNTDOWN, DEFAULT_COUNTDOWN)
    }
    
    fun setSOSCountdown(seconds: Int) {
        val validSeconds = seconds.coerceIn(3, 30)
        prefs.edit().putInt(KEY_SOS_COUNTDOWN, validSeconds).apply()
        settingsChangeListener?.onCountdownChanged(validSeconds)
    }
    
    /**
     * Get countdown display text.
     */
    fun getCountdownDisplayText(): String {
        return "${getSOSCountdown()} sec"
    }
    
    // ==================== LOCATION SETTINGS ====================
    
    fun isLocationTrackingEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCATION_TRACKING, true)
    }
    
    fun setLocationTrackingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCATION_TRACKING, enabled).apply()
    }
    
    fun isAutoShareLocationEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SHARE_LOCATION, true)
    }
    
    fun setAutoShareLocationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SHARE_LOCATION, enabled).apply()
    }
}
