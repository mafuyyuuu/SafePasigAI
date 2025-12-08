package com.capstone.safepasigai.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.repository.SafetyHistoryRepository
import com.capstone.safepasigai.data.repository.SettingsRepository
import com.capstone.safepasigai.databinding.ActivityMonitoringBinding
import com.capstone.safepasigai.service.SmartEscortService
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MonitoringActivity - Smart Escort monitoring screen with FREE OpenStreetMap.
 * 
 * Uses OSMDroid (OpenStreetMap) - completely free, no API key required!
 */
class MonitoringActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitoringBinding
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var safetyHistoryRepository: SafetyHistoryRepository
    
    // Map
    private var currentMarker: Marker? = null
    private val pathPoints = mutableListOf<GeoPoint>()
    private var pathOverlay: Polyline? = null
    
    // Service binding
    private var escortService: SmartEscortService? = null
    private var isBound = false
    
    // State
    private var isVoiceEnabled = true
    
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
        
        // Load settings
        isVoiceEnabled = settingsRepository.isVoiceDetectionEnabled()

        setupMap()
        setupButtons()
        updateInitialStatus()
        checkPermissionsAndStart()
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
    }
    
    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}