package com.capstone.safepasigai.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.bumptech.glide.Glide
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.model.UserProfile
import com.capstone.safepasigai.data.repository.UserRepository
import com.capstone.safepasigai.databinding.ActivityProfileSetupBinding
import com.capstone.safepasigai.utils.ImageCropHelper
import com.yalantis.ucrop.UCrop
import java.io.File

/**
 * ProfileSetupActivity - Collect user profile and medical information.
 */
class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private lateinit var userRepository: UserRepository
    
    // Current avatar URI
    private var currentAvatarUri: String = ""

    // Pasig City Barangays
    private val barangays = listOf(
        "Bagong Ilog", "Bagong Katipunan", "Bambang", "Buting", "Caniogan",
        "Dela Paz", "Kalawaan", "Kapasigan", "Kapitolyo", "Malinao",
        "Manggahan", "Maybunga", "Oranbo", "Palatiw", "Pinagbuhatan",
        "Pineda", "Rosario", "Sagad", "San Antonio", "San Joaquin",
        "San Jose", "San Miguel", "San Nicolas", "Santa Cruz", "Santa Lucia",
        "Santa Rosa", "Santo Tomas", "Santolan", "Sumilang", "Ugong"
    )

    private val bloodTypes = listOf(
        "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown"
    )
    
    // Image picker launcher - opens gallery
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            // Launch UCrop for 1:1 cropping
            val cropIntent = ImageCropHelper.createCropIntent(this, selectedUri)
            cropLauncher.launch(cropIntent)
        }
    }
    
    // Crop launcher - handles UCrop result
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedPath = ImageCropHelper.handleCropResult(result.resultCode, result.data)
            if (croppedPath != null) {
                // Save to internal storage
                val savedPath = ImageCropHelper.saveCroppedImage(this, croppedPath, "profile_avatar")
                if (savedPath != null) {
                    currentAvatarUri = savedPath
                    showAvatarImage(savedPath)
                } else {
                    Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = result.data?.let { UCrop.getError(it) }
            Toast.makeText(this, "Crop failed: ${error?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)

        setupDropdowns()
        setupListeners()
        loadExistingProfile()
    }
    
    
    private fun showAvatarImage(path: String) {
        binding.tvInitial.visibility = View.GONE
        
        // Add ImageView dynamically if needed, or use existing one
        Glide.with(this)
            .load(File(path))
            .circleCrop()
            .into(binding.imgAvatar)
        binding.imgAvatar.visibility = View.VISIBLE
    }

    private fun setupDropdowns() {
        // Barangay dropdown
        val barangayAdapter = ArrayAdapter(this, R.layout.item_dropdown, barangays)
        binding.actvBarangay.setAdapter(barangayAdapter)

        // Blood type dropdown
        val bloodTypeAdapter = ArrayAdapter(this, R.layout.item_dropdown, bloodTypes)
        binding.actvBloodType.setAdapter(bloodTypeAdapter)
    }

    private fun setupListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Profile image picker
        binding.profileImageCard.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Update initial when name changes
        binding.etName.doAfterTextChanged { text ->
            if (currentAvatarUri.isEmpty()) {
                val initial = text?.firstOrNull()?.uppercase() ?: "?"
                binding.tvInitial.text = initial
            }
        }

        // Save button
        binding.btnSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun loadExistingProfile() {
        userRepository.getProfile()?.let { profile ->
            binding.etName.setText(profile.name)
            binding.etPhone.setText(profile.phone)
            binding.actvBarangay.setText(profile.barangay, false)
            binding.actvBloodType.setText(profile.bloodType, false)
            binding.etMedicalConditions.setText(profile.medicalConditions)
            binding.etAllergies.setText(profile.allergies)
            binding.etEmergencyNotes.setText(profile.emergencyNotes)
            
            // Load avatar if exists
            if (profile.avatarUri.isNotEmpty()) {
                currentAvatarUri = profile.avatarUri
                showAvatarImage(profile.avatarUri)
            } else {
                // Update initial
                val initial = profile.name.firstOrNull()?.uppercase() ?: "?"
                binding.tvInitial.text = initial
            }
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val barangay = binding.actvBarangay.text.toString().trim()

        // Validation
        if (name.isEmpty()) {
            binding.etName.error = "Name is required"
            binding.etName.requestFocus()
            return
        }

        if (phone.isEmpty()) {
            binding.etPhone.error = "Phone number is required"
            binding.etPhone.requestFocus()
            return
        }

        val profile = UserProfile(
            id = userRepository.getProfile()?.id ?: System.currentTimeMillis().toString(),
            name = name,
            phone = phone,
            barangay = barangay,
            bloodType = binding.actvBloodType.text.toString().trim(),
            medicalConditions = binding.etMedicalConditions.text.toString().trim(),
            allergies = binding.etAllergies.text.toString().trim(),
            emergencyNotes = binding.etEmergencyNotes.text.toString().trim(),
            avatarUri = currentAvatarUri,
            isOnboardingComplete = true
        )

        if (userRepository.saveProfile(profile)) {
            Toast.makeText(this, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
            
            // Navigate to MainActivity
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show()
        }
    }
}
