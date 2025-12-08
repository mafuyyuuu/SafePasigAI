package com.capstone.safepasigai.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.repository.SettingsRepository
import com.capstone.safepasigai.data.repository.UserRepository
import com.capstone.safepasigai.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var userRepository: UserRepository
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userRepository = UserRepository(requireContext())
        settingsRepository = SettingsRepository(requireContext())
        
        loadSettings()
        setupListeners()
    }
    
    override fun onResume() {
        super.onResume()
        loadProfile()
        loadSettings()
    }
    
    private fun loadSettings() {
        // Load saved settings
        binding.switchFallDetection.isChecked = settingsRepository.isFallDetectionEnabled()
        binding.switchVoiceDetection.isChecked = settingsRepository.isVoiceDetectionEnabled()
        binding.tvCountdown.text = settingsRepository.getCountdownDisplayText()
    }
    
    private fun setupListeners() {
        // Profile card click
        binding.cardProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileSetupActivity::class.java))
        }
        
        // Fall Detection toggle
        binding.switchFallDetection.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setFallDetectionEnabled(isChecked)
        }
        
        // Voice Detection toggle
        binding.switchVoiceDetection.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setVoiceDetectionEnabled(isChecked)
        }
        
        // SOS Countdown selector
        binding.btnCountdownDuration.setOnClickListener {
            showCountdownPicker()
        }
        
        // About SafePasig.AI
        binding.btnAbout.setOnClickListener {
            showAboutDialog()
        }
        
        // Privacy Policy
        binding.btnPrivacy.setOnClickListener {
            showPrivacyPolicy()
        }
    }
    
    private fun showCountdownPicker() {
        val currentValue = settingsRepository.getSOSCountdown()
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_countdown_picker, null)
        val picker = dialogView.findViewById<NumberPicker>(R.id.numberPicker)
        
        picker.apply {
            minValue = 3
            maxValue = 30
            value = currentValue
            wrapSelectorWheel = false
            displayedValues = (3..30).map { "$it seconds" }.toTypedArray()
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("SOS Countdown")
            .setMessage("Time before sending emergency alerts")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val selectedValue = picker.value
                settingsRepository.setSOSCountdown(selectedValue)
                binding.tvCountdown.text = "${selectedValue} sec"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
    
    private fun showPrivacyPolicy() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_privacy, null)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("I Understand", null)
            .show()
    }
    
    private fun loadProfile() {
        val profile = userRepository.getProfile()
        
        if (profile != null && profile.name.isNotEmpty()) {
            binding.tvProfileName.text = profile.name
            binding.tvProfileBarangay.text = if (profile.barangay.isNotEmpty()) {
                "Brgy. ${profile.barangay}"
            } else {
                "Tap to edit profile"
            }
            
            // Avatar
            if (!profile.avatarUri.isNullOrEmpty()) {
                val file = java.io.File(profile.avatarUri)
                if (file.exists()) {
                    binding.imgProfileAvatar.visibility = View.VISIBLE
                    binding.tvProfileInitial.visibility = View.GONE
                    Glide.with(this)
                        .load(file)
                        .circleCrop()
                        .placeholder(R.drawable.ic_launcher_background)
                        .into(binding.imgProfileAvatar)
                } else {
                    showProfileInitial(profile.getInitial())
                }
            } else {
                showProfileInitial(profile.getInitial())
            }
        } else {
            binding.tvProfileName.text = "Set up your profile"
            binding.tvProfileBarangay.text = "Tap to complete"
            showProfileInitial("?")
        }
    }
    
    private fun showProfileInitial(initial: String) {
        binding.imgProfileAvatar.visibility = View.GONE
        binding.tvProfileInitial.visibility = View.VISIBLE
        binding.tvProfileInitial.text = initial
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
