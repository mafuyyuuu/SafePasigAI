package com.capstone.safepasigai.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.capstone.safepasigai.SafePasigApplication
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase

/**
 * LocationTracker - Real-time location tracking for Smart Escort.
 * 
 * Features:
 * 1. Continuous GPS tracking during escort
 * 2. Firebase Realtime Database updates for live monitoring
 * 3. Geofencing support (future)
 * 4. Battery-efficient location updates
 */
class LocationTracker(private val context: Context) {

    companion object {
        private const val TAG = "LocationTracker"
        private const val LOCATION_INTERVAL_MS = 10000L // 10 seconds
        private const val FASTEST_INTERVAL_MS = 5000L   // 5 seconds minimum
        private const val FIREBASE_PATH_TRACKING = "tracking"
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val database = FirebaseDatabase.getInstance(SafePasigApplication.DATABASE_URL)
    
    private var locationCallback: LocationCallback? = null
    private var isTracking = false
    private var sessionId: String? = null
    private var userId: String? = null

    private var onLocationUpdate: ((Location) -> Unit)? = null

    /**
     * Start tracking location and uploading to Firebase.
     */
    fun startTracking(
        userId: String,
        sessionId: String,
        onUpdate: ((Location) -> Unit)? = null
    ) {
        if (isTracking) {
            Log.w(TAG, "Already tracking")
            return
        }

        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            return
        }

        this.userId = userId
        this.sessionId = sessionId
        this.onLocationUpdate = onUpdate

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isTracking = true
            Log.d(TAG, "Location tracking started for session: $sessionId")
            
            // Mark session as active in Firebase
            updateSessionStatus(true)
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${e.message}")
        }
    }

    /**
     * Stop tracking location.
     */
    fun stopTracking() {
        if (!isTracking) return

        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        isTracking = false
        
        // Mark session as ended
        updateSessionStatus(false)
        
        Log.d(TAG, "Location tracking stopped")
    }

    private fun handleLocationUpdate(location: Location) {
        Log.d(TAG, "Location: ${location.latitude}, ${location.longitude}")
        
        // Notify callback
        onLocationUpdate?.invoke(location)
        
        // Upload to Firebase
        uploadLocationToFirebase(location)
    }

    private fun uploadLocationToFirebase(location: Location) {
        val uid = userId ?: return
        val session = sessionId ?: return

        val locationData = mapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "accuracy" to location.accuracy,
            "speed" to location.speed,
            "bearing" to location.bearing,
            "altitude" to location.altitude,
            "timestamp" to System.currentTimeMillis(),
            "provider" to location.provider
        )

        // Update current location
        database.reference
            .child(FIREBASE_PATH_TRACKING)
            .child(uid)
            .child("current")
            .setValue(locationData)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to upload location: ${e.message}")
            }

        // Add to history trail
        database.reference
            .child(FIREBASE_PATH_TRACKING)
            .child(uid)
            .child("sessions")
            .child(session)
            .child("trail")
            .push()
            .setValue(locationData)
    }

    private fun updateSessionStatus(isActive: Boolean) {
        val uid = userId ?: return
        val session = sessionId ?: return

        val statusData = mapOf(
            "isActive" to isActive,
            "sessionId" to session,
            "lastUpdate" to System.currentTimeMillis()
        )

        database.reference
            .child(FIREBASE_PATH_TRACKING)
            .child(uid)
            .child("status")
            .setValue(statusData)
    }

    /**
     * Get current location once.
     */
    fun getCurrentLocation(onSuccess: (Location) -> Unit, onError: (String) -> Unit) {
        if (!hasLocationPermission()) {
            onError("Location permission not granted")
            return
        }

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        onSuccess(location)
                    } else {
                        // Request fresh location
                        requestFreshLocation(onSuccess, onError)
                    }
                }
                .addOnFailureListener { e ->
                    onError(e.message ?: "Failed to get location")
                }
        } catch (e: SecurityException) {
            onError("Security exception: ${e.message}")
        }
    }

    private fun requestFreshLocation(onSuccess: (Location) -> Unit, onError: (String) -> Unit) {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).setMaxUpdates(1).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocationClient.removeLocationUpdates(this)
                result.lastLocation?.let { onSuccess(it) }
                    ?: onError("Location unavailable")
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            onError("Security exception: ${e.message}")
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isCurrentlyTracking(): Boolean = isTracking
}
