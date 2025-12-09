package com.capstone.safepasigai.ml

import kotlin.math.sqrt
import kotlin.math.abs

/**
 * SignalVectorMagnitude - Advanced fall detection algorithm using accelerometer data.
 * 
 * This implements the SVM (Signal Vector Magnitude) algorithm for fall detection:
 * 1. Combines X, Y, Z accelerometer axes into a single magnitude
 * 2. Uses threshold-based detection with multiple phases
 * 3. Applies signal processing (filtering, smoothing) for accuracy
 * 
 * Fall Detection Phases:
 * 1. FREE-FALL: Low SVM (< 3 m/s²) - weightlessness during fall
 * 2. IMPACT: High SVM peak (> 20 m/s²) - hitting the ground
 * 3. POST-IMPACT: Return to ~gravity (9.8 m/s²) with low variance - lying still
 */
class SignalVectorMagnitude {
    
    companion object {
        private const val TAG = "SVM"
        
        // Detection thresholds (m/s²)
        const val GRAVITY = 9.81f
        const val FREE_FALL_THRESHOLD = 3.0f       // Near weightlessness
        const val IMPACT_THRESHOLD = 20.0f         // High G impact
        const val POST_IMPACT_VARIANCE = 2.0f      // Stillness variance
        
        // Timing thresholds (ms)
        const val MIN_FREE_FALL_DURATION = 80L     // Minimum free-fall time
        const val MAX_FREE_FALL_DURATION = 600L    // Maximum free-fall time (longer = not a fall)
        const val IMPACT_WINDOW = 500L             // Time window to detect impact after free-fall
        const val STILLNESS_WINDOW = 1500L         // Time to confirm stillness after impact
        
        // Filter parameters
        const val SMOOTHING_FACTOR = 0.2f          // Low-pass filter alpha
        const val BUFFER_SIZE = 50                 // Samples for variance calculation
    }
    
    /**
     * Fall detection state machine states.
     */
    enum class FallState {
        IDLE,              // Normal state, waiting for free-fall
        FREE_FALL,         // Detecting free-fall phase
        IMPACT_DETECTION,  // Waiting for impact after free-fall
        POST_IMPACT,       // Checking for stillness after impact
        FALL_CONFIRMED     // Fall detected, SOS should trigger
    }
    
    /**
     * Result of fall detection analysis.
     */
    data class FallAnalysisResult(
        val state: FallState,
        val svm: Float,                  // Current Signal Vector Magnitude
        val smoothedSvm: Float,          // Low-pass filtered SVM
        val variance: Float,             // Recent variance (for stillness detection)
        val isFallDetected: Boolean,
        val confidence: Float,           // 0.0 - 1.0 confidence score
        val debugInfo: String = ""
    )
    
    // State variables
    private var currentState = FallState.IDLE
    private var freeFallStartTime = 0L
    private var impactTime = 0L
    private var postImpactStartTime = 0L
    
    // Signal processing
    private var smoothedSvm = GRAVITY
    private val svmBuffer = mutableListOf<Float>()
    private var peakSvmDuringImpact = 0f
    
    /**
     * Calculate Signal Vector Magnitude from accelerometer readings.
     * 
     * SVM = √(x² + y² + z²)
     * 
     * At rest: SVM ≈ 9.81 m/s² (gravity)
     * Free fall: SVM ≈ 0 m/s²
     * Impact: SVM > 20 m/s²
     */
    fun calculateSVM(x: Float, y: Float, z: Float): Float {
        return sqrt(x * x + y * y + z * z)
    }
    
    /**
     * Process new accelerometer reading and detect falls.
     * 
     * @param x Accelerometer X axis (m/s²)
     * @param y Accelerometer Y axis (m/s²)
     * @param z Accelerometer Z axis (m/s²)
     * @return FallAnalysisResult with current state and detection status
     */
    fun analyze(x: Float, y: Float, z: Float): FallAnalysisResult {
        val currentTime = System.currentTimeMillis()
        val rawSvm = calculateSVM(x, y, z)
        
        // Apply low-pass filter for smoothing
        smoothedSvm = smoothedSvm * (1 - SMOOTHING_FACTOR) + rawSvm * SMOOTHING_FACTOR
        
        // Update buffer for variance calculation
        svmBuffer.add(rawSvm)
        if (svmBuffer.size > BUFFER_SIZE) {
            svmBuffer.removeAt(0)
        }
        
        val variance = calculateVariance(svmBuffer)
        var debugInfo = ""
        var isFallDetected = false
        var confidence = 0f
        
        // State machine for fall detection
        when (currentState) {
            FallState.IDLE -> {
                // Look for free-fall signature (near weightlessness)
                if (rawSvm < FREE_FALL_THRESHOLD) {
                    currentState = FallState.FREE_FALL
                    freeFallStartTime = currentTime
                    debugInfo = "Free-fall started (SVM: ${"%.2f".format(rawSvm)})"
                }
            }
            
            FallState.FREE_FALL -> {
                val duration = currentTime - freeFallStartTime
                
                if (rawSvm >= FREE_FALL_THRESHOLD) {
                    // Free-fall ended
                    if (duration in MIN_FREE_FALL_DURATION..MAX_FREE_FALL_DURATION) {
                        // Valid free-fall duration, now look for impact
                        currentState = FallState.IMPACT_DETECTION
                        impactTime = currentTime
                        peakSvmDuringImpact = rawSvm
                        debugInfo = "Free-fall ended (${duration}ms), checking for impact"
                    } else {
                        // Invalid duration, reset
                        currentState = FallState.IDLE
                        debugInfo = if (duration < MIN_FREE_FALL_DURATION) {
                            "Free-fall too short (${duration}ms)"
                        } else {
                            "Free-fall too long (${duration}ms)"
                        }
                    }
                } else if (duration > MAX_FREE_FALL_DURATION) {
                    // Timeout - not a fall
                    currentState = FallState.IDLE
                    debugInfo = "Free-fall timeout"
                }
            }
            
            FallState.IMPACT_DETECTION -> {
                val timeSinceFreeFall = currentTime - impactTime
                
                // Track peak SVM during impact window
                if (rawSvm > peakSvmDuringImpact) {
                    peakSvmDuringImpact = rawSvm
                }
                
                if (peakSvmDuringImpact >= IMPACT_THRESHOLD) {
                    // High-G impact detected, move to post-impact phase
                    currentState = FallState.POST_IMPACT
                    postImpactStartTime = currentTime
                    debugInfo = "Impact detected (peak: ${"%.2f".format(peakSvmDuringImpact)})"
                } else if (timeSinceFreeFall > IMPACT_WINDOW) {
                    // No significant impact within window
                    currentState = FallState.IDLE
                    debugInfo = "No impact detected within window"
                }
            }
            
            FallState.POST_IMPACT -> {
                val stillnessDuration = currentTime - postImpactStartTime
                
                // Check for stillness (low variance, near gravity)
                val isStill = variance < POST_IMPACT_VARIANCE && 
                              abs(smoothedSvm - GRAVITY) < 3.0f
                
                if (isStill && stillnessDuration >= STILLNESS_WINDOW) {
                    // FALL CONFIRMED!
                    currentState = FallState.FALL_CONFIRMED
                    isFallDetected = true
                    
                    // Calculate confidence based on how well it matched the pattern
                    confidence = calculateConfidence(peakSvmDuringImpact, variance, stillnessDuration)
                    
                    debugInfo = "FALL CONFIRMED! Confidence: ${"%.1f".format(confidence * 100)}%"
                } else if (stillnessDuration > STILLNESS_WINDOW * 2) {
                    // Person is moving - not a fall, or they recovered
                    currentState = FallState.IDLE
                    debugInfo = "Movement detected, resetting"
                }
            }
            
            FallState.FALL_CONFIRMED -> {
                // Stay in confirmed state until reset
                isFallDetected = true
                confidence = 1.0f
            }
        }
        
        return FallAnalysisResult(
            state = currentState,
            svm = rawSvm,
            smoothedSvm = smoothedSvm,
            variance = variance,
            isFallDetected = isFallDetected,
            confidence = confidence,
            debugInfo = debugInfo
        )
    }
    
    /**
     * Calculate variance of recent SVM values.
     */
    private fun calculateVariance(values: List<Float>): Float {
        if (values.size < 2) return 0f
        
        val mean = values.average().toFloat()
        val sumSquaredDiff = values.map { (it - mean) * (it - mean) }.sum()
        return sumSquaredDiff / (values.size - 1)
    }
    
    /**
     * Calculate confidence score for fall detection.
     */
    private fun calculateConfidence(peakImpact: Float, variance: Float, stillnessDuration: Long): Float {
        // Higher impact = more confidence
        val impactScore = (peakImpact / 30f).coerceIn(0.5f, 1f)
        
        // Lower variance = more confidence
        val stillnessScore = (1f - variance / POST_IMPACT_VARIANCE).coerceIn(0.5f, 1f)
        
        // Longer stillness = more confidence
        val durationScore = (stillnessDuration / (STILLNESS_WINDOW * 2f)).coerceIn(0.5f, 1f)
        
        return (impactScore * 0.4f + stillnessScore * 0.3f + durationScore * 0.3f)
    }
    
    /**
     * Reset the fall detection state machine.
     * Call this after handling a fall or to cancel detection.
     */
    fun reset() {
        currentState = FallState.IDLE
        freeFallStartTime = 0L
        impactTime = 0L
        postImpactStartTime = 0L
        peakSvmDuringImpact = 0f
        svmBuffer.clear()
        smoothedSvm = GRAVITY
    }
    
    /**
     * Get current state for debugging/UI.
     */
    fun getCurrentState(): FallState = currentState
    
    /**
     * Check if a fall has been detected and not yet reset.
     */
    fun isFallDetected(): Boolean = currentState == FallState.FALL_CONFIRMED
}
