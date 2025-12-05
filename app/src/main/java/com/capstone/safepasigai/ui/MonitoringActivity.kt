package com.capstone.safepasigai.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.capstone.safepasigai.R
import com.capstone.safepasigai.databinding.ActivityMonitoringBinding
import com.capstone.safepasigai.service.SmartEscortService

/**
 * MonitoringActivity - UI for the active monitoring screen.
 * 
 * This Activity controls the SmartEscortService.
 * The actual sensor monitoring happens in the ForegroundService,
 * allowing it to continue even when the screen is off.
 */
class MonitoringActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitoringBinding
    
    // Service binding
    private var escortService: SmartEscortService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SmartEscortService.LocalBinder
            escortService = binder.getService()
            isBound = true
            
            // Set callbacks
            escortService?.setDangerCallback { reason ->
                runOnUiThread { onDangerDetected(reason) }
            }
            
            escortService?.setStatusCallback { status ->
                runOnUiThread { updateStatusUI(status) }
            }
            
            updateUI(isActive = true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            escortService = null
            isBound = false
        }
    }
    
    // Permission launcher
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
        enableEdgeToEdge()

        binding = ActivityMonitoringBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        checkPermissionsAndStart()
    }

    private fun setupButtons() {
        // Back button
        binding.btnBack.setOnClickListener {
            stopMonitoringService()
            finish()
        }
        
        // STOP Button
        binding.btnStop.setOnClickListener {
            Toast.makeText(this, "Monitoring Stopped", Toast.LENGTH_SHORT).show()
            stopMonitoringService()
            finish()
        }

        // SOS Button - Manual trigger
        binding.btnSOS.setOnClickListener {
            // Trigger via service if bound, otherwise direct
            if (isBound && escortService != null) {
                escortService?.triggerSOS("MANUAL SOS")
            } else {
                val intent = Intent(this, SOSActivity::class.java)
                intent.putExtra("REASON", "MANUAL SOS")
                startActivity(intent)
            }
        }
    }
    
    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Audio permission for voice detection
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        // Location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        // SMS permission
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
        // Start the foreground service
        val serviceIntent = Intent(this, SmartEscortService::class.java).apply {
            action = SmartEscortService.ACTION_START
        }
        
        ContextCompat.startForegroundService(this, serviceIntent)
        
        // Bind to receive callbacks
        bindService(
            Intent(this, SmartEscortService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }
    
    private fun stopMonitoringService() {
        // Unbind first
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        
        // Stop service
        val serviceIntent = Intent(this, SmartEscortService::class.java).apply {
            action = SmartEscortService.ACTION_STOP
        }
        startService(serviceIntent)
    }
    
    private fun updateUI(isActive: Boolean) {
        if (isActive) {
            binding.pulseCircle.backgroundTintList = getColorStateList(R.color.pasig_light)
        } else {
            binding.pulseCircle.backgroundTintList = getColorStateList(R.color.gray_400)
        }
    }
    
    private fun updateStatusUI(status: String) {
        // Could update a status text view here
    }
    
    private fun onDangerDetected(reason: String) {
        // Visual feedback - turn pulse red
        binding.pulseCircle.backgroundTintList = getColorStateList(R.color.alert_red)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}