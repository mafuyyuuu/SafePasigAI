package com.capstone.safepasigai.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.repository.DangerZoneRepository
import com.capstone.safepasigai.data.repository.SafetyHistoryRepository
import com.capstone.safepasigai.data.repository.SettingsRepository
import com.capstone.safepasigai.databinding.ActivityMonitoringBinding
import com.capstone.safepasigai.service.SmartEscortService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MonitoringActivity - Smart Escort monitoring screen with FREE OpenStreetMap.
 * 
 * Features:
 * - Real-time location tracking
 * - Danger zone heatmap visualization
 * - Danger zone warnings when entering high-risk areas
 */
class MonitoringActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitoringBinding
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var safetyHistoryRepository: SafetyHistoryRepository
    private lateinit var dangerZoneRepository: DangerZoneRepository
    
    // Map
    private var currentMarker: Marker? = null
    private val pathPoints = mutableListOf<GeoPoint>()
    private var pathOverlay: Polyline? = null
    private val dangerZoneOverlays = mutableListOf<Polygon>()
    
    // Service binding
    private var escortService: SmartEscortService? = null
    private var isBound = false
    
    // State
    private var isVoiceEnabled = true
    private var isHeatmapVisible = true
    private var lastDangerWarningTime = 0L
    private val dangerWarningCooldown = 60000L  // 1 minute between warnings
    
    // Handler for periodic danger zone checks
    private val handler = Handler(Looper.getMainLooper())
    private val dangerZoneCheckRunnable = object : Runnable {
        override fun run() {
            checkDangerZone()
            handler.postDelayed(this, 10000)  // Check every 10 seconds
        }
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SmartEscortService.LocalBinder
            escortService = binder.getService()
            isBound = true
            
            escortService?.setDangerCallback { reason ->
                runOnUiThread { onDangerDetected(reason) }
            }
            
            escortService?.setStatusCallback { status ->
                runOnUiThread { updateStatusUI(status) }
            }
            
            escortService?.setLocationCallback { location ->
                runOnUiThread { updateLocationUI(location) }
            }
            
            // Initial status update
            runOnUiThread { updateFeatureStatus() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            escortService = null
            isBound = false
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startMonitoringService()
        } else {
            Toast.makeText(this, "Permissions required for safety monitoring", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure OSMDroid
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMonitoringBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settingsRepository = SettingsRepository(this)
        safetyHistoryRepository = SafetyHistoryRepository(this)
        dangerZoneRepository = DangerZoneRepository(this)
        
        // Load settings
        isVoiceEnabled = settingsRepository.isVoiceDetectionEnabled()

        setupMap()
        setupButtons()
        updateInitialStatus()
        
        // Add sample danger zones for testing (remove in production)
        addSampleDangerZonesIfEmpty()
        
        loadDangerZones()
        checkPermissionsAndStart()
    }
    
    /**
     * Add sample danger zones for testing/demo purposes.
     * Remove this in production.
     */
    private fun addSampleDangerZonesIfEmpty() {
        val clusters = dangerZoneRepository.getDangerClusters()
        if (clusters.isEmpty()) {
            // Add sample danger events in Pasig City for demo
            // These are sample locations - not real danger zones
            
            // Sample 1: Near Pasig City Hall area (cluster of 4 events)
            dangerZoneRepository.reportDangerEvent(14.5764, 121.0851, "DEMO", 3)
            dangerZoneRepository.reportDangerEvent(14.5766, 121.0853, "DEMO", 4)
            dangerZoneRepository.reportDangerEvent(14.5762, 121.0849, "DEMO", 3)
            dangerZoneRepository.reportDangerEvent(14.5765, 121.0850, "DEMO", 5)
            
            // Sample 2: Near Ortigas area (cluster of 3 events)
            dangerZoneRepository.reportDangerEvent(14.5873, 121.0615, "DEMO", 4)
            dangerZoneRepository.reportDangerEvent(14.5875, 121.0617, "DEMO", 3)
            dangerZoneRepository.reportDangerEvent(14.5871, 121.0613, "DEMO", 4)
            
            android.util.Log.d("MonitoringActivity", "Added sample danger zones for demo")
        }
    }
    
    private fun updateInitialStatus() {
        // Set initial voice status based on settings
        binding.tvAudioStatus.text = if (isVoiceEnabled) "🎤 Voice: ON" else "🎤 Voice: OFF"
        binding.tvLocationStatus.text = "📍 GPS: Starting..."
    }
    
    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            
            // Default to Pasig City center
            val pasigCenter = GeoPoint(14.5764, 121.0851)
            controller.setCenter(pasigCenter)
        }
        
        // Initialize path overlay
        pathOverlay = Polyline().apply {
            outlinePaint.color = ContextCompat.getColor(this@MonitoringActivity, R.color.pasig_dark)
            outlinePaint.strokeWidth = 8f
        }
        binding.mapView.overlays.add(pathOverlay)
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            stopMonitoringService()
            finish()
        }
        
        binding.btnStop.setOnClickListener {
            Toast.makeText(this, "Monitoring Stopped", Toast.LENGTH_SHORT).show()
            safetyHistoryRepository.recordEscortEnded()
            stopMonitoringService()
            finish()
        }

        binding.btnSOS.setOnClickListener {
            if (isBound && escortService != null) {
                escortService?.triggerSOS("MANUAL SOS")
            } else {
                val intent = Intent(this, SOSActivity::class.java)
                intent.putExtra("REASON", "MANUAL SOS")
                startActivity(intent)
            }
        }
        
        binding.btnRecenter.setOnClickListener {
            escortService?.getCurrentLocation()?.let { location ->
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                binding.mapView.controller.animateTo(geoPoint)
            }
        }
        
        // Heatmap toggle button
        binding.btnHeatmap.setOnClickListener {
            toggleHeatmapVisibility()
        }
        
        // Mic button - toggle voice detection
        binding.btnMic.setOnClickListener {
            isVoiceEnabled = !isVoiceEnabled
            settingsRepository.setVoiceDetectionEnabled(isVoiceEnabled)
            updateMicButtonUI()
            updateFeatureStatus()
            
            val message = if (isVoiceEnabled) "Voice detection enabled" else "Voice detection disabled"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            
            // Notify service if bound
            escortService?.setVoiceDetectionEnabled(isVoiceEnabled)
        }
        
        updateMicButtonUI()
    }
    
    private fun updateMicButtonUI() {
        if (isVoiceEnabled) {
            binding.btnMic.setCardBackgroundColor(ContextCompat.getColor(this, R.color.blue_bg))
            binding.ivMic.imageTintList = ContextCompat.getColorStateList(this, R.color.pasig_dark)
        } else {
            binding.btnMic.setCardBackgroundColor(ContextCompat.getColor(this, R.color.gray_200))
            binding.ivMic.imageTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
        }
        
        // Update the status text immediately
        binding.tvAudioStatus.text = if (isVoiceEnabled) "🎤 Voice: ON" else "🎤 Voice: OFF"
    }
    
    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            startMonitoringService()
        }
    }
    
    private fun startMonitoringService() {
        // Record event
        safetyHistoryRepository.recordEscortStarted()
        
        val serviceIntent = Intent(this, SmartEscortService::class.java).apply {
            action = SmartEscortService.ACTION_START
        }
        
        ContextCompat.startForegroundService(this, serviceIntent)
        
        bindService(
            Intent(this, SmartEscortService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }
    
    private fun stopMonitoringService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        
        val serviceIntent = Intent(this, SmartEscortService::class.java).apply {
            action = SmartEscortService.ACTION_STOP
        }
        startService(serviceIntent)
    }
    
    private fun updateStatusUI(status: String) {
        binding.tvStatus.text = status
        binding.tvMonitoringStatus.text = when (status) {
            "ACTIVE" -> "Monitoring your safety..."
            "STOPPED" -> "Monitoring stopped"
            else -> status
        }
    }
    
    private fun updateLocationUI(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        
        // Update text
        binding.tvLocation.text = String.format(
            Locale.getDefault(),
            "📍 %.5f, %.5f",
            location.latitude,
            location.longitude
        )
        
        val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        binding.tvLastUpdate.text = "Last update: ${timeFormat.format(Date())}"
        
        // Update map marker
        currentMarker?.let { binding.mapView.overlays.remove(it) }
        currentMarker = Marker(binding.mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "You are here"
        }
        binding.mapView.overlays.add(currentMarker)
        
        // Add to path and draw
        pathPoints.add(geoPoint)
        pathOverlay?.setPoints(pathPoints)
        
        // Center on first location
        if (pathPoints.size == 1) {
            binding.mapView.controller.animateTo(geoPoint)
        }
        
        binding.mapView.invalidate()
        
        // Update location status
        binding.tvLocationStatus.text = "📍 GPS: ON"
    }
    
    private fun updateFeatureStatus() {
        val audioEnabled = escortService?.isAudioDetectionEnabled() ?: isVoiceEnabled
        val locationEnabled = escortService?.isLocationTrackingEnabled() ?: false
        
        binding.tvAudioStatus.text = if (audioEnabled) "🎤 Voice: ON" else "🎤 Voice: OFF"
        binding.tvLocationStatus.text = if (locationEnabled) "📍 GPS: ON" else "📍 GPS: Starting..."
    }
    
    private fun onDangerDetected(reason: String) {
        binding.statusDot.backgroundTintList = 
            ContextCompat.getColorStateList(this, R.color.alert_red)
        binding.tvStatus.text = reason
        binding.tvMonitoringStatus.text = "⚠️ Alert triggered!"
        
        // Add danger marker on map
        escortService?.getCurrentLocation()?.let { location ->
            val dangerMarker = Marker(binding.mapView).apply {
                position = GeoPoint(location.latitude, location.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "⚠️ $reason"
            }
            binding.mapView.overlays.add(dangerMarker)
            binding.mapView.invalidate()
        }
    }
    
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        handler.post(dangerZoneCheckRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        handler.removeCallbacks(dangerZoneCheckRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(dangerZoneCheckRunnable)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
    
    // ==================== DANGER ZONE VISUALIZATION ====================
    
    /**
     * Load and display danger zones as heatmap circles on the map.
     */
    private fun loadDangerZones() {
        // Clear existing overlays
        dangerZoneOverlays.forEach { binding.mapView.overlays.remove(it) }
        dangerZoneOverlays.clear()
        
        // Get danger clusters from DBSCAN
        val clusters = dangerZoneRepository.getDangerClusters()
        
        for (cluster in clusters) {
            // Create a circle polygon for each danger zone
            val center = GeoPoint(cluster.centroid.first, cluster.centroid.second)
            val radiusInDegrees = cluster.radius / 111000.0  // Convert meters to degrees
            
            // Generate circle points
            val circlePoints = mutableListOf<GeoPoint>()
            for (i in 0..36) {
                val angle = Math.toRadians(i * 10.0)
                val lat = center.latitude + radiusInDegrees * kotlin.math.cos(angle)
                val lng = center.longitude + radiusInDegrees * kotlin.math.sin(angle) / 
                          kotlin.math.cos(Math.toRadians(center.latitude))
                circlePoints.add(GeoPoint(lat, lng))
            }
            
            // Create polygon with color based on risk score
            val polygon = Polygon().apply {
                points = circlePoints
                
                // Color intensity based on risk score (0.0 - 1.0)
                val alpha = (cluster.riskScore * 150).toInt().coerceIn(50, 180)
                fillPaint.color = Color.argb(alpha, 255, 0, 0)  // Red with varying opacity
                
                outlinePaint.color = Color.argb(200, 200, 0, 0)
                outlinePaint.strokeWidth = 2f
                outlinePaint.style = Paint.Style.STROKE
                
                title = "⚠️ Danger Zone"
                snippet = "Risk: ${(cluster.riskScore * 100).toInt()}% • ${cluster.pointCount} incidents"
            }
            
            dangerZoneOverlays.add(polygon)
            binding.mapView.overlays.add(0, polygon)  // Add behind other overlays
        }
        
        binding.mapView.invalidate()
    }
    
    /**
     * Check if user is in a danger zone and show warning.
     */
    private fun checkDangerZone() {
        val location = escortService?.getCurrentLocation() ?: return
        
        val (isInDanger, nearestCluster) = dangerZoneRepository.checkDangerZone(
            location.latitude, 
            location.longitude,
            threshold = 0.3
        )
        
        if (isInDanger && nearestCluster != null) {
            val currentTime = System.currentTimeMillis()
            
            // Only show warning if cooldown has passed
            if (currentTime - lastDangerWarningTime >= dangerWarningCooldown) {
                lastDangerWarningTime = currentTime
                showDangerZoneWarning(nearestCluster.riskScore, nearestCluster.pointCount)
            }
        }
    }
    
    /**
     * Show danger zone warning dialog.
     */
    private fun showDangerZoneWarning(riskScore: Double, incidentCount: Int) {
        val riskPercent = (riskScore * 100).toInt()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Danger Zone Alert")
            .setMessage(
                "You are entering an area with elevated risk!\n\n" +
                "• Risk Level: $riskPercent%\n" +
                "• Past Incidents: $incidentCount\n\n" +
                "Stay alert and consider an alternative route."
            )
            .setPositiveButton("I Understand") { _, _ -> }
            .setNegativeButton("View on Map") { _, _ ->
                // Zoom to show danger zones
                binding.mapView.controller.setZoom(16.0)
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
        
        // Update status UI
        binding.statusDot.backgroundTintList = 
            ContextCompat.getColorStateList(this, R.color.orange)
        binding.tvMonitoringStatus.text = "⚠️ In danger zone ($riskPercent% risk)"
    }
    
    /**
     * Toggle visibility of danger zone heatmap overlays.
     */
    private fun toggleHeatmapVisibility() {
        isHeatmapVisible = !isHeatmapVisible
        
        if (isHeatmapVisible) {
            // Show danger zones
            loadDangerZones()
            binding.btnHeatmap.backgroundTintList = ContextCompat.getColorStateList(this, R.color.alert_red)
            Toast.makeText(this, "Danger zones visible", Toast.LENGTH_SHORT).show()
        } else {
            // Hide danger zones
            dangerZoneOverlays.forEach { binding.mapView.overlays.remove(it) }
            dangerZoneOverlays.clear()
            binding.mapView.invalidate()
            binding.btnHeatmap.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray_400)
            Toast.makeText(this, "Danger zones hidden", Toast.LENGTH_SHORT).show()
        }
    }
}