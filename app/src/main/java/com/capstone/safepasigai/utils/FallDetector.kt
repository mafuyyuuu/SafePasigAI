package com.capstone.safepasigai.utils

import kotlin.math.sqrt

/**
 * FallDetector - Utility class for fall detection algorithms.
 * 
 * Uses accelerometer data to detect sudden changes in motion
 * that may indicate a fall event.
 */
object FallDetector {
    
    // Thresholds (can be tuned based on testing)
    private const val FALL_THRESHOLD = 25.0f      // High acceleration spike
    private const val FREE_FALL_THRESHOLD = 3.0f  // Near-zero G (free fall)
    private const val IMPACT_THRESHOLD = 20.0f    // Post-fall impact
    
    /**
     * Simple magnitude-based fall detection.
     * Triggers when acceleration exceeds threshold.
     * 
     * @param x Accelerometer X value
     * @param y Accelerometer Y value
     * @param z Accelerometer Z value
     * @return true if fall detected
     */
    fun detectFallSimple(x: Float, y: Float, z: Float): Boolean {
        val magnitude = calculateMagnitude(x, y, z)
        return magnitude > FALL_THRESHOLD
    }
    
    /**
     * Calculate acceleration magnitude.
     * Normal standing = ~9.8 (gravity)
     * Free fall = ~0
     * Impact = >20
     */
    fun calculateMagnitude(x: Float, y: Float, z: Float): Float {
        return sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    }
    
    /**
     * Check if device is in free-fall state.
     * Free fall occurs when all acceleration approaches zero.
     */
    fun isFreeFall(x: Float, y: Float, z: Float): Boolean {
        return calculateMagnitude(x, y, z) < FREE_FALL_THRESHOLD
    }
    
    /**
     * Advanced fall detection using state machine.
     * Detects the pattern: Free Fall -> Impact -> Stillness
     * 
     * TODO: Implement in Phase 3 for higher accuracy
     */
    data class FallState(
        var isInFreeFall: Boolean = false,
        var freeFallStartTime: Long = 0,
        var impactDetected: Boolean = false
    )
}
