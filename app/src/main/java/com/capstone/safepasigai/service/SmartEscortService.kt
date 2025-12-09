package com.capstone.safepasigai.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.repository.DangerZoneRepository
import com.capstone.safepasigai.data.repository.SettingsRepository
import com.capstone.safepasigai.ml.SignalVectorMagnitude
import com.capstone.safepasigai.ui.MonitoringActivity
import com.capstone.safepasigai.ui.SOSActivity
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * SmartEscortService - Production-Ready Foreground Service
 * 
 * Features:
 * 1. PARTIAL_WAKE_LOCK to keep CPU running when screen is off
 * 2. Signal Vector Magnitude (SVM) algorithm for accurate fall detection
 * 3. TensorFlow Lite audio classification for "Saklolo" detection
 * 4. DBSCAN-based danger zone detection and heatmaps
 * 5. Persistent notification with stop action
 * 
 * Architecture: Service owns all sensor logic. Activity binds for UI updates only.
 */
class SmartEscortService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "SmartEscortService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "smart_escort_channel"
        
        // Fall detection parameters - Multi-stage detection
        // Stage 1: Free-fall detection (acceleration drops below gravity)
        private const val FREE_FALL_THRESHOLD = 3.0f  // Near weightlessness
        private const val FREE_FALL_MIN_DURATION_MS = 100L  // At least 100ms of free-fall
        
        // Stage 2: Impact detection (sudden high acceleration after free-fall)
        private const val IMPACT_THRESHOLD = 25.0f  // High G impact
        private const val IMPACT_WINDOW_MS = 1000L  // Must happen within 1 second of free-fall
        
        // Stage 3: Stillness detection (lack of movement after impact)
        private const val STILLNESS_THRESHOLD = 2.0f  // Near-stationary (gravity only ~9.8)
        private const val STILLNESS_DURATION_MS = 1500L  // Must be still for 1.5 seconds
        
        private const val DEBOUNCE_MS = 10000L // 10 seconds debounce between alerts
        
        // Audio classification parameters
        private const val MODEL_FILE = "soundclassifier.tflite"
        private const val SAKLOLO_THRESHOLD = 0.70f  // Lowered for better detection
        private const val AUDIO_CLASSIFY_INTERVAL_MS = 500L
        
        // Actions
        const val ACTION_START = "com.capstone.safepasigai.START_ESCORT"
        const val ACTION_STOP = "com.capstone.safepasigai.STOP_ESCORT"
    }

    // Binder for Activity communication
    private val binder = LocalBinder()
    
    // Wake Lock - keeps CPU running
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Sensors
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var vibrator: Vibrator? = null
    
    // Settings Repository
    private lateinit var settingsRepository: SettingsRepository
    
    // Danger Zone Repository (for DBSCAN clustering)
    private lateinit var dangerZoneRepository: DangerZoneRepository
    
    // Signal Vector Magnitude for fall detection
    private val svmDetector = SignalVectorMagnitude()
    
    // Audio AI
    private var audioClassifier: AudioClassifier? = null
    private var tensorAudio: TensorAudio? = null
    private var audioRecord: AudioRecord? = null
    private var audioExecutor: ScheduledExecutorService? = null
    @Volatile private var isAudioEnabled = false
    @Volatile private var isAudioInitialized = false
    @Volatile private var isFallDetectionEnabled = true
    @Volatile private var useVolumeBasedDetection = false  // Fallback when no TFLite model
    
    // Volume-based detection parameters (fallback)
    private var loudSoundStartTime: Long = 0
    private var isLoudSound = false
    private val LOUD_SOUND_THRESHOLD = 5000  // Amplitude threshold for loud sound
    private val LOUD_SOUND_DURATION_MS = 1500L  // Must be loud for 1.5 seconds
    
    // Location Tracking
    private var locationTracker: LocationTracker? = null
    private var currentSessionId: String? = null
    private var currentLocation: android.location.Location? = null
    
    // State
    private var isMonitoring = false
    private var lastTriggerTime: Long = 0
    
    // Callbacks
    private var dangerCallback: ((String) -> Unit)? = null
    private var statusCallback: ((String) -> Unit)? = null
    private var locationCallback: ((android.location.Location) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): SmartEscortService = this@SmartEscortService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        settingsRepository = SettingsRepository(this)
        dangerZoneRepository = DangerZoneRepository(this)
        
        // Load settings
        isFallDetectionEnabled = settingsRepository.isFallDetectionEnabled()
        isAudioEnabled = settingsRepository.isVoiceDetectionEnabled()
        
        initializeSensors()
        initializeLocationTracker()
        createNotificationChannel()
        
        // Initialize audio classifier on background thread (heavy I/O)
        Executors.newSingleThreadExecutor().execute {
            initializeAudioClassifier()
        }
    }

    private fun initializeLocationTracker() {
        locationTracker = LocationTracker(this)
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun initializeAudioClassifier() {
        try {
            // Check microphone permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "RECORD_AUDIO permission not granted. Voice detection disabled.")
                isAudioEnabled = false
                isAudioInitialized = true
                return
            }
            
            // Check if model exists
            val modelExists = assets.list("")?.contains(MODEL_FILE) ?: false
            if (!modelExists) {
                Log.w(TAG, "Audio model '$MODEL_FILE' not found. Using volume-based detection as fallback.")
                // Enable volume-based detection as fallback
                useVolumeBasedDetection = true
                isAudioEnabled = settingsRepository.isVoiceDetectionEnabled()
                isAudioInitialized = true
                
                if (isMonitoring && isAudioEnabled) {
                    startVolumeBasedDetection()
                }
                return
            }
            
            audioClassifier = AudioClassifier.createFromFile(this, MODEL_FILE)
            tensorAudio = audioClassifier?.createInputTensorAudio()
            
            isAudioEnabled = true
            isAudioInitialized = true
            useVolumeBasedDetection = false
            Log.d(TAG, "Audio classifier initialized successfully")
            
            // If monitoring already started, start audio classification now
            if (isMonitoring) {
                startAudioClassification()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio classifier: ${e.message}. Using volume-based fallback.")
            useVolumeBasedDetection = true
            isAudioEnabled = settingsRepository.isVoiceDetectionEnabled()
            isAudioInitialized = true
            
            if (isMonitoring && isAudioEnabled) {
                startVolumeBasedDetection()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    // ==================== MONITORING CONTROL ====================

    private fun startMonitoring() {
        if (isMonitoring) return
        
        Log.d(TAG, "Starting Smart Escort monitoring...")
        isMonitoring = true
        
        // Generate session ID
        currentSessionId = "session_${System.currentTimeMillis()}"
        
        // 1. Acquire Wake Lock (keeps CPU running when screen off)
        acquireWakeLock()
        
        // 2. Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 3. Register accelerometer
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        // 4. Start audio classification (if available)
        if (isAudioEnabled) {
            startAudioClassification()
        }
        
        // 5. Start location tracking
        startLocationTracking()
        
        statusCallback?.invoke("ACTIVE")
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stopping Smart Escort monitoring...")
        isMonitoring = false
        
        // Stop location tracking
        stopLocationTracking()
        
        // Stop audio classification
        stopAudioClassification()
        
        // Unregister sensors
        sensorManager.unregisterListener(this)
        
        // Release wake lock
        releaseWakeLock()
        
        // Stop foreground
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        currentSessionId = null
        statusCallback?.invoke("STOPPED")
    }
    
    // ==================== LOCATION TRACKING ====================
    
    private fun startLocationTracking() {
        val userId = getUserId()
        val sessionId = currentSessionId ?: return
        
        locationTracker?.startTracking(
            userId = userId,
            sessionId = sessionId,
            onUpdate = { location ->
                currentLocation = location
                locationCallback?.invoke(location)
            }
        )
        Log.d(TAG, "Location tracking started")
    }
    
    private fun stopLocationTracking() {
        locationTracker?.stopTracking()
        Log.d(TAG, "Location tracking stopped")
    }
    
    private fun getUserId(): String {
        // Try to get Firebase user ID, fallback to device ID
        return try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: getSharedPreferences("user", Context.MODE_PRIVATE)
                    .getString("device_id", null)
                ?: java.util.UUID.randomUUID().toString().also { deviceId ->
                    getSharedPreferences("user", Context.MODE_PRIVATE)
                        .edit().putString("device_id", deviceId).apply()
                }
        } catch (e: Exception) {
            java.util.UUID.randomUUID().toString()
        }
    }

    // ==================== WAKE LOCK ====================

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SafePasigAI::SmartEscortWakeLock"
        ).apply {
            acquire(30 * 60 * 1000L) // 30 minutes max
        }
        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    // ==================== ACCELEROMETER - SVM FALL DETECTION ====================

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMonitoring || !isFallDetectionEnabled) return
        
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                
                // Use Signal Vector Magnitude algorithm for accurate fall detection
                val result = svmDetector.analyze(x, y, z)
                
                // Log state transitions for debugging
                if (result.debugInfo.isNotEmpty()) {
                    Log.d(TAG, "SVM: ${result.debugInfo}")
                }
                
                // Check if fall was detected
                if (result.isFallDetected) {
                    Log.w(TAG, "!!! FALL DETECTED via SVM (confidence: ${result.confidence}) !!!")
                    
                    // Reset the detector for next detection
                    svmDetector.reset()
                    
                    // Trigger SOS
                    triggerSOS("FALL DETECTED")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ==================== AUDIO CLASSIFICATION ====================

    private fun startAudioClassification() {
        // Use volume-based detection if no TFLite model
        if (useVolumeBasedDetection) {
            startVolumeBasedDetection()
            return
        }
        
        // Wait for async initialization to complete
        if (!isAudioInitialized || !isAudioEnabled || audioClassifier == null) return
        
        // Avoid duplicate starts
        if (audioExecutor != null) return
        
        try {
            audioRecord = audioClassifier?.createAudioRecord()
            audioRecord?.startRecording()
            
            audioExecutor = Executors.newSingleThreadScheduledExecutor()
            audioExecutor?.scheduleAtFixedRate(
                { classifyAudio() },
                0,
                AUDIO_CLASSIFY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
            
            Log.d(TAG, "Audio classification started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio classification: ${e.message}")
        }
    }

    private fun stopAudioClassification() {
        audioExecutor?.shutdownNow()
        audioExecutor = null
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        Log.d(TAG, "Audio classification stopped")
    }
    
    // ==================== VOLUME-BASED DETECTION (FALLBACK) ====================
    
    /**
     * Fallback voice detection using volume/amplitude analysis.
     * This is used when no TFLite model is available.
     * Detects sustained loud sounds (like shouting) as potential distress.
     */
    private fun startVolumeBasedDetection() {
        if (!isAudioEnabled || audioExecutor != null) return
        
        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "RECORD_AUDIO permission not granted for volume detection")
                return
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            audioRecord?.startRecording()
            
            audioExecutor = Executors.newSingleThreadScheduledExecutor()
            audioExecutor?.scheduleAtFixedRate(
                { analyzeVolume() },
                0,
                200L,  // Check every 200ms
                TimeUnit.MILLISECONDS
            )
            
            Log.d(TAG, "Volume-based detection started (fallback mode)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start volume detection: ${e.message}")
        }
    }
    
    private fun analyzeVolume() {
        if (!isMonitoring || !isAudioEnabled) return
        
        try {
            val buffer = ShortArray(1024)
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            
            if (read > 0) {
                // Calculate RMS amplitude
                var sum = 0L
                for (i in 0 until read) {
                    sum += buffer[i] * buffer[i]
                }
                val rms = kotlin.math.sqrt(sum.toDouble() / read).toInt()
                
                val currentTime = System.currentTimeMillis()
                
                if (rms > LOUD_SOUND_THRESHOLD) {
                    if (!isLoudSound) {
                        isLoudSound = true
                        loudSoundStartTime = currentTime
                        Log.d(TAG, "Loud sound detected (RMS: $rms)")
                    } else {
                        // Check if loud sound has been sustained
                        val duration = currentTime - loudSoundStartTime
                        if (duration >= LOUD_SOUND_DURATION_MS) {
                            Log.w(TAG, "Sustained loud sound detected! Duration: ${duration}ms, RMS: $rms")
                            isLoudSound = false
                            triggerSOS("LOUD DISTRESS SOUND")
                        }
                    }
                } else {
                    // Reset if sound drops
                    if (isLoudSound && (currentTime - loudSoundStartTime > 500)) {
                        isLoudSound = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Volume analysis error: ${e.message}")
        }
    }

    private fun classifyAudio() {
        if (!isMonitoring || !isAudioEnabled) return
        
        try {
            tensorAudio?.let { audio ->
                audioClassifier?.let { classifier ->
                    audioRecord?.let { record ->
                        // Load audio buffer
                        audio.load(record)
                        
                        // Run inference
                        val results = classifier.classify(audio)
                        
                        // Check for distress keywords
                        results.firstOrNull()?.categories?.forEach { category ->
                            val label = category.label.lowercase()
                            val score = category.score
                            
                            // Check for "Saklolo" or "Tulong"
                            if ((label.contains("saklolo") || label.contains("tulong")) 
                                && score >= SAKLOLO_THRESHOLD) {
                                Log.w(TAG, "DISTRESS AUDIO: $label ($score)")
                                triggerSOS("VOICE: ${category.label.uppercase()}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio classification error: ${e.message}")
        }
    }

    // ==================== SOS TRIGGER ====================

    fun triggerSOS(reason: String) {
        val currentTime = System.currentTimeMillis()
        
        // Debounce
        if (currentTime - lastTriggerTime < DEBOUNCE_MS) {
            Log.d(TAG, "SOS debounced (too soon)")
            return
        }
        lastTriggerTime = currentTime
        
        Log.w(TAG, "!!! SOS TRIGGERED: $reason !!!")
        
        // 1. Vibrate pattern (emergency)
        vibrator?.let {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            it.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
        
        // 2. Report to danger zone repository for DBSCAN clustering
        currentLocation?.let { location ->
            val eventType = when {
                reason.contains("FALL") -> "FALL"
                reason.contains("VOICE") -> "VOICE"
                reason.contains("LOUD") -> "LOUD"
                reason.contains("MANUAL") -> "MANUAL"
                else -> "SOS"
            }
            val severity = if (reason.contains("FALL") || reason.contains("VOICE")) 4 else 3
            
            dangerZoneRepository.reportDangerEvent(
                lat = location.latitude,
                lng = location.longitude,
                eventType = eventType,
                severity = severity
            )
            Log.d(TAG, "Danger event reported to DBSCAN: $eventType at ${location.latitude}, ${location.longitude}")
        }
        
        // 3. Notify bound Activity
        dangerCallback?.invoke(reason)
        
        // 4. Launch SOS Activity
        val sosIntent = Intent(this, SOSActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("REASON", reason)
        }
        startActivity(sosIntent)
    }

    // ==================== NOTIFICATION ====================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Smart Escort",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "SafePasig.AI is actively monitoring your safety"
            setShowBadge(true)
        }
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MonitoringActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, SmartEscortService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val statusText = when {
            isAudioEnabled && useVolumeBasedDetection -> "Motion + Volume Detection Active"
            isAudioEnabled -> "Motion + Voice AI Detection Active"
            else -> "Motion Detection Active"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafePasig.AI Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ==================== PUBLIC API ====================

    fun setDangerCallback(callback: (String) -> Unit) {
        dangerCallback = callback
    }
    
    fun setStatusCallback(callback: (String) -> Unit) {
        statusCallback = callback
    }
    
    fun setLocationCallback(callback: (android.location.Location) -> Unit) {
        locationCallback = callback
    }
    
    fun isCurrentlyMonitoring(): Boolean = isMonitoring
    
    fun isAudioDetectionEnabled(): Boolean = isAudioEnabled
    
    fun isUsingVolumeBasedDetection(): Boolean = useVolumeBasedDetection
    
    fun isLocationTrackingEnabled(): Boolean = locationTracker?.isCurrentlyTracking() ?: false
    
    fun getCurrentLocation(): android.location.Location? = currentLocation
    
    fun getSessionId(): String? = currentSessionId
    
    /**
     * Enable or disable voice detection at runtime.
     */
    fun setVoiceDetectionEnabled(enabled: Boolean) {
        if (enabled && !isAudioEnabled) {
            // Enable - try to start audio classification
            isAudioEnabled = true
            if (isMonitoring && isAudioInitialized) {
                startAudioClassification()
            }
        } else if (!enabled && isAudioEnabled) {
            // Disable - stop audio classification
            isAudioEnabled = false
            stopAudioClassification()
        }
    }
    
    /**
     * Enable or disable fall detection at runtime.
     */
    fun setFallDetectionEnabled(enabled: Boolean) {
        isFallDetectionEnabled = enabled
        settingsRepository.setFallDetectionEnabled(enabled)
    }
    
    fun isFallDetectionCurrentlyEnabled(): Boolean = isFallDetectionEnabled
    
    /**
     * Get danger zone clusters for heatmap display.
     */
    fun getDangerZoneClusters() = dangerZoneRepository.getDangerClusters()
    
    /**
     * Get risk level at a specific location.
     * @return Risk level 0.0 (safe) to 1.0 (high danger)
     */
    fun getRiskLevelAt(lat: Double, lng: Double) = dangerZoneRepository.getRiskLevel(lat, lng)
    
    /**
     * Check if current location is in a danger zone.
     */
    fun isInDangerZone(): Boolean {
        val location = currentLocation ?: return false
        return dangerZoneRepository.checkDangerZone(location.latitude, location.longitude).first
    }
    
    /**
     * Get heatmap points for visualization.
     */
    fun getHeatmapPoints() = dangerZoneRepository.getHeatmapPoints()

    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()
        stopAudioClassification()
        sensorManager.unregisterListener(this)
        releaseWakeLock()
        svmDetector.reset()
        Log.d(TAG, "Service destroyed")
    }
}
