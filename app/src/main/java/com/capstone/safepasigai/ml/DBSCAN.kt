package com.capstone.safepasigai.ml

import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.asin

/**
 * DBSCAN - Density-Based Spatial Clustering of Applications with Noise
 * 
 * Used for the Smart Escort feature to:
 * 1. Identify clusters of danger zones (SOS triggers, incidents)
 * 2. Generate heatmaps of high-risk areas
 * 3. Detect irregular patterns (noise points = isolated incidents)
 * 
 * Superior to K-Means because:
 * - Does not require specifying number of clusters beforehand
 * - Can find arbitrarily shaped clusters
 * - Identifies noise/outliers naturally
 * - Works well with geographic (lat/lng) data
 */
class DBSCAN(
    private val eps: Double = 0.0005,    // Maximum distance between points (in degrees, ~50m)
    private val minPoints: Int = 3        // Minimum points to form a cluster
) {
    
    companion object {
        private const val TAG = "DBSCAN"
        
        // Cluster labels
        const val NOISE = -1
        const val UNCLASSIFIED = 0
        
        // Earth radius in meters (for Haversine distance)
        private const val EARTH_RADIUS_M = 6371000.0
    }
    
    /**
     * Represents a geographic point with optional metadata.
     */
    data class GeoPoint(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long = System.currentTimeMillis(),
        val eventType: String = "",       // e.g., "SOS", "FALL", "VOICE"
        val severity: Int = 1             // 1-5 severity scale
    ) {
        var clusterId: Int = UNCLASSIFIED
        var isVisited: Boolean = false
    }
    
    /**
     * Represents a cluster of danger points.
     */
    data class DangerCluster(
        val id: Int,
        val points: List<GeoPoint>,
        val centroid: Pair<Double, Double>,  // (lat, lng)
        val radius: Double,                   // meters
        val riskScore: Double                 // 0.0 - 1.0
    ) {
        val pointCount: Int get() = points.size
        
        /**
         * Check if a point is within this cluster.
         */
        fun contains(lat: Double, lng: Double): Boolean {
            val distance = haversineDistance(centroid.first, centroid.second, lat, lng)
            return distance <= radius * 1.2 // 20% buffer
        }
    }
    
    /**
     * Run DBSCAN clustering on a list of geographic points.
     * 
     * @param points List of danger event locations
     * @return List of identified danger clusters
     */
    fun cluster(points: List<GeoPoint>): List<DangerCluster> {
        if (points.isEmpty()) return emptyList()
        
        // Reset state
        val mutablePoints = points.map { it.copy() }
        mutablePoints.forEach { 
            it.clusterId = UNCLASSIFIED
            it.isVisited = false
        }
        
        var currentClusterId = 0
        
        for (point in mutablePoints) {
            if (point.isVisited) continue
            
            point.isVisited = true
            
            // Find neighbors within eps distance
            val neighbors = regionQuery(mutablePoints, point)
            
            if (neighbors.size < minPoints) {
                // Mark as noise
                point.clusterId = NOISE
            } else {
                // Start a new cluster
                currentClusterId++
                expandCluster(mutablePoints, point, neighbors, currentClusterId)
            }
        }
        
        // Group points by cluster ID and create DangerCluster objects
        return mutablePoints
            .filter { it.clusterId > 0 }
            .groupBy { it.clusterId }
            .map { (clusterId, clusterPoints) ->
                createDangerCluster(clusterId, clusterPoints)
            }
            .sortedByDescending { it.riskScore }
    }
    
    /**
     * Expand cluster by adding density-reachable points.
     */
    private fun expandCluster(
        points: List<GeoPoint>,
        point: GeoPoint,
        neighbors: MutableList<GeoPoint>,
        clusterId: Int
    ) {
        point.clusterId = clusterId
        
        var i = 0
        while (i < neighbors.size) {
            val neighbor = neighbors[i]
            
            if (!neighbor.isVisited) {
                neighbor.isVisited = true
                
                val neighborNeighbors = regionQuery(points, neighbor)
                
                if (neighborNeighbors.size >= minPoints) {
                    // Add new neighbors to expand cluster
                    neighborNeighbors.forEach { nn ->
                        if (!neighbors.contains(nn)) {
                            neighbors.add(nn)
                        }
                    }
                }
            }
            
            if (neighbor.clusterId == UNCLASSIFIED || neighbor.clusterId == NOISE) {
                neighbor.clusterId = clusterId
            }
            
            i++
        }
    }
    
    /**
     * Find all points within eps distance of the given point.
     */
    private fun regionQuery(points: List<GeoPoint>, center: GeoPoint): MutableList<GeoPoint> {
        return points.filter { point ->
            val distance = haversineDistance(
                center.latitude, center.longitude,
                point.latitude, point.longitude
            )
            distance <= eps * 111000 // Convert degrees to approximate meters
        }.toMutableList()
    }
    
    /**
     * Create a DangerCluster from a list of clustered points.
     */
    private fun createDangerCluster(id: Int, points: List<GeoPoint>): DangerCluster {
        // Calculate centroid
        val avgLat = points.map { it.latitude }.average()
        val avgLng = points.map { it.longitude }.average()
        
        // Calculate radius (max distance from centroid)
        val radius = points.maxOfOrNull { point ->
            haversineDistance(avgLat, avgLng, point.latitude, point.longitude)
        } ?: 0.0
        
        // Calculate risk score based on:
        // - Number of incidents
        // - Recency of incidents
        // - Severity of incidents
        val now = System.currentTimeMillis()
        val dayInMs = 24 * 60 * 60 * 1000L
        
        val recencyScore = points.map { point ->
            val ageInDays = (now - point.timestamp) / dayInMs.toDouble()
            // More recent = higher score (exponential decay)
            kotlin.math.exp(-ageInDays / 30.0) // 30-day half-life
        }.average()
        
        val severityScore = points.map { it.severity }.average() / 5.0
        
        val densityScore = (points.size.toDouble() / 10.0).coerceAtMost(1.0)
        
        val riskScore = (recencyScore * 0.4 + severityScore * 0.3 + densityScore * 0.3)
            .coerceIn(0.0, 1.0)
        
        return DangerCluster(
            id = id,
            points = points,
            centroid = Pair(avgLat, avgLng),
            radius = radius.coerceAtLeast(50.0), // Minimum 50m radius
            riskScore = riskScore
        )
    }
    
    /**
     * Check if a location is in a danger zone.
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @param clusters Previously computed clusters
     * @return Risk level (0.0 = safe, 1.0 = high danger)
     */
    fun getRiskLevel(lat: Double, lng: Double, clusters: List<DangerCluster>): Double {
        var maxRisk = 0.0
        
        for (cluster in clusters) {
            val distance = haversineDistance(cluster.centroid.first, cluster.centroid.second, lat, lng)
            
            if (distance <= cluster.radius) {
                // Inside cluster - full risk
                maxRisk = maxOf(maxRisk, cluster.riskScore)
            } else if (distance <= cluster.radius * 2) {
                // Near cluster - partial risk (linear falloff)
                val falloff = 1.0 - (distance - cluster.radius) / cluster.radius
                maxRisk = maxOf(maxRisk, cluster.riskScore * falloff)
            }
        }
        
        return maxRisk
    }
    
    /**
     * Generate heatmap data for visualization.
     * 
     * @param clusters List of danger clusters
     * @param gridSize Number of grid points per dimension
     * @param bounds Map bounds (minLat, maxLat, minLng, maxLng)
     * @return 2D array of risk values [lat][lng]
     */
    fun generateHeatmapGrid(
        clusters: List<DangerCluster>,
        gridSize: Int = 50,
        bounds: Array<Double>  // [minLat, maxLat, minLng, maxLng]
    ): Array<FloatArray> {
        val (minLat, maxLat, minLng, maxLng) = bounds
        
        val latStep = (maxLat - minLat) / gridSize
        val lngStep = (maxLng - minLng) / gridSize
        
        return Array(gridSize) { i ->
            FloatArray(gridSize) { j ->
                val lat = minLat + i * latStep
                val lng = minLng + j * lngStep
                getRiskLevel(lat, lng, clusters).toFloat()
            }
        }
    }
}

/**
 * Haversine distance between two geographic points in meters.
 */
fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    
    val a = kotlin.math.sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2).pow(2)
    
    val c = 2 * asin(sqrt(a))
    
    return 6371000.0 * c // Earth radius in meters
}
