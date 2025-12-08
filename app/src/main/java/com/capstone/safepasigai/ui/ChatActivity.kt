package com.capstone.safepasigai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.repository.ChatRepository
import com.capstone.safepasigai.data.repository.ContactsRepository
import com.capstone.safepasigai.data.repository.UserRepository
import com.capstone.safepasigai.databinding.ActivityChatBinding
import com.capstone.safepasigai.ui.adapter.MessageAdapter
import com.capstone.safepasigai.utils.EmergencyDispatcher
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * ChatActivity - Individual chat conversation screen.
 * Supports both Firebase in-app chat AND real SMS messaging.
 */
class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_NAME = "chat_name"
        const val EXTRA_IS_ONLINE = "is_online"
        const val EXTRA_CONTACT_PHONE = "contact_phone"
        private const val SMS_PERMISSION_CODE = 101
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatRepository: ChatRepository
    private lateinit var userRepository: UserRepository
    private lateinit var contactsRepository: ContactsRepository
    private lateinit var messageAdapter: MessageAdapter
    
    private var chatId: String = ""
    private var chatName: String = ""
    private var contactPhone: String = ""
    private var userName: String = "User"
    private var isAuthenticated = false
    private var isSmsMode = false  // Toggle between Firebase chat and SMS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
        chatName = intent.getStringExtra(EXTRA_CHAT_NAME) ?: "Chat"
        contactPhone = intent.getStringExtra(EXTRA_CONTACT_PHONE) ?: ""
        val isOnline = intent.getBooleanExtra(EXTRA_IS_ONLINE, false)

        Log.d(TAG, "Opening chat: $chatId with $chatName, phone: $contactPhone")

        if (chatId.isEmpty()) {
            Toast.makeText(this, "Invalid chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatRepository = ChatRepository()
        userRepository = UserRepository(this)
        contactsRepository = ContactsRepository(this)
        
        // Get user name from profile
        userName = userRepository.getUserName()
        
        // Try to get contact phone if not provided
        if (contactPhone.isEmpty()) {
            contactPhone = findContactPhone(chatName)
        }
        
        setupUI(chatName, isOnline)
        setupRecyclerView()
        setupListeners()
        
        // Authenticate first, then observe messages
        authenticateAndObserve()
    }
    
    private fun findContactPhone(name: String): String {
        val contacts = contactsRepository.getContacts()
        return contacts.find { it.name == name }?.phone ?: ""
    }
    
    private fun authenticateAndObserve() {
        Log.d(TAG, "Authenticating...")
        
        if (chatRepository.currentUserId != null) {
            Log.d(TAG, "Already authenticated: ${chatRepository.currentUserId}")
            isAuthenticated = true
            observeMessages()
            return
        }
        
        chatRepository.signInAnonymously(
            onSuccess = { userId ->
                Log.d(TAG, "Auth successful: $userId")
                isAuthenticated = true
                observeMessages()
            },
            onError = { error ->
                Log.e(TAG, "Auth failed: $error")
                Toast.makeText(this, "Connection failed: $error", Toast.LENGTH_SHORT).show()
                binding.emptyState.visibility = View.VISIBLE
            }
        )
    }

    private fun setupUI(name: String, isOnline: Boolean) {
        binding.tvName.text = name
        binding.tvInitial.text = name.firstOrNull()?.uppercase() ?: "?"
        binding.tvStatus.text = if (isOnline) "Online" else "Offline"
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(
                this, 
                if (isOnline) com.capstone.safepasigai.R.color.success_green 
                else com.capstone.safepasigai.R.color.text_secondary
            )
        )
        
        // Update SMS toggle button appearance
        updateSmsModeUI()
    }
    
    private fun updateSmsModeUI() {
        if (isSmsMode) {
            binding.btnSmsToggle.setColorFilter(ContextCompat.getColor(this, R.color.pasig_dark))
            binding.etMessage.hint = "Type SMS message..."
            binding.tvStatus.text = "SMS Mode • ${if (contactPhone.isNotEmpty()) contactPhone else "No phone"}"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.warning_amber))
        } else {
            binding.btnSmsToggle.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            binding.etMessage.hint = "Type a message..."
            binding.tvStatus.text = if (isAuthenticated) "In-App Chat" else "Connecting..."
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        }
    }

    private fun setupRecyclerView() {
        val userId = chatRepository.currentUserId ?: ""
        messageAdapter = MessageAdapter(userId) { lat, lng ->
            openLocationInMaps(lat, lng)
        }
        
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        
        // SMS Toggle Button
        binding.btnSmsToggle.setOnClickListener {
            toggleSmsMode()
        }
        
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                if (isSmsMode) {
                    sendSmsMessage(message)
                } else {
                    sendMessage(message)
                }
            }
        }
        
        binding.btnShareLocation.setOnClickListener {
            shareCurrentLocation()
        }
        
        binding.btnCall.setOnClickListener {
            if (contactPhone.isNotEmpty()) {
                makePhoneCall()
            } else {
                Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun toggleSmsMode() {
        if (contactPhone.isEmpty()) {
            Toast.makeText(this, "No phone number for this contact. Add phone in Contacts.", Toast.LENGTH_LONG).show()
            return
        }
        
        isSmsMode = !isSmsMode
        updateSmsModeUI()
        
        if (isSmsMode) {
            MaterialAlertDialogBuilder(this)
                .setTitle("SMS Mode Enabled")
                .setMessage("Messages will be sent as real SMS to $contactPhone.\n\nNote: Standard SMS rates may apply.")
                .setPositiveButton("Got it", null)
                .show()
        }
    }
    
    private fun sendSmsMessage(message: String) {
        if (contactPhone.isEmpty()) {
            Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check SMS permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
            return
        }
        
        try {
            val smsManager = SmsManager.getDefault()
            
            // Split long messages
            val parts = smsManager.divideMessage(message)
            
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(contactPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(contactPhone, null, message, null, null)
            }
            
            // Also save to Firebase for local history
            chatRepository.sendMessage(
                chatId = chatId,
                text = "📱 SMS: $message",
                senderName = userName,
                onSuccess = {},
                onError = {}
            )
            
            binding.etMessage.setText("")
            Toast.makeText(this, "SMS sent to $contactPhone", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}", e)
            Toast.makeText(this, "Failed to send SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun makePhoneCall() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$contactPhone")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot make call", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permission granted. Try sending again.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
                isSmsMode = false
                updateSmsModeUI()
            }
        }
    }

    private fun observeMessages() {
        Log.d(TAG, "Observing messages for chat: $chatId")
        
        // Update adapter with correct user ID after authentication
        val userId = chatRepository.currentUserId ?: ""
        messageAdapter = MessageAdapter(userId) { lat, lng ->
            openLocationInMaps(lat, lng)
        }
        binding.rvMessages.adapter = messageAdapter
        
        chatRepository.observeMessages(chatId) { messages ->
            Log.d(TAG, "Received ${messages.size} messages")
            
            if (messages.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.rvMessages.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvMessages.visibility = View.VISIBLE
                messageAdapter.submitList(messages)
                binding.rvMessages.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun sendMessage(text: String) {
        if (!isAuthenticated) {
            Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Sending message: $text")
        binding.etMessage.setText("")
        
        chatRepository.sendMessage(
            chatId = chatId,
            text = text,
            senderName = userName,
            onSuccess = {
                Log.d(TAG, "Message sent successfully")
                // Message sent, UI will update via observer
            },
            onError = { error ->
                Log.e(TAG, "Failed to send message: $error")
                Toast.makeText(this, "Failed to send: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun shareCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }
        
        EmergencyDispatcher.getCurrentLocation(
            context = this,
            onSuccess = { location ->
                chatRepository.sendLocationMessage(
                    chatId = chatId,
                    senderName = userName,
                    lat = location.latitude,
                    lng = location.longitude,
                    onSuccess = {
                        Toast.makeText(this, "Location shared", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(this, "Failed to share location: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onFailure = { error ->
                Toast.makeText(this, "Failed to get location: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun openLocationInMaps(lat: Double, lng: Double) {
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback to browser
            val browserUri = Uri.parse("https://www.google.com/maps?q=$lat,$lng")
            startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAuthenticated) {
            chatRepository.setOnlineStatus(true)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isAuthenticated) {
            chatRepository.setOnlineStatus(false)
        }
    }
}
