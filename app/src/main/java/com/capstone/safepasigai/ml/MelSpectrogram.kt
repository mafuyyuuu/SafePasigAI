package com.capstone.safepasigai.ml

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * MelSpectrogram - Converts raw audio waveforms into Mel-Spectrogram images
 * for CNN-based audio classification.
 * 
 * This is the feature extraction layer that prepares audio data for the
 * TensorFlow Lite CNN model.
 * 
 * Pipeline: Raw Audio → STFT → Mel Filter Banks → Log Scaling → Spectrogram Image
 */
class MelSpectrogram(
    private val sampleRate: Int = 16000,
    private val nFft: Int = 512,          // FFT window size
    private val hopLength: Int = 256,      // Hop between windows
    private val nMels: Int = 40,           // Number of Mel filter banks
    private val fMin: Float = 0f,          // Minimum frequency
    private val fMax: Float = 8000f        // Maximum frequency (Nyquist)
) {
    
    companion object {
        private const val TAG = "MelSpectrogram"
    }
    
    // Precomputed Mel filter banks
    private val melFilterBank: Array<FloatArray> by lazy { createMelFilterBank() }
    
    // Hann window for STFT
    private val hannWindow: FloatArray by lazy { createHannWindow() }
    
    /**
     * Convert raw audio samples to Mel-Spectrogram.
     * 
     * @param audioSamples Raw PCM audio samples (normalized to -1.0 to 1.0)
     * @return 2D array of Mel-Spectrogram values [time][mel_bins]
     */
    fun compute(audioSamples: FloatArray): Array<FloatArray> {
        // Step 1: Apply Short-Time Fourier Transform (STFT)
        val stftResult = stft(audioSamples)
        
        // Step 2: Compute power spectrum (magnitude squared)
        val powerSpectrum = stftResult.map { frame ->
            frame.map { it.pow(2) }.toFloatArray()
        }
        
        // Step 3: Apply Mel filter banks
        val melSpectrum = powerSpectrum.map { frame ->
            applyMelFilters(frame)
        }
        
        // Step 4: Apply log scaling (convert to dB)
        val logMelSpectrum = melSpectrum.map { frame ->
            frame.map { value ->
                // Add small epsilon to avoid log(0)
                (10 * log10(value.coerceAtLeast(1e-10f)))
            }.toFloatArray()
        }
        
        return logMelSpectrum.toTypedArray()
    }
    
    /**
     * Convert Mel-Spectrogram to normalized image for CNN input.
     * 
     * @param melSpectrogram The computed Mel-Spectrogram
     * @return Normalized 2D array with values in range [0, 1]
     */
    fun toNormalizedImage(melSpectrogram: Array<FloatArray>): Array<FloatArray> {
        // Find min and max for normalization
        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        
        for (frame in melSpectrogram) {
            for (value in frame) {
                if (value < minVal) minVal = value
                if (value > maxVal) maxVal = value
            }
        }
        
        val range = maxVal - minVal
        if (range == 0f) return melSpectrogram
        
        // Normalize to [0, 1]
        return melSpectrogram.map { frame ->
            frame.map { value ->
                (value - minVal) / range
            }.toFloatArray()
        }.toTypedArray()
    }
    
    /**
     * Short-Time Fourier Transform (STFT).
     * Breaks audio into overlapping frames and applies FFT to each.
     */
    private fun stft(audioSamples: FloatArray): List<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        val numFrames = (audioSamples.size - nFft) / hopLength + 1
        
        for (i in 0 until numFrames) {
            val start = i * hopLength
            val frame = FloatArray(nFft)
            
            // Extract frame and apply Hann window
            for (j in 0 until nFft) {
                if (start + j < audioSamples.size) {
                    frame[j] = audioSamples[start + j] * hannWindow[j]
                }
            }
            
            // Apply FFT and get magnitude spectrum
            val magnitude = fftMagnitude(frame)
            frames.add(magnitude)
        }
        
        return frames
    }
    
    /**
     * Compute FFT magnitude spectrum.
     * Uses a simple DFT implementation (for production, use a library like JTransforms).
     */
    private fun fftMagnitude(frame: FloatArray): FloatArray {
        val n = frame.size
        val halfN = n / 2 + 1
        val magnitude = FloatArray(halfN)
        
        // Simple DFT (O(n²) - acceptable for small windows)
        for (k in 0 until halfN) {
            var real = 0f
            var imag = 0f
            
            for (t in 0 until n) {
                val angle = 2.0 * PI * k * t / n
                real += frame[t] * cos(angle).toFloat()
                imag -= frame[t] * kotlin.math.sin(angle).toFloat()
            }
            
            magnitude[k] = sqrt(real * real + imag * imag)
        }
        
        return magnitude
    }
    
    /**
     * Apply Mel filter banks to power spectrum.
     */
    private fun applyMelFilters(powerSpectrum: FloatArray): FloatArray {
        val melSpectrum = FloatArray(nMels)
        
        for (i in 0 until nMels) {
            var sum = 0f
            val filter = melFilterBank[i]
            
            for (j in filter.indices) {
                if (j < powerSpectrum.size) {
                    sum += powerSpectrum[j] * filter[j]
                }
            }
            
            melSpectrum[i] = sum
        }
        
        return melSpectrum
    }
    
    /**
     * Create Mel filter banks.
     * Maps linear frequency bins to Mel scale using triangular filters.
     */
    private fun createMelFilterBank(): Array<FloatArray> {
        val numFftBins = nFft / 2 + 1
        
        // Convert frequency bounds to Mel scale
        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        
        // Create equally spaced Mel points
        val melPoints = FloatArray(nMels + 2) { i ->
            melMin + i * (melMax - melMin) / (nMels + 1)
        }
        
        // Convert back to Hz
        val hzPoints = melPoints.map { melToHz(it) }
        
        // Convert to FFT bin indices
        val binPoints = hzPoints.map { hz ->
            ((nFft + 1) * hz / sampleRate).toInt().coerceIn(0, numFftBins - 1)
        }
        
        // Create triangular filters
        return Array(nMels) { i ->
            val filter = FloatArray(numFftBins)
            val left = binPoints[i]
            val center = binPoints[i + 1]
            val right = binPoints[i + 2]
            
            // Rising slope
            for (j in left until center) {
                if (center != left) {
                    filter[j] = (j - left).toFloat() / (center - left)
                }
            }
            
            // Falling slope
            for (j in center until right) {
                if (right != center) {
                    filter[j] = (right - j).toFloat() / (right - center)
                }
            }
            
            filter
        }
    }
    
    /**
     * Create Hann window for STFT.
     */
    private fun createHannWindow(): FloatArray {
        return FloatArray(nFft) { n ->
            (0.5 * (1 - cos(2 * PI * n / (nFft - 1)))).toFloat()
        }
    }
    
    /**
     * Convert frequency in Hz to Mel scale.
     */
    private fun hzToMel(hz: Float): Float {
        return (2595 * log10(1 + hz / 700)).toFloat()
    }
    
    /**
     * Convert Mel scale to frequency in Hz.
     */
    private fun melToHz(mel: Float): Float {
        return (700 * (10.0.pow(mel / 2595.0) - 1)).toFloat()
    }
}
