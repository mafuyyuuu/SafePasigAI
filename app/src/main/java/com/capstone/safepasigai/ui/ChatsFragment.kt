package com.capstone.safepasigai.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.model.Chat
import com.capstone.safepasigai.data.model.EmergencyContact
import com.capstone.safepasigai.data.repository.ChatRepository
import com.capstone.safepasigai.data.repository.ContactsRepository
import com.capstone.safepasigai.data.repository.UserRepository
import com.capstone.safepasigai.databinding.FragmentChatsBinding
import com.capstone.safepasigai.ui.adapter.ChatListAdapter
import com.capstone.safepasigai.ui.adapter.ContactPickerAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ChatsFragment : Fragment() {
    
    companion object {
        private const val TAG = "ChatsFragment"
    }

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var chatRepository: ChatRepository
    private lateinit var contactsRepository: ContactsRepository
    private lateinit var userRepository: UserRepository
    private lateinit var chatAdapter: ChatListAdapter
    private var isAuthenticated = false
    private var currentDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        chatRepository = ChatRepository()
        contactsRepository = ContactsRepository(requireContext())
        userRepository = UserRepository(requireContext())
        
        setupRecyclerView()
        setupFAB()
        authenticateAndLoadChats()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatListAdapter { chat ->
            openChat(chat)
        }
        
        binding.rvChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }
    }
    
    private fun setupFAB() {
        binding.fabNewChat.setOnClickListener {
            if (!isAuthenticated) {
                Toast.makeText(context, "Connecting to server...", Toast.LENGTH_SHORT).show()
                authenticateAndLoadChats()
                return@setOnClickListener
            }
            showContactPickerDialog()
        }
    }
    
    private fun showContactPickerDialog() {
        val contacts = contactsRepository.getContacts()
        
        if (contacts.isEmpty()) {
            Toast.makeText(context, "Add emergency contacts first in Settings", Toast.LENGTH_LONG).show()
            return
        }
        
        Log.d(TAG, "Showing contact picker with ${contacts.size} contacts")
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_contact, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvContacts)
        val emptyState = dialogView.findViewById<LinearLayout>(R.id.emptyState)
        
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = ContactPickerAdapter(contacts) { contact ->
            Log.d(TAG, "Contact selected: ${contact.name}")
            currentDialog?.dismiss()
            startChatWithContact(contact)
        }
        recyclerView.adapter = adapter
        
        currentDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Message")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()
        
        currentDialog?.show()
    }
    
    private fun startChatWithContact(contact: EmergencyContact) {
        if (!isAuthenticated) {
            Toast.makeText(context, "Connecting to chat server...", Toast.LENGTH_SHORT).show()
            authenticateAndLoadChats()
            return
        }
        
        Log.d(TAG, "Starting chat with contact: ${contact.name} (ID: ${contact.id}, Phone: ${contact.phone})")
        
        // Show loading
        Toast.makeText(context, "Starting chat with ${contact.name}...", Toast.LENGTH_SHORT).show()
        
        // Get user's phone number for phone-based chat matching
        val myPhone = userRepository.getProfile()?.phone ?: ""
        
        if (contact.phone.isNotEmpty() && myPhone.isNotEmpty()) {
            // Use phone-based chat (allows two phones to chat in same room)
            chatRepository.getOrCreateChatByPhone(
                myPhone = myPhone,
                contactPhone = contact.phone,
                contactName = contact.name,
                onSuccess = { chatId ->
                    Log.d(TAG, "Phone chat created/found: $chatId")
                    openChatActivity(chatId, contact)
                },
                onError = { error ->
                    Log.e(TAG, "Phone chat failed: $error, falling back to regular chat")
                    // Fallback to regular chat
                    createRegularChat(contact)
                }
            )
        } else {
            // Fallback to regular chat if phone numbers not available
            createRegularChat(contact)
        }
    }
    
    private fun createRegularChat(contact: EmergencyContact) {
        chatRepository.getOrCreateDirectChat(
            contactId = contact.id,
            contactName = contact.name,
            onSuccess = { chatId ->
                Log.d(TAG, "Chat created/found: $chatId")
                openChatActivity(chatId, contact)
            },
            onError = { error ->
                Log.e(TAG, "Failed to create chat: $error")
                Toast.makeText(context, "Failed to create chat: $error", Toast.LENGTH_LONG).show()
            }
        )
    }
    
    private fun openChatActivity(chatId: String, contact: EmergencyContact) {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
            putExtra(ChatActivity.EXTRA_CHAT_NAME, contact.name)
            putExtra(ChatActivity.EXTRA_IS_ONLINE, false)
            putExtra(ChatActivity.EXTRA_CONTACT_PHONE, contact.phone)
            putExtra(ChatActivity.EXTRA_CONTACT_ID, contact.id)
        }
        startActivity(intent)
    }
    
    private fun authenticateAndLoadChats() {
        Log.d(TAG, "Authenticating for chat...")
        
        // Check if already authenticated
        if (chatRepository.currentUserId != null) {
            Log.d(TAG, "Already authenticated: ${chatRepository.currentUserId}")
            isAuthenticated = true
            syncChatsByPhone()
            observeChats()
            return
        }
        
        // Sign in anonymously for demo
        chatRepository.signInAnonymously(
            onSuccess = { userId ->
                Log.d(TAG, "Auth successful: $userId")
                isAuthenticated = true
                
                // Update user profile with local name
                val profile = userRepository.getProfile()
                if (profile != null) {
                    val chatUser = com.capstone.safepasigai.data.model.ChatUser(
                        id = userId,
                        name = profile.name,
                        phone = profile.phone,
                        barangay = profile.barangay,
                        isOnline = true
                    )
                    chatRepository.updateUserProfile(chatUser) {}
                    
                    // Sync existing chats by phone number
                    syncChatsByPhone()
                }
                
                observeChats()
            },
            onError = { error ->
                Log.e(TAG, "Auth failed: $error")
                isAuthenticated = false
                showEmptyState()
                
                // Show more helpful error
                val message = if (error.contains("Enable Anonymous")) {
                    "Setup required: Enable Anonymous authentication in Firebase Console"
                } else {
                    "Connection failed: $error"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        )
    }
    
    /**
     * Sync chats by phone number to find existing chats from other devices
     */
    private fun syncChatsByPhone() {
        val profile = userRepository.getProfile()
        if (profile?.phone.isNullOrEmpty()) {
            Log.d(TAG, "No phone number in profile, skipping phone sync")
            return
        }
        
        Log.d(TAG, "Syncing chats by phone: ${profile?.phone}")
        chatRepository.findChatsByPhone(profile!!.phone) { chatIds ->
            Log.d(TAG, "Found ${chatIds.size} chats by phone")
        }
    }

    private fun observeChats() {
        chatRepository.observeUserChats { chats ->
            if (chats.isEmpty()) {
                showEmptyState()
            } else {
                showChatList(chats)
            }
        }
    }

    private fun showChatList(chats: List<Chat>) {
        binding.rvChats.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        chatAdapter.submitList(chats)
    }

    private fun showEmptyState() {
        binding.rvChats.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
    }

    private fun openChat(chat: Chat) {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_CHAT_ID, chat.id)
            putExtra(ChatActivity.EXTRA_CHAT_NAME, chat.name)
            putExtra(ChatActivity.EXTRA_IS_ONLINE, chat.isOnline)
        }
        startActivity(intent)
    }
    
    override fun onResume() {
        super.onResume()
        // Re-authenticate if needed when returning to fragment
        if (!isAuthenticated) {
            authenticateAndLoadChats()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentDialog?.dismiss()
        _binding = null
    }
}
