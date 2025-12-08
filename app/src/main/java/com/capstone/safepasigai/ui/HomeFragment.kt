package com.capstone.safepasigai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.model.SafetyEvent
import com.capstone.safepasigai.data.model.SafetyEventType
import com.capstone.safepasigai.data.repository.SafetyHistoryRepository
import com.capstone.safepasigai.data.repository.UserRepository
import com.capstone.safepasigai.databinding.FragmentHomeBinding
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var userRepository: UserRepository
    private lateinit var safetyHistoryRepository: SafetyHistoryRepository
    private var historyAdapter: SafetyHistoryAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userRepository = UserRepository(requireContext())
        safetyHistoryRepository = SafetyHistoryRepository(requireContext())

        setupButtons()
        setupHistoryRecyclerView()
    }
    
    override fun onResume() {
        super.onResume()
        loadUserProfile()
        loadCurrentLocation()
        loadSafetyHistory()
        updateSystemStatus()
    }
    
    private fun setupButtons() {
        // Start Smart Escort button
        binding.btnStartEscort.setOnClickListener {
            Toast.makeText(context, "Initializing Sensors...", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireActivity(), MonitoringActivity::class.java)
            startActivity(intent)
        }
        
        // Avatar click - open profile
        binding.cardAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileSetupActivity::class.java))
        }
        
        // Status card click - show details or go to settings
        binding.cardStatus.setOnClickListener {
            showStatusDetails()
        }
        
        // Location card click - refresh location
        binding.cardLocation.setOnClickListener {
            Toast.makeText(context, "Refreshing location...", Toast.LENGTH_SHORT).show()
            loadCurrentLocation()
        }
    }
    
    private fun showStatusDetails() {
        val hasLocation = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasAudio = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasSms = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_system_status, null)
        val tvLocation = dialogView.findViewById<android.widget.TextView>(R.id.tvLocationStatus)
        val tvAudio = dialogView.findViewById<android.widget.TextView>(R.id.tvAudioStatus)
        val tvSms = dialogView.findViewById<android.widget.TextView>(R.id.tvSmsStatus)
        val iconLocation = dialogView.findViewById<android.widget.ImageView>(R.id.iconLocation)
        val iconAudio = dialogView.findViewById<android.widget.ImageView>(R.id.iconAudio)
        val iconSms = dialogView.findViewById<android.widget.ImageView>(R.id.iconSms)
        
        tvLocation.text = if (hasLocation) "Enabled" else "Disabled"
        tvAudio.text = if (hasAudio) "Enabled" else "Disabled"
        tvSms.text = if (hasSms) "Enabled" else "Disabled"
        
        val enabledColor = ContextCompat.getColor(requireContext(), R.color.success_green)
        val disabledColor = ContextCompat.getColor(requireContext(), R.color.alert_red)
        
        tvLocation.setTextColor(if (hasLocation) enabledColor else disabledColor)
        tvAudio.setTextColor(if (hasAudio) enabledColor else disabledColor)
        tvSms.setTextColor(if (hasSms) enabledColor else disabledColor)
        
        iconLocation.imageTintList = ContextCompat.getColorStateList(
            requireContext(), if (hasLocation) R.color.success_green else R.color.alert_red
        )
        iconAudio.imageTintList = ContextCompat.getColorStateList(
            requireContext(), if (hasAudio) R.color.success_green else R.color.alert_red
        )
        iconSms.imageTintList = ContextCompat.getColorStateList(
            requireContext(), if (hasSms) R.color.success_green else R.color.alert_red
        )
        
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("OK", null)
        
        if (!hasLocation || !hasAudio || !hasSms) {
            builder.setNeutralButton("Open Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.fromParts("package", requireContext().packageName, null)
                startActivity(intent)
            }
        }
        
        builder.show()
    }
    
    private fun setupHistoryRecyclerView() {
        historyAdapter = SafetyHistoryAdapter()
        binding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }
    
    private fun loadUserProfile() {
        val profile = userRepository.getProfile()
        
        // Greeting based on time
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Magandang Umaga,"
            hour < 18 -> "Magandang Hapon,"
            else -> "Magandang Gabi,"
        }
        binding.tvGreeting.text = greeting
        
        // User name
        val name = profile?.name?.split(" ")?.firstOrNull() ?: "User"
        binding.tvUserName.text = name
        
        // Avatar
        if (!profile?.avatarUri.isNullOrEmpty()) {
            val file = java.io.File(profile?.avatarUri ?: "")
            if (file.exists()) {
                binding.imgAvatar.visibility = View.VISIBLE
                binding.tvAvatarInitial.visibility = View.GONE
                Glide.with(this)
                    .load(file)
                    .circleCrop()
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(binding.imgAvatar)
            } else {
                showAvatarInitial(profile)
            }
        } else {
            showAvatarInitial(profile)
        }
    }
    
    private fun showAvatarInitial(profile: com.capstone.safepasigai.data.model.UserProfile?) {
        binding.imgAvatar.visibility = View.GONE
        binding.tvAvatarInitial.visibility = View.VISIBLE
        binding.tvAvatarInitial.text = profile?.getInitial() ?: "?"
    }
    
    private fun loadCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && isAdded) {
                    try {
                        val geocoder = Geocoder(requireContext(), Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val barangay = address.subLocality ?: address.locality ?: "Pasig City"
                            val city = address.locality ?: "Pasig City"
                            binding.tvLocationName.text = "$barangay,\n$city"
                        }
                    } catch (e: Exception) {
                        binding.tvLocationName.text = "Pasig City"
                    }
                }
            }
        }
    }
    
    private fun loadSafetyHistory() {
        val events = safetyHistoryRepository.getRecentEvents(5)
        
        if (events.isEmpty()) {
            // Show empty state or default items
            binding.recyclerHistory.visibility = View.GONE
            binding.tvNoHistory.visibility = View.VISIBLE
        } else {
            binding.recyclerHistory.visibility = View.VISIBLE
            binding.tvNoHistory.visibility = View.GONE
            historyAdapter?.submitList(events)
        }
    }
    
    private fun updateSystemStatus() {
        // Check if all safety features are enabled
        val hasLocation = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasAudio = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasSms = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        val allReady = hasLocation && hasAudio && hasSms
        
        if (allReady) {
            binding.tvSystemPercent.text = "100%"
            binding.tvSystemBadge.text = "SECURE"
            binding.tvSystemBadge.setBackgroundResource(R.drawable.bg_white_rounded)
            binding.tvSystemBadge.backgroundTintList = 
                ContextCompat.getColorStateList(requireContext(), R.color.success_bg)
            binding.tvSystemBadge.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.success_green)
            )
        } else {
            val percent = listOf(hasLocation, hasAudio, hasSms).count { it } * 33
            binding.tvSystemPercent.text = "$percent%"
            binding.tvSystemBadge.text = "SETUP"
            binding.tvSystemBadge.backgroundTintList = 
                ContextCompat.getColorStateList(requireContext(), R.color.orange_bg)
            binding.tvSystemBadge.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.orange_500)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Simple adapter for safety history items.
 */
class SafetyHistoryAdapter : androidx.recyclerview.widget.ListAdapter<SafetyEvent, SafetyHistoryAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<SafetyEvent>() {
        override fun areItemsTheSame(oldItem: SafetyEvent, newItem: SafetyEvent) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SafetyEvent, newItem: SafetyEvent) = oldItem == newItem
    }
) {
    class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val iconBg: View = itemView.findViewById(R.id.iconBg)
        val icon: android.widget.ImageView = itemView.findViewById(R.id.ivIcon)
        val title: android.widget.TextView = itemView.findViewById(R.id.tvTitle)
        val time: android.widget.TextView = itemView.findViewById(R.id.tvTime)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_safety_history, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = getItem(position)
        
        holder.title.text = event.title
        holder.time.text = formatTime(event.timestamp)
        
        // Set icon and color based on type
        val (iconRes, bgColor) = when (event.type) {
            SafetyEventType.ESCORT_STARTED -> R.drawable.ic_walk to R.color.blue_bg
            SafetyEventType.ESCORT_ENDED -> R.drawable.ic_shield_check to R.color.success_bg
            SafetyEventType.SOS_TRIGGERED -> R.drawable.ic_warning to R.color.alert_red_bg
            SafetyEventType.SOS_CANCELLED -> R.drawable.ic_warning to R.color.orange_bg
            SafetyEventType.FALL_DETECTED -> R.drawable.ic_walk to R.color.alert_red_bg
            SafetyEventType.VOICE_DETECTED -> R.drawable.ic_mic to R.color.alert_red_bg
            SafetyEventType.ARRIVED_HOME -> R.drawable.ic_house to R.color.success_bg
            SafetyEventType.LOCATION_SHARED -> R.drawable.ic_location to R.color.blue_bg
        }
        
        holder.icon.setImageResource(iconRes)
        holder.iconBg.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, bgColor)
        
        if (event.type == SafetyEventType.SOS_TRIGGERED || 
            event.type == SafetyEventType.FALL_DETECTED ||
            event.type == SafetyEventType.VOICE_DETECTED) {
            holder.icon.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.alert_red)
        } else {
            holder.icon.imageTintList = ContextCompat.getColorStateList(holder.itemView.context, R.color.pasig_dark)
        }
    }
    
    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} min ago"
            diff < 86400000 -> {
                val format = SimpleDateFormat("h:mm a", Locale.getDefault())
                "Today, ${format.format(timestamp)}"
            }
            diff < 172800000 -> {
                val format = SimpleDateFormat("h:mm a", Locale.getDefault())
                "Yesterday, ${format.format(timestamp)}"
            }
            else -> {
                val format = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                format.format(timestamp)
            }
        }
    }
}