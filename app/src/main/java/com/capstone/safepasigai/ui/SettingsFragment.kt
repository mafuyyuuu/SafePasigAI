package com.capstone.safepasigai.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.repository.ChatRepository
import com.capstone.safepasigai.data.repository.SettingsRepository
import com.capstone.safepasigai.data.repository.UserRepository
import com.capstone.safepasigai.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var userRepository: UserRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var chatRepository: ChatRepository

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
        chatRepository = ChatRepository()
        
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
        
        // Delete All Chats
        binding.btnDeleteChats.setOnClickListener {
            showDeleteChatsConfirmation()
        }
        
        // Delete Account
        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountConfirmation()
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
    
    private fun showDeleteChatsConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete All Chats?")
            .setMessage("This will permanently delete all your conversations. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteAllChats()
            }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
    
    private fun deleteAllChats() {
        chatRepository.deleteAllChats(
            onSuccess = {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "All chats deleted", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { error ->
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private fun showDeleteAccountConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Account?")
            .setMessage(
                "This will permanently delete:\n\n" +
                "• Your profile\n" +
                "• All conversations\n" +
                "• All emergency contacts\n" +
                "• All safety history\n\n" +
                "This action cannot be undone!"
            )
            .setPositiveButton("Delete Account") { _, _ ->
                // Second confirmation for safety
                showFinalDeleteConfirmation()
            }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
    
    private fun showFinalDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Are you absolutely sure?")
            .setMessage("Type DELETE to confirm account deletion.")
            .setPositiveButton("Yes, Delete Everything") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("No, Keep My Account", null)
            .show()
    }
    
    private fun deleteAccount() {
        // Clear local data first
        userRepository.clearAllData()
        settingsRepository.clearAllSettings()
        
        // Delete Firebase data
        chatRepository.deleteAccount(
            onSuccess = {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_SHORT).show()
                    
                    // Navigate to onboarding
                    val intent = Intent(requireContext(), OnboardingActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    activity?.finish()
                }
            },
            onError = { error ->
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
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
