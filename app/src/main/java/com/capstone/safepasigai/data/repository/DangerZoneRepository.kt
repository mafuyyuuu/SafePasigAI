package com.capstone.safepasigai.data.repository

import android.content.Context
import android.util.Log
import com.capstone.safepasigai.SafePasigApplication
import com.capstone.safepasigai.ml.DBSCAN
import com.capstone.safepasigai.ml.haversineDistance
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * DangerZoneRepository - Stores and analyzes danger zone data.
 * 
 * Uses DBSCAN clustering to:
 * 1. Identify clusters of dangerous areas
 * 2. Generate risk heatmaps
 * 3. Warn users when entering danger zones
 * 
 * Data is stored both locally (for offline access) and in Firebase (for community data).
 */
class DangerZoneRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "DangerZoneRepository"
        private const val PREFS_NAME = "danger_zones"
        private const val KEY_LOCAL_EVENTS = "local_events"
        private const val FIREBASE_PATH = "danger_zones"
        
        // DBSCAN parameters
        private const val CLUSTER_EPS = 0.0005      // ~50 meters
        private const val CLUSTER_MIN_POINTS = 3    // Minimum incidents to form a zone
        
        // Pasig City bounds (for heatmap generation)
        val PASIG_BOUNDS = arrayOf(14.52, 14.62, 121.05, 121.12) // minLat, maxLat, minLng, maxLng
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val database = FirebaseDatabase.getInstance(SafePasigApplication.DATABASE_URL)
    private val dbscan = DBSCAN(eps = CLUSTER_EPS, minPoints = CLUSTER_MIN_POINTS)
    
    // Cached clusters
    private var cachedClusters: List<DBSCAN.DangerCluster> = emptyList()
    private var lastClusterUpdate = 0L
    
    /**
     * Report a danger event at a location.
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @param eventType Type of event (SOS, FALL, VOICE, MANUAL)
     * @param severity Severity level 1-5
     */
    fun reportDangerEvent(
        lat: Double,
        lng: Double,
        eventType: String,
        severity: Int = 3
    ) {
        val event = DBSCAN.GeoPoint(
            latitude = lat,
            longitude = lng,
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            severity = severity.coerceIn(1, 5)
        )
        
        // Save locally
        saveLocalEvent(event)
        
        // Upload to Firebase for community data
        uploadEventToFirebase(event)
        
        // Invalidate cache
        lastClusterUpdate = 0L
        
        Log.d(TAG, "Danger event reported: $eventType at ($lat, $lng)")
    }
    
    /**
     * Get danger clusters for display/analysis.
     * 
     * @param forceRefresh Force recalculation even if cache is valid
     * @return List of danger clusters sorted by risk score
     */
    fun getDangerClusters(forceRefresh: Boolean = false): List<DBSCAN.DangerCluster> {
        val cacheAge = System.currentTimeMillis() - lastClusterUpdate
        val cacheValid = cacheAge < 5 * 60 * 1000 // 5 minutes
        
        if (!forceRefresh && cacheValid && cachedClusters.isNotEmpty()) {
            return cachedClusters
        }
        
        // Get all danger events
        val events = getAllDangerEvents()
        
        if (events.isEmpty()) {
            cachedClusters = emptyList()
            return cachedClusters
        }
        
        // Run DBSCAN clustering
        cachedClusters = dbscan.cluster(events)
        lastClusterUpdate = System.currentTimeMillis()
        
        Log.d(TAG, "Computed ${cachedClusters.size} danger clusters from ${events.size} events")
        
        return cachedClusters
    }
    
    /**
     * Get risk level at a specific location.
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @return Risk level 0.0 (safe) to 1.0 (high danger)
     */
    fun getRiskLevel(lat: Double, lng: Double): Double {
        val clusters = getDangerClusters()
        return dbscan.getRiskLevel(lat, lng, clusters)
    }
    
    /**
     * Check if a location is in a danger zone.
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @param threshold Risk threshold (default 0.3)
     * @return Pair of (isInDangerZone, nearestCluster)
     */
    fun checkDangerZone(lat: Double, lng: Double, threshold: Double = 0.3): Pair<Boolean, DBSCAN.DangerCluster?> {
        val clusters = getDangerClusters()
        
        var nearestCluster: DBSCAN.DangerCluster? = null
        var minDistance = Double.MAX_VALUE
        
        for (cluster in clusters) {
            val distance = haversineDistance(lat, lng, cluster.centroid.first, cluster.centroid.second)
            
            if (distance < minDistance) {
                minDistance = distance
                nearestCluster = cluster
            }
        }
        
        val riskLevel = dbscan.getRiskLevel(lat, lng, clusters)
        return Pair(riskLevel >= threshold, nearestCluster)
    }
    
    /**
     * Generate heatmap data for visualization.
     * 
     * @param gridSize Number of grid points per dimension
     * @return 2D array of risk values [lat][lng] in range [0, 1]
     */
    fun generateHeatmap(gridSize: Int = 50): Array<FloatArray> {
        val clusters = getDangerClusters()
        return dbscan.generateHeatmapGrid(clusters, gridSize, PASIG_BOUNDS)
    }
    
    /**
     * Get heatmap data as a list of weighted points for map overlay.
     */
    fun getHeatmapPoints(): List<HeatmapPoint> {
        val clusters = getDangerClusters()
        val points = mutableListOf<HeatmapPoint>()
        
        for (cluster in clusters) {
            // Add cluster center
            points.add(HeatmapPoint(
                lat = cluster.centroid.first,
                lng = cluster.centroid.second,
                weight = cluster.riskScore.toFloat(),
                radius = cluster.radius.toFloat()
            ))
            
            // Add individual incident points with lower weight
            for (point in cluster.points) {
                points.add(HeatmapPoint(
                    lat = point.latitude,
                    lng = point.longitude,
                    weight = (cluster.riskScore * 0.5).toFloat(),
                    radius = 30f
                ))
            }
        }
        
        return points
    }
    
    /**
     * Observe danger zones from Firebase for community data.
     */
    fun observeCommunityDangerZones(onUpdate: (List<DBSCAN.DangerCluster>) -> Unit) {
        database.reference.child(FIREBASE_PATH).child("events")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val events = mutableListOf<DBSCAN.GeoPoint>()
                    
                    for (child in snapshot.children) {
                        try {
                            val lat = child.child("lat").getValue(Double::class.java) ?: continue
                            val lng = child.child("lng").getValue(Double::class.java) ?: continue
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val eventType = child.child("eventType").getValue(String::class.java) ?: ""
                            val severity = child.child("severity").getValue(Int::class.java) ?: 3
                            
                            events.add(DBSCAN.GeoPoint(lat, lng, timestamp, eventType, severity))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse event: ${e.message}")
                        }
                    }
                    
                    // Merge with local events and cluster
                    val allEvents = events + getLocalEvents()
                    val clusters = dbscan.cluster(allEvents)
                    
                    cachedClusters = clusters
                    lastClusterUpdate = System.currentTimeMillis()
                    
                    onUpdate(clusters)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Firebase error: ${error.message}")
                }
            })
    }
    
    // ==================== LOCAL STORAGE ====================
    
    private fun saveLocalEvent(event: DBSCAN.GeoPoint) {
        val events = getLocalEvents().toMutableList()
        events.add(event)
        
        // Keep only last 1000 events
        val recentEvents = events.takeLast(1000)
        
        val json = gson.toJson(recentEvents.map { 
            mapOf(
                "lat" to it.latitude,
                "lng" to it.longitude,
                "timestamp" to it.timestamp,
                "eventType" to it.eventType,
                "severity" to it.severity
            )
        })
        
        prefs.edit().putString(KEY_LOCAL_EVENTS, json).apply()
    }
    
    private fun getLocalEvents(): List<DBSCAN.GeoPoint> {
        val json = prefs.getString(KEY_LOCAL_EVENTS, null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val list: List<Map<String, Any>> = gson.fromJson(json, type)
            
            list.map { map ->
                DBSCAN.GeoPoint(
                    latitude = (map["lat"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (map["lng"] as? Number)?.toDouble() ?: 0.0,
                    timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                    eventType = map["eventType"] as? String ?: "",
                    severity = (map["severity"] as? Number)?.toInt() ?: 3
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse local events: ${e.message}")
            emptyList()
        }
    }
    
    private fun getAllDangerEvents(): List<DBSCAN.GeoPoint> {
        return getLocalEvents()
    }
    
    // ==================== FIREBASE ====================
    
    private fun uploadEventToFirebase(event: DBSCAN.GeoPoint) {
        val eventData = mapOf(
            "lat" to event.latitude,
            "lng" to event.longitude,
            "timestamp" to event.timestamp,
            "eventType" to event.eventType,
            "severity" to event.severity
        )
        
        database.reference.child(FIREBASE_PATH).child("events")
            .push()
            .setValue(eventData)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to upload event: ${e.message}")
            }
    }
    
    /**
     * Data class for heatmap visualization.
     */
    data class HeatmapPoint(
        val lat: Double,
        val lng: Double,
        val weight: Float,
        val radius: Float
    )
}
