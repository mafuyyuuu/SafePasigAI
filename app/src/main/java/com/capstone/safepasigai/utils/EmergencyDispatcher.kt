package com.capstone.safepasigai.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * EmergencyDispatcher - Handles GPS location retrieval and SMS sending.
 * 
 * Usage:
 * 1. Call getLastKnownLocation() or getCurrentLocation()
 * 2. Call sendEmergencySMS() with the location
 */
object EmergencyDispatcher {
    
    private const val TAG = "EmergencyDispatcher"
    
    // SharedPreferences key for emergency contacts
    const val PREFS_NAME = "SafePasigPrefs"
    const val KEY_CONTACT_1 = "emergency_contact_1"
    const val KEY_CONTACT_2 = "emergency_contact_2"
    const val KEY_CONTACT_3 = "emergency_contact_3"
    
    /**
     * Get the last known location (fast, but may be stale).
     */
    fun getLastKnownLocation(
        context: Context,
        onSuccess: (Location) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            onFailure("Location permission not granted")
            return
        }
        
        val fusedClient: FusedLocationProviderClient = 
            LocationServices.getFusedLocationProviderClient(context)
        
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "Last location: ${location.latitude}, ${location.longitude}")
                        onSuccess(location)
                    } else {
                        // No cached location, get fresh one
                        getCurrentLocation(context, onSuccess, onFailure)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get last location: ${e.message}")
                    onFailure(e.message ?: "Unknown error")
                }
        } catch (e: SecurityException) {
            onFailure("Location permission denied")
        }
    }
    
    /**
     * Get a fresh GPS location (more accurate, slower).
     */
    fun getCurrentLocation(
        context: Context,
        onSuccess: (Location) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            onFailure("Location permission not granted")
            return
        }
        
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationToken = CancellationTokenSource()
        
        try {
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    Log.d(TAG, "Current location: ${location.latitude}, ${location.longitude}")
                    onSuccess(location)
                } else {
                    onFailure("Unable to get current location")
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get current location: ${e.message}")
                onFailure(e.message ?: "Unknown error")
            }
        } catch (e: SecurityException) {
            onFailure("Location permission denied")
        }
    }
    
    /**
     * Send emergency SMS to all saved contacts.
     * 
     * @param context Application context
     * @param location GPS location (nullable - will send without if unavailable)
     * @param reason The trigger reason (e.g., "FALL DETECTED", "MANUAL SOS")
     * @return Number of messages sent successfully
     */
    fun sendEmergencySMS(
        context: Context,
        location: Location?,
        reason: String
    ): Int {
        if (!hasSmsPermission(context)) {
            Log.e(TAG, "SMS permission not granted")
            return 0
        }
        
        val contacts = getEmergencyContacts(context)
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts configured")
            return 0
        }
        
        val message = buildEmergencyMessage(location, reason)
        var sentCount = 0
        
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            contacts.forEach { phoneNumber ->
                try {
                    // Split message if too long (SMS limit is 160 chars)
                    val parts = smsManager.divideMessage(message)
                    smsManager.sendMultipartTextMessage(
                        phoneNumber,
                        null,
                        parts,
                        null,
                        null
                    )
                    Log.d(TAG, "SMS sent to: $phoneNumber")
                    sentCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send SMS to $phoneNumber: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS Manager error: ${e.message}")
        }
        
        return sentCount
    }
    
    /**
     * Build the emergency message with location link.
     */
    private fun buildEmergencyMessage(location: Location?, reason: String): String {
        val timestamp = java.text.SimpleDateFormat(
            "HH:mm", java.util.Locale.getDefault()
        ).format(java.util.Date())
        
        return if (location != null) {
            val lat = location.latitude
            val lng = location.longitude
            val mapsUrl = "https://maps.google.com/?q=$lat,$lng"
            
            """
            🚨 EMERGENCY ALERT - SafePasig.AI
            
            $reason at $timestamp
            
            📍 Location: $mapsUrl
            
            Please check on me immediately!
            """.trimIndent()
        } else {
            """
            🚨 EMERGENCY ALERT - SafePasig.AI
            
            $reason at $timestamp
            
            ⚠️ Location unavailable
            
            Please try to contact me immediately!
            """.trimIndent()
        }
    }
    
    /**
     * Get emergency contacts from SharedPreferences.
     */
    fun getEmergencyContacts(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        return listOfNotNull(
            prefs.getString(KEY_CONTACT_1, null),
            prefs.getString(KEY_CONTACT_2, null),
            prefs.getString(KEY_CONTACT_3, null)
        ).filter { it.isNotBlank() }
    }
    
    /**
     * Save an emergency contact.
     */
    fun saveEmergencyContact(context: Context, slot: Int, phoneNumber: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (slot) {
            1 -> KEY_CONTACT_1
            2 -> KEY_CONTACT_2
            3 -> KEY_CONTACT_3
            else -> return
        }
        prefs.edit().putString(key, phoneNumber).apply()
        Log.d(TAG, "Saved contact $slot: $phoneNumber")
    }
    
    /**
     * Check location permission.
     */
    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check SMS permission.
     */
    private fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
