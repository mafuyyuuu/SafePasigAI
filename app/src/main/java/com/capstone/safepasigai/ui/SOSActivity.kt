package com.capstone.safepasigai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.capstone.safepasigai.R
import com.capstone.safepasigai.databinding.ActivitySosBinding
import com.capstone.safepasigai.utils.EmergencyDispatcher

/**
 * SOSActivity - Emergency countdown screen.
 * 
 * Shows a 5-second countdown. If not cancelled:
 * 1. Gets current GPS location
 * 2. Sends SMS to all emergency contacts
 * 3. Shows confirmation
 */
class SOSActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SOSActivity"
    }

    private lateinit var binding: ActivitySosBinding
    private lateinit var timer: CountDownTimer
    
    private var triggerReason: String = "EMERGENCY"
    private var currentLocation: Location? = null
    private var isAlertSent = false

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            fetchLocationAndSend()
        } else {
            // Send without location
            sendEmergencyAlerts(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get reason from intent
        triggerReason = intent.getStringExtra("REASON") ?: "EMERGENCY"
        
        startRippleAnimations()
        startCountdown()
        preloadLocation()

        binding.btnCancel.setOnClickListener {
            cancelEmergency()
        }
    }

    private fun startRippleAnimations() {
        val rippleAnim1 = AnimationUtils.loadAnimation(this, R.anim.ripple_pulse)
        val rippleAnim2 = AnimationUtils.loadAnimation(this, R.anim.ripple_pulse_delayed)

        binding.ripple1.startAnimation(rippleAnim1)
        binding.ripple2.startAnimation(rippleAnim2)
    }

    private fun startCountdown() {
        timer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                binding.tvCountdown.text = (secondsLeft + 1).toString()

                // Pulse animation
                binding.tvCountdown.animate()
                    .scaleX(1.2f).scaleY(1.2f)
                    .setDuration(150)
                    .withEndAction {
                        binding.tvCountdown.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(150)
                    }
            }

            override fun onFinish() {
                binding.tvCountdown.text = "!"
                binding.tvCountdown.textSize = 64f
                dispatchEmergency()
            }
        }.start()
    }

    /**
     * Pre-load location while countdown is running.
     */
    private fun preloadLocation() {
        if (hasLocationPermission()) {
            EmergencyDispatcher.getLastKnownLocation(
                context = this,
                onSuccess = { location ->
                    currentLocation = location
                    Log.d(TAG, "Location preloaded: ${location.latitude}, ${location.longitude}")
                },
                onFailure = { error ->
                    Log.w(TAG, "Location preload failed: $error")
                }
            )
        }
    }

    /**
     * Called when countdown finishes.
     */
    private fun dispatchEmergency() {
        if (isAlertSent) return
        
        Log.w(TAG, "!!! DISPATCHING EMERGENCY: $triggerReason !!!")
        
        // Check permissions and send
        if (hasRequiredPermissions()) {
            fetchLocationAndSend()
        } else {
            requestPermissions()
        }
    }

    private fun fetchLocationAndSend() {
        // Show loading state
        binding.tvCountdown.text = "📍"
        
        // If we already have a location from preload, use it
        if (currentLocation != null) {
            sendEmergencyAlerts(currentLocation)
            return
        }
        
        // Otherwise try to get fresh location
        EmergencyDispatcher.getCurrentLocation(
            context = this,
            onSuccess = { location ->
                currentLocation = location
                sendEmergencyAlerts(location)
            },
            onFailure = { error ->
                Log.e(TAG, "Location failed: $error")
                // Send without location
                sendEmergencyAlerts(null)
            }
        )
    }

    private fun sendEmergencyAlerts(location: Location?) {
        if (isAlertSent) return
        isAlertSent = true
        
        // Check if we have contacts
        val contacts = EmergencyDispatcher.getEmergencyContacts(this)
        if (contacts.isEmpty()) {
            showResult(false, "No emergency contacts configured!\n\nPlease add contacts in Settings.")
            return
        }
        
        // Check SMS permission
        if (!hasSmsPermission()) {
            showResult(false, "SMS permission required")
            return
        }
        
        // Send SMS
        val sentCount = EmergencyDispatcher.sendEmergencySMS(
            context = this,
            location = location,
            reason = triggerReason
        )
        
        if (sentCount > 0) {
            val locationStatus = if (location != null) "with location" else "without location"
            showResult(true, "Alerts sent to $sentCount contact(s) $locationStatus")
        } else {
            showResult(false, "Failed to send alerts")
        }
    }

    private fun showResult(success: Boolean, message: String) {
        runOnUiThread {
            binding.tvCountdown.text = if (success) "✓" else "✗"
            binding.tvCountdown.textSize = 64f
            
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            
            Log.d(TAG, "Emergency result: $message")
        }
    }

    private fun cancelEmergency() {
        timer.cancel()
        isAlertSent = true // Prevent sending after cancel
        Toast.makeText(this, "Emergency Cancelled", Toast.LENGTH_SHORT).show()
        finish()
    }

    // ==================== PERMISSIONS ====================

    private fun hasRequiredPermissions(): Boolean {
        return hasLocationPermission() && hasSmsPermission()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (!hasLocationPermission()) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!hasSmsPermission()) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            fetchLocationAndSend()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::timer.isInitialized) {
            timer.cancel()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Disable back button during emergency countdown
    }
}