package com.capstone.safepasigai.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.capstone.safepasigai.databinding.ActivitySettingsBinding
import com.capstone.safepasigai.utils.EmergencyDispatcher

/**
 * SettingsActivity - User settings for emergency contacts and detection options.
 * 
 * Features:
 * - Save up to 3 emergency contact phone numbers
 * - Toggle fall detection on/off
 * - Toggle voice detection on/off
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = EmergencyDispatcher.PREFS_NAME
        private const val KEY_FALL_DETECTION = "fall_detection_enabled"
        private const val KEY_VOICE_DETECTION = "voice_detection_enabled"
    }

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Load emergency contacts
        binding.etContact1.setText(prefs.getString(EmergencyDispatcher.KEY_CONTACT_1, ""))
        binding.etContact2.setText(prefs.getString(EmergencyDispatcher.KEY_CONTACT_2, ""))
        binding.etContact3.setText(prefs.getString(EmergencyDispatcher.KEY_CONTACT_3, ""))
        
        // Load detection toggles
        binding.switchFallDetection.isChecked = prefs.getBoolean(KEY_FALL_DETECTION, true)
        binding.switchVoiceDetection.isChecked = prefs.getBoolean(KEY_VOICE_DETECTION, true)
        
        // Check if voice model is available
        checkVoiceModelStatus()
    }

    private fun checkVoiceModelStatus() {
        val modelExists = try {
            assets.list("")?.contains("soundclassifier.tflite") ?: false
        } catch (e: Exception) {
            false
        }
        
        if (!modelExists) {
            binding.tvVoiceStatus.text = "Model not found - feature disabled"
            binding.switchVoiceDetection.isEnabled = false
            binding.switchVoiceDetection.isChecked = false
        }
    }

    private fun setupListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Save button
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Get contact values
        val contact1 = binding.etContact1.text.toString().trim()
        val contact2 = binding.etContact2.text.toString().trim()
        val contact3 = binding.etContact3.text.toString().trim()
        
        // Validate at least one contact
        if (contact1.isEmpty() && contact2.isEmpty() && contact3.isEmpty()) {
            Toast.makeText(this, "Please enter at least one emergency contact", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate phone number format (basic check)
        val phoneRegex = Regex("^[+]?[0-9]{10,15}$")
        
        if (contact1.isNotEmpty() && !phoneRegex.matches(contact1.replace(" ", "").replace("-", ""))) {
            binding.tilContact1.error = "Invalid phone number"
            return
        } else {
            binding.tilContact1.error = null
        }
        
        if (contact2.isNotEmpty() && !phoneRegex.matches(contact2.replace(" ", "").replace("-", ""))) {
            binding.tilContact2.error = "Invalid phone number"
            return
        } else {
            binding.tilContact2.error = null
        }
        
        if (contact3.isNotEmpty() && !phoneRegex.matches(contact3.replace(" ", "").replace("-", ""))) {
            binding.tilContact3.error = "Invalid phone number"
            return
        } else {
            binding.tilContact3.error = null
        }
        
        // Save contacts
        editor.putString(EmergencyDispatcher.KEY_CONTACT_1, contact1)
        editor.putString(EmergencyDispatcher.KEY_CONTACT_2, contact2)
        editor.putString(EmergencyDispatcher.KEY_CONTACT_3, contact3)
        
        // Save detection settings
        editor.putBoolean(KEY_FALL_DETECTION, binding.switchFallDetection.isChecked)
        editor.putBoolean(KEY_VOICE_DETECTION, binding.switchVoiceDetection.isChecked)
        
        editor.apply()
        
        // Show confirmation
        val contactCount = listOf(contact1, contact2, contact3).count { it.isNotEmpty() }
        Toast.makeText(this, "Settings saved! $contactCount contact(s) configured.", Toast.LENGTH_SHORT).show()
        
        finish()
    }
}
