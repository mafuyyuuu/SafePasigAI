package com.capstone.safepasigai.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * AudioClassifierCNN - Wrapper for TensorFlow Lite CNN audio classification.
 * 
 * Uses Mel-Spectrogram feature extraction to convert audio into images
 * that a CNN can classify for distress keywords (Saklolo, Tulong).
 * 
 * Architecture:
 * Raw Audio → Mel-Spectrogram → CNN Model → Classification
 * 
 * The model should be trained on:
 * - "saklolo" class
 * - "tulong" class  
 * - "background" class (ambient noise, speech, etc.)
 */
class AudioClassifierCNN(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioClassifierCNN"
        
        // Model configuration
        const val MODEL_FILE = "soundclassifier.tflite"
        const val SAMPLE_RATE = 16000
        const val AUDIO_DURATION_MS = 1000  // 1 second of audio
        const val AUDIO_SAMPLES = SAMPLE_RATE * AUDIO_DURATION_MS / 1000
        
        // Classification thresholds
        const val DISTRESS_THRESHOLD = 0.70f  // Minimum confidence for distress detection
    }
    
    // TFLite Interpreter
    private var interpreter: Interpreter? = null
    
    // Mel-Spectrogram feature extractor
    private val melSpectrogram = MelSpectrogram(
        sampleRate = SAMPLE_RATE,
        nFft = 512,
        hopLength = 256,
        nMels = 40
    )
    
    // Model input/output dimensions (determined by the model)
    private var inputShape: IntArray = intArrayOf()
    private var outputShape: IntArray = intArrayOf()
    private var numClasses = 0
    
    // Class labels (should match training labels)
    private var classLabels: List<String> = listOf("background", "saklolo", "tulong")
    
    /**
     * Classification result with confidence scores.
     */
    data class ClassificationResult(
        val label: String,
        val confidence: Float,
        val allScores: Map<String, Float>,
        val isDistress: Boolean
    )
    
    /**
     * Initialize the TFLite interpreter.
     * 
     * @return true if initialization successful
     */
    fun initialize(): Boolean {
        return try {
            val modelBuffer = loadModelFile(MODEL_FILE)
            
            if (modelBuffer == null) {
                Log.w(TAG, "Model file not found: $MODEL_FILE")
                return false
            }
            
            interpreter = Interpreter(modelBuffer)
            
            // Get input/output tensor shapes
            inputShape = interpreter!!.getInputTensor(0).shape()
            outputShape = interpreter!!.getOutputTensor(0).shape()
            numClasses = outputShape.last()
            
            Log.d(TAG, "Model loaded. Input: ${inputShape.contentToString()}, Output: ${outputShape.contentToString()}")
            
            // Load labels if available
            loadLabels()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize: ${e.message}")
            false
        }
    }
    
    /**
     * Classify audio samples.
     * 
     * @param audioSamples Raw PCM audio samples (16-bit, mono, 16kHz)
     * @return ClassificationResult with label and confidence
     */
    fun classify(audioSamples: ShortArray): ClassificationResult? {
        if (interpreter == null) {
            Log.w(TAG, "Interpreter not initialized")
            return null
        }
        
        try {
            // Convert to normalized float array (-1.0 to 1.0)
            val normalizedAudio = audioSamples.map { it.toFloat() / 32768f }.toFloatArray()
            
            // Extract Mel-Spectrogram features
            val melSpec = melSpectrogram.compute(normalizedAudio)
            val normalizedMelSpec = melSpectrogram.toNormalizedImage(melSpec)
            
            // Prepare input tensor
            val inputBuffer = prepareInputTensor(normalizedMelSpec)
            
            // Prepare output buffer
            val outputBuffer = Array(1) { FloatArray(numClasses) }
            
            // Run inference
            interpreter!!.run(inputBuffer, outputBuffer)
            
            // Process results
            val scores = outputBuffer[0]
            val allScores = mutableMapOf<String, Float>()
            
            var maxScore = 0f
            var maxIndex = 0
            
            for (i in scores.indices) {
                val label = if (i < classLabels.size) classLabels[i] else "class_$i"
                allScores[label] = scores[i]
                
                if (scores[i] > maxScore) {
                    maxScore = scores[i]
                    maxIndex = i
                }
            }
            
            val topLabel = if (maxIndex < classLabels.size) classLabels[maxIndex] else "unknown"
            
            // Check if distress detected
            val isDistress = (topLabel == "saklolo" || topLabel == "tulong") && 
                             maxScore >= DISTRESS_THRESHOLD
            
            return ClassificationResult(
                label = topLabel,
                confidence = maxScore,
                allScores = allScores,
                isDistress = isDistress
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Classification error: ${e.message}")
            return null
        }
    }
    
    /**
     * Classify from float array (already normalized).
     */
    fun classifyFloat(audioSamples: FloatArray): ClassificationResult? {
        val shortArray = audioSamples.map { (it * 32768f).toInt().toShort() }.toShortArray()
        return classify(shortArray)
    }
    
    /**
     * Prepare input tensor from Mel-Spectrogram.
     */
    private fun prepareInputTensor(melSpec: Array<FloatArray>): ByteBuffer {
        // Calculate buffer size based on input shape
        // Typical shape: [1, time, mel_bins, 1] or [1, mel_bins, time]
        val bufferSize = inputShape.reduce { acc, i -> acc * i } * 4 // 4 bytes per float
        
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        buffer.order(ByteOrder.nativeOrder())
        
        // Flatten Mel-Spectrogram to match input shape
        // Adjust based on your model's expected input format
        when (inputShape.size) {
            4 -> {
                // [batch, height, width, channels]
                for (i in melSpec.indices.take(inputShape[1])) {
                    for (j in melSpec[0].indices.take(inputShape[2])) {
                        buffer.putFloat(if (i < melSpec.size && j < melSpec[i].size) melSpec[i][j] else 0f)
                    }
                }
            }
            3 -> {
                // [batch, time, features]
                for (i in melSpec.indices.take(inputShape[1])) {
                    for (j in melSpec[0].indices.take(inputShape[2])) {
                        buffer.putFloat(if (i < melSpec.size && j < melSpec[i].size) melSpec[i][j] else 0f)
                    }
                }
            }
            else -> {
                // Flatten everything
                for (frame in melSpec) {
                    for (value in frame) {
                        if (buffer.hasRemaining()) {
                            buffer.putFloat(value)
                        }
                    }
                }
            }
        }
        
        // Pad with zeros if needed
        while (buffer.hasRemaining()) {
            buffer.putFloat(0f)
        }
        
        buffer.rewind()
        return buffer
    }
    
    /**
     * Load TFLite model from assets.
     */
    private fun loadModelFile(filename: String): ByteBuffer? {
        return try {
            val assetFileDescriptor = context.assets.openFd(filename)
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
            null
        }
    }
    
    /**
     * Load class labels from assets (optional).
     */
    private fun loadLabels() {
        try {
            val labelsFile = "labels.txt"
            val labels = context.assets.open(labelsFile)
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
            
            if (labels.isNotEmpty()) {
                classLabels = labels
                Log.d(TAG, "Loaded ${labels.size} labels: $labels")
            }
        } catch (e: Exception) {
            Log.d(TAG, "No labels.txt found, using default labels")
        }
    }
    
    /**
     * Release resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
    
    /**
     * Check if the classifier is ready.
     */
    fun isReady(): Boolean = interpreter != null
}
