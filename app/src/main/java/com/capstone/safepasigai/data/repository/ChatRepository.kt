package com.capstone.safepasigai.data.repository

import android.util.Log
import com.capstone.safepasigai.data.model.Chat
import com.capstone.safepasigai.data.model.ChatType
import com.capstone.safepasigai.data.model.ChatUser
import com.capstone.safepasigai.data.model.Message
import com.capstone.safepasigai.data.model.MessageType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ServerValue

import com.capstone.safepasigai.SafePasigApplication

/**
 * Repository for Firebase Realtime Database chat operations.
 * 
 * IMPORTANT: Configure Firebase Realtime Database Rules in Firebase Console:
 * Go to: Firebase Console > Realtime Database > Rules
 * Set these rules:
 * {
 *   "rules": {
 *     ".read": "auth != null",
 *     ".write": "auth != null"
 *   }
 * }
 * 
 * Also enable Anonymous Authentication:
 * Firebase Console > Authentication > Sign-in method > Anonymous > Enable
 */
class ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        private const val CHATS = "chats"
        private const val MESSAGES = "messages"
        private const val USERS = "users"
        private const val USER_CHATS = "user_chats"
    }

    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(SafePasigApplication.DATABASE_URL)
    }
    private val auth = FirebaseAuth.getInstance()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    // ==================== AUTHENTICATION ====================

    /**
     * Sign in anonymously for demo purposes.
     * In production, use proper authentication.
     * 
     * SETUP REQUIRED:
     * 1. Go to Firebase Console > Authentication
     * 2. Click "Sign-in method" tab
     * 3. Enable "Anonymous" provider
     */
    fun signInAnonymously(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        Log.d(TAG, "Attempting anonymous sign in...")
        
        // Check if already signed in
        auth.currentUser?.let { user ->
            Log.d(TAG, "Already signed in: ${user.uid}")
            onSuccess(user.uid)
            return
        }
        
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val userId = result.user?.uid ?: ""
                Log.d(TAG, "Signed in anonymously: $userId")
                onSuccess(userId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Sign in failed: ${e.message}", e)
                // Provide more helpful error message
                val errorMsg = when {
                    e.message?.contains("CONFIGURATION_NOT_FOUND") == true -> 
                        "Enable Anonymous auth in Firebase Console"
                    e.message?.contains("NETWORK") == true -> 
                        "Network error. Check internet connection"
                    else -> e.message ?: "Authentication failed"
                }
                onError(errorMsg)
            }
    }

    /**
     * Create or update user profile.
     */
    fun updateUserProfile(user: ChatUser, onComplete: (Boolean) -> Unit) {
        val userId = currentUserId ?: return onComplete(false)
        
        database.reference.child(USERS).child(userId)
            .setValue(user.copy(id = userId))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * Set user online status.
     */
    fun setOnlineStatus(isOnline: Boolean) {
        val userId = currentUserId ?: return
        
        val updates = mapOf(
            "isOnline" to isOnline,
            "lastSeen" to ServerValue.TIMESTAMP
        )
        
        database.reference.child(USERS).child(userId).updateChildren(updates)
    }

    // ==================== CHATS ====================

    /**
     * Get all chats for the current user.
     */
    fun observeUserChats(onChatsUpdated: (List<Chat>) -> Unit) {
        val userId = currentUserId ?: return
        
        database.reference.child(USER_CHATS).child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chatIds = snapshot.children.mapNotNull { it.key }
                    if (chatIds.isEmpty()) {
                        onChatsUpdated(emptyList())
                        return
                    }
                    
                    // Fetch full chat data for each chat ID
                    val chats = mutableListOf<Chat>()
                    var loadedCount = 0
                    
                    chatIds.forEach { chatId ->
                        database.reference.child(CHATS).child(chatId)
                            .get()
                            .addOnSuccessListener { chatSnapshot ->
                                chatSnapshot.getValue(Chat::class.java)?.let { chat ->
                                    chats.add(chat.copy(id = chatId))
                                }
                                loadedCount++
                                if (loadedCount == chatIds.size) {
                                    onChatsUpdated(chats.sortedByDescending { it.lastMessageTime })
                                }
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to observe chats: ${error.message}")
                }
            })
    }

    /**
     * Create a new chat with a contact or barangay.
     */
    fun createChat(
        name: String,
        type: ChatType,
        participantIds: List<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = currentUserId ?: return onError("Not authenticated")
        
        val chatRef = database.reference.child(CHATS).push()
        val chatId = chatRef.key ?: return onError("Failed to create chat")
        
        val allParticipants = (participantIds + userId).distinct()
        
        val chat = Chat(
            id = chatId,
            name = name,
            type = type,
            participants = allParticipants,
            lastMessageTime = System.currentTimeMillis()
        )
        
        chatRef.setValue(chat)
            .addOnSuccessListener {
                // Add chat reference to all participants
                allParticipants.forEach { participantId ->
                    database.reference.child(USER_CHATS)
                        .child(participantId)
                        .child(chatId)
                        .setValue(true)
                }
                onSuccess(chatId)
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to create chat")
            }
    }

    /**
     * Create or get existing direct chat with a contact.
     * Uses phone number as the shared identifier so both phones can find each other.
     */
    fun getOrCreateDirectChat(
        contactId: String,
        contactName: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = currentUserId ?: return onError("Not authenticated")
        
        // Check if direct chat already exists
        database.reference.child(USER_CHATS).child(userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val existingChatIds = snapshot.children.mapNotNull { it.key }
                
                // Check each chat to find existing direct chat with contact
                var found = false
                var checkedCount = 0
                
                if (existingChatIds.isEmpty()) {
                    createChat(contactName, ChatType.CONTACT, listOf(contactId), onSuccess, onError)
                    return@addOnSuccessListener
                }
                
                existingChatIds.forEach { chatId ->
                    database.reference.child(CHATS).child(chatId).get()
                        .addOnSuccessListener { chatSnapshot ->
                            checkedCount++
                            val chat = chatSnapshot.getValue(Chat::class.java)
                            
                            if (!found && chat != null && 
                                chat.type == ChatType.CONTACT && 
                                chat.participants.contains(contactId)) {
                                found = true
                                onSuccess(chatId)
                            }
                            
                            // If all chats checked and none found, create new
                            if (checkedCount == existingChatIds.size && !found) {
                                createChat(contactName, ChatType.CONTACT, listOf(contactId), onSuccess, onError)
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to check existing chats")
            }
    }
    
    /**
     * Get or create a chat using PHONE NUMBER as the shared identifier.
     * This allows two phones with each other's contacts to chat in the same room.
     * 
     * Chat ID format: "chat_PHONE1_PHONE2" (sorted alphabetically)
     */
    fun getOrCreateChatByPhone(
        myPhone: String,
        contactPhone: String,
        contactName: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = currentUserId ?: return onError("Not authenticated")
        
        if (myPhone.isEmpty() || contactPhone.isEmpty()) {
            // Fall back to regular chat if phones not available
            Log.w(TAG, "Phone numbers empty, falling back to regular chat")
            getOrCreateDirectChat(contactPhone, contactName, onSuccess, onError)
            return
        }
        
        // Normalize phone numbers (remove spaces, dashes, etc.)
        val normalizedMyPhone = normalizePhone(myPhone)
        val normalizedContactPhone = normalizePhone(contactPhone)
        
        // Create deterministic chat ID based on sorted phone numbers
        val phones = listOf(normalizedMyPhone, normalizedContactPhone).sorted()
        val chatId = "chat_${phones[0]}_${phones[1]}"
        
        Log.d(TAG, "Looking for phone-based chat: $chatId")
        
        // Check if chat already exists
        database.reference.child(CHATS).child(chatId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    Log.d(TAG, "Found existing phone chat: $chatId")
                    // Chat exists, add current user if not already participant
                    addUserToChat(chatId, userId)
                    onSuccess(chatId)
                } else {
                    Log.d(TAG, "Creating new phone chat: $chatId")
                    // Create new chat with deterministic ID
                    createChatWithId(chatId, contactName, userId, onSuccess, onError)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check chat: ${e.message}")
                onError(e.message ?: "Failed to check existing chat")
            }
    }
    
    private fun normalizePhone(phone: String): String {
        // Remove all non-digit characters except +
        return phone.replace(Regex("[^0-9+]"), "")
            .let { 
                // If starts with 0, replace with +63 (Philippines)
                if (it.startsWith("0")) "+63${it.substring(1)}" 
                else if (!it.startsWith("+")) "+$it"
                else it 
            }
    }
    
    private fun createChatWithId(
        chatId: String,
        name: String,
        userId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val chat = Chat(
            id = chatId,
            name = name,
            type = ChatType.CONTACT,
            participants = listOf(userId),
            lastMessageTime = System.currentTimeMillis()
        )
        
        database.reference.child(CHATS).child(chatId)
            .setValue(chat)
            .addOnSuccessListener {
                // Add to user's chat list
                database.reference.child(USER_CHATS)
                    .child(userId)
                    .child(chatId)
                    .setValue(true)
                
                Log.d(TAG, "Chat created with ID: $chatId")
                onSuccess(chatId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create chat: ${e.message}")
                onError(e.message ?: "Failed to create chat")
            }
    }
    
    private fun addUserToChat(chatId: String, userId: String) {
        // Add user to participants
        database.reference.child(CHATS).child(chatId).child("participants")
            .get()
            .addOnSuccessListener { snapshot ->
                val participants = snapshot.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
                if (!participants.contains(userId)) {
                    participants.add(userId)
                    database.reference.child(CHATS).child(chatId).child("participants").setValue(participants)
                }
            }
        
        // Add to user's chat list
        database.reference.child(USER_CHATS)
            .child(userId)
            .child(chatId)
            .setValue(true)
    }

    // ==================== MESSAGES ====================

    /**
     * Observe messages in a chat.
     */
    fun observeMessages(chatId: String, onMessagesUpdated: (List<Message>) -> Unit) {
        database.reference.child(MESSAGES).child(chatId)
            .orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = snapshot.children.mapNotNull { 
                        it.getValue(Message::class.java)?.copy(id = it.key ?: "")
                    }
                    onMessagesUpdated(messages)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to observe messages: ${error.message}")
                }
            })
    }

    /**
     * Send a text message.
     */
    fun sendMessage(
        chatId: String,
        text: String,
        senderName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = currentUserId ?: return onError("Not authenticated")
        
        val messageRef = database.reference.child(MESSAGES).child(chatId).push()
        val messageId = messageRef.key ?: return onError("Failed to create message")
        
        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId = userId,
            senderName = senderName,
            text = text,
            timestamp = System.currentTimeMillis(),
            type = MessageType.TEXT
        )
        
        messageRef.setValue(message)
            .addOnSuccessListener {
                // Update chat's last message
                updateChatLastMessage(chatId, text)
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to send message")
            }
    }

    /**
     * Send a location message (for emergency tracking).
     */
    fun sendLocationMessage(
        chatId: String,
        senderName: String,
        lat: Double,
        lng: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = currentUserId ?: return onError("Not authenticated")
        
        val messageRef = database.reference.child(MESSAGES).child(chatId).push()
        val messageId = messageRef.key ?: return onError("Failed to create message")
        
        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId = userId,
            senderName = senderName,
            text = "📍 Location shared",
            timestamp = System.currentTimeMillis(),
            type = MessageType.LOCATION,
            locationLat = lat,
            locationLng = lng
        )
        
        messageRef.setValue(message)
            .addOnSuccessListener {
                updateChatLastMessage(chatId, "📍 Location shared")
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to send location")
            }
    }

    /**
     * Send an alert message (SOS triggered).
     */
    fun sendAlertMessage(
        chatId: String,
        senderName: String,
        alertType: String,
        lat: Double?,
        lng: Double?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = currentUserId ?: return onError("Not authenticated")
        
        val messageRef = database.reference.child(MESSAGES).child(chatId).push()
        val messageId = messageRef.key ?: return onError("Failed to create message")
        
        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId = userId,
            senderName = senderName,
            text = "🚨 EMERGENCY ALERT: $alertType",
            timestamp = System.currentTimeMillis(),
            type = MessageType.ALERT,
            locationLat = lat,
            locationLng = lng
        )
        
        messageRef.setValue(message)
            .addOnSuccessListener {
                updateChatLastMessage(chatId, "🚨 EMERGENCY ALERT")
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to send alert")
            }
    }

    private fun updateChatLastMessage(chatId: String, lastMessage: String) {
        val updates = mapOf(
            "lastMessage" to lastMessage,
            "lastMessageTime" to ServerValue.TIMESTAMP
        )
        database.reference.child(CHATS).child(chatId).updateChildren(updates)
    }

    // ==================== BARANGAY RESPONDERS ====================

    /**
     * Get available barangay responders.
     */
    fun getBarangayResponders(
        barangay: String,
        onSuccess: (List<ChatUser>) -> Unit,
        onError: (String) -> Unit
    ) {
        database.reference.child(USERS)
            .orderByChild("barangay")
            .equalTo(barangay)
            .get()
            .addOnSuccessListener { snapshot ->
                val responders = snapshot.children.mapNotNull { 
                    val user = it.getValue(ChatUser::class.java)
                    if (user?.role == com.capstone.safepasigai.data.model.UserRole.RESPONDER) {
                        user.copy(id = it.key ?: "")
                    } else null
                }
                onSuccess(responders)
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to get responders")
            }
    }

    /**
     * Create a chat with barangay for emergency.
     */
    fun createEmergencyChat(
        barangay: String,
        userName: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val chatName = "$barangay - Emergency"
        createChat(chatName, ChatType.BARANGAY, emptyList(), onSuccess, onError)
    }
}
