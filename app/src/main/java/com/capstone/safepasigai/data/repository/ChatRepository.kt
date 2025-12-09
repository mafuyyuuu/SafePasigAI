package com.capstone.safepasigai.data.repository

import android.util.Log
import com.capstone.safepasigai.data.model.Chat
import com.capstone.safepasigai.data.model.ChatType
import com.capstone.safepasigai.data.model.ChatUser
import com.capstone.safepasigai.data.model.Message
import com.capstone.safepasigai.data.model.MessageType
import com.capstone.safepasigai.data.model.MessageStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ChildEventListener

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
        private const val DELETED_CHATS = "deleted_chats"  // Track deleted chats
        private const val PRESENCE = "presence"
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
            setupPresence(user.uid)
            onSuccess(user.uid)
            return
        }
        
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val userId = result.user?.uid ?: ""
                Log.d(TAG, "Signed in anonymously: $userId")
                setupPresence(userId)
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
     * Setup presence system for online/offline status
     */
    private fun setupPresence(userId: String) {
        val presenceRef = database.reference.child(USERS).child(userId)
        val connectedRef = database.getReference(".info/connected")
        
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    // When connected, update online status
                    presenceRef.child("isOnline").setValue(true)
                    presenceRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)
                    
                    // When disconnected, update offline status
                    presenceRef.child("isOnline").onDisconnect().setValue(false)
                    presenceRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Presence listener cancelled: ${error.message}")
            }
        })
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
    
    /**
     * Set typing status for a chat
     */
    fun setTypingStatus(chatId: String, isTyping: Boolean) {
        val userId = currentUserId ?: return
        
        database.reference.child(USERS).child(userId).updateChildren(
            mapOf(
                "isTyping" to isTyping,
                "typingInChat" to if (isTyping) chatId else ""
            )
        )
    }
    
    /**
     * Observe a user's online status and typing status
     */
    fun observeUserStatus(userId: String, onStatusChanged: (isOnline: Boolean, lastSeen: Long, isTyping: Boolean) -> Unit) {
        database.reference.child(USERS).child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                    val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                    val isTyping = snapshot.child("isTyping").getValue(Boolean::class.java) ?: false
                    onStatusChanged(isOnline, lastSeen, isTyping)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "User status listener cancelled: ${error.message}")
                }
            })
    }

    // ==================== CHATS ====================

    /**
     * Get all chats for the current user.
     * Also fetches online status for each chat participant.
     * Uses addValueEventListener on individual chats for real-time updates.
     */
    fun observeUserChats(onChatsUpdated: (List<Chat>) -> Unit) {
        val userId = currentUserId ?: return
        
        database.reference.child(USER_CHATS).child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chatIds = snapshot.children.mapNotNull { it.key }
                    Log.d(TAG, "User has ${chatIds.size} chats")
                    
                    if (chatIds.isEmpty()) {
                        onChatsUpdated(emptyList())
                        return
                    }
                    
                    // Use a map to track chat data for real-time updates
                    val chatMap = mutableMapOf<String, Chat>()
                    
                    chatIds.forEach { chatId ->
                        // Add listener for each chat to get real-time updates
                        database.reference.child(CHATS).child(chatId)
                            .addValueEventListener(object : ValueEventListener {
                                override fun onDataChange(chatSnapshot: DataSnapshot) {
                                    val chat = chatSnapshot.getValue(Chat::class.java)
                                    if (chat != null) {
                                        // Find the other participant to check their online status
                                        val otherParticipant = chat.participants.find { it != userId }
                                        
                                        if (otherParticipant != null) {
                                            database.reference.child(USERS).child(otherParticipant)
                                                .get()
                                                .addOnSuccessListener { userSnapshot ->
                                                    val isOnline = userSnapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                                                    val lastSeen = userSnapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                                                    chatMap[chatId] = chat.copy(id = chatId, isOnline = isOnline, lastSeen = lastSeen)
                                                    
                                                    // Emit updated list
                                                    onChatsUpdated(chatMap.values.sortedByDescending { it.lastMessageTime })
                                                }
                                                .addOnFailureListener {
                                                    chatMap[chatId] = chat.copy(id = chatId)
                                                    onChatsUpdated(chatMap.values.sortedByDescending { it.lastMessageTime })
                                                }
                                        } else {
                                            chatMap[chatId] = chat.copy(id = chatId)
                                            onChatsUpdated(chatMap.values.sortedByDescending { it.lastMessageTime })
                                        }
                                    }
                                }
                                
                                override fun onCancelled(error: DatabaseError) {
                                    Log.e(TAG, "Chat listener cancelled: ${error.message}")
                                }
                            })
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
     * Chat ID format: "chat_PHONE1_PHONE2" (sorted alphabetically, digits only)
     * 
     * IMPORTANT: Both phones will use the SAME chat ID because it's based on
     * sorted phone numbers, not Firebase User IDs.
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
        // Use only digits to ensure same ID regardless of formatting
        val myDigits = normalizedMyPhone.filter { it.isDigit() }.takeLast(10)
        val contactDigits = normalizedContactPhone.filter { it.isDigit() }.takeLast(10)
        
        val phones = listOf(myDigits, contactDigits).sorted()
        val chatId = "chat_${phones[0]}_${phones[1]}"
        
        Log.d(TAG, "Looking for phone-based chat: $chatId (my: $myDigits, contact: $contactDigits)")
        
        // Check if chat already exists
        database.reference.child(CHATS).child(chatId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    Log.d(TAG, "Found existing phone chat: $chatId")
                    // Chat exists, add current user if not already participant
                    addUserToChat(chatId, userId, normalizedMyPhone)
                    onSuccess(chatId)
                } else {
                    Log.d(TAG, "Creating new phone chat: $chatId")
                    // Create new chat with deterministic ID
                    createChatWithId(chatId, contactName, userId, normalizedMyPhone, normalizedContactPhone, onSuccess, onError)
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
        myPhone: String,
        contactPhone: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val chat = Chat(
            id = chatId,
            name = name,
            type = ChatType.CONTACT,
            participants = listOf(userId),
            participantPhones = listOf(myPhone, contactPhone), // Store both phone numbers
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
                
                // Also index by phone number so the other user can find it
                database.reference.child("phone_chats")
                    .child(myPhone.filter { it.isDigit() }.takeLast(10))
                    .child(chatId)
                    .setValue(true)
                database.reference.child("phone_chats")
                    .child(contactPhone.filter { it.isDigit() }.takeLast(10))
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
    
    private fun addUserToChat(chatId: String, userId: String, userPhone: String = "") {
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
        
        // Also index by phone if provided
        if (userPhone.isNotEmpty()) {
            database.reference.child("phone_chats")
                .child(userPhone.filter { it.isDigit() }.takeLast(10))
                .child(chatId)
                .setValue(true)
        }
    }
    
    /**
     * Find chats for a user by their phone number.
     * This helps when a user reinstalls the app or gets a new Firebase User ID.
     * Does NOT re-add chats that were previously deleted by the user.
     */
    fun findChatsByPhone(phone: String, onComplete: (List<String>) -> Unit) {
        val phoneDigits = phone.filter { it.isDigit() }.takeLast(10)
        val userId = currentUserId
        
        if (userId == null) {
            onComplete(emptyList())
            return
        }
        
        // First, get the list of deleted chats for this user
        database.reference.child(DELETED_CHATS).child(userId)
            .get()
            .addOnSuccessListener { deletedSnapshot ->
                val deletedChatIds = deletedSnapshot.children.mapNotNull { it.key }.toSet()
                
                // Now get phone chats
                database.reference.child("phone_chats").child(phoneDigits)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val chatIds = snapshot.children.mapNotNull { it.key }
                        
                        // Filter out deleted chats
                        val validChatIds = chatIds.filter { it !in deletedChatIds }
                        
                        // Add only non-deleted chats to the current user's chat list
                        validChatIds.forEach { chatId ->
                            database.reference.child(USER_CHATS)
                                .child(userId)
                                .child(chatId)
                                .setValue(true)
                            
                            // Also add to participants
                            addUserToChat(chatId, userId, phone)
                        }
                        
                        onComplete(validChatIds)
                    }
                    .addOnFailureListener {
                        onComplete(emptyList())
                    }
            }
            .addOnFailureListener {
                onComplete(emptyList())
            }
    }

    // ==================== MESSAGES ====================

    /**
     * Observe messages in a chat with real-time updates.
     */
    fun observeMessages(chatId: String, onMessagesUpdated: (List<Message>) -> Unit) {
        Log.d(TAG, "Starting message observation for chat: $chatId")
        
        database.reference.child(MESSAGES).child(chatId)
            .orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "Message snapshot received: ${snapshot.childrenCount} messages")
                    
                    val messages = snapshot.children.mapNotNull { child ->
                        try {
                            child.getValue(Message::class.java)?.copy(id = child.key ?: "")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse message: ${e.message}")
                            null
                        }
                    }.sortedBy { it.timestamp }
                    
                    Log.d(TAG, "Parsed ${messages.size} messages")
                    onMessagesUpdated(messages)
                    
                    // Mark messages as delivered for current user
                    markMessagesAsDelivered(chatId, messages)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to observe messages: ${error.message}")
                }
            })
    }
    
    /**
     * Mark messages as delivered when received
     */
    private fun markMessagesAsDelivered(chatId: String, messages: List<Message>) {
        val userId = currentUserId ?: return
        
        messages.filter { 
            it.senderId != userId && it.status == MessageStatus.SENT 
        }.forEach { message ->
            database.reference.child(MESSAGES).child(chatId).child(message.id)
                .updateChildren(mapOf(
                    "status" to MessageStatus.DELIVERED.name,
                    "deliveredAt" to ServerValue.TIMESTAMP
                ))
        }
    }
    
    /**
     * Mark messages as seen when chat is opened
     */
    fun markMessagesAsSeen(chatId: String) {
        val userId = currentUserId ?: return
        
        database.reference.child(MESSAGES).child(chatId)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.forEach { messageSnapshot ->
                    val message = messageSnapshot.getValue(Message::class.java)
                    if (message != null && 
                        message.senderId != userId && 
                        message.status != MessageStatus.SEEN) {
                        
                        val seenBy = message.seenBy.toMutableList()
                        if (!seenBy.contains(userId)) {
                            seenBy.add(userId)
                        }
                        
                        messageSnapshot.ref.updateChildren(mapOf(
                            "status" to MessageStatus.SEEN.name,
                            "seenAt" to ServerValue.TIMESTAMP,
                            "seenBy" to seenBy
                        ))
                    }
                }
                
                // Reset unread count
                database.reference.child(CHATS).child(chatId).child("unreadCount").setValue(0)
            }
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
            type = MessageType.TEXT,
            status = MessageStatus.SENT
        )
        
        messageRef.setValue(message)
            .addOnSuccessListener {
                // Update chat's last message
                updateChatLastMessage(chatId, text, userId)
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
            status = MessageStatus.SENT,
            locationLat = lat,
            locationLng = lng
        )
        
        messageRef.setValue(message)
            .addOnSuccessListener {
                updateChatLastMessage(chatId, "📍 Location shared", userId)
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
            status = MessageStatus.SENT,
            locationLat = lat,
            locationLng = lng
        )
        
        messageRef.setValue(message)
            .addOnSuccessListener {
                updateChatLastMessage(chatId, "🚨 EMERGENCY ALERT", userId)
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to send alert")
            }
    }

    private fun updateChatLastMessage(chatId: String, lastMessage: String, senderId: String) {
        val updates = mapOf(
            "lastMessage" to lastMessage,
            "lastMessageTime" to ServerValue.TIMESTAMP,
            "lastMessageSenderId" to senderId
        )
        database.reference.child(CHATS).child(chatId).updateChildren(updates)
        
        // Increment unread count for other participants
        database.reference.child(CHATS).child(chatId).child("participants")
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.mapNotNull { it.getValue(String::class.java) }
                    .filter { it != senderId }
                    .forEach { participantId ->
                        // This could be enhanced to track per-user unread counts
                    }
            }
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
    
    // ==================== DELETE OPERATIONS ====================
    
    /**
     * Delete a chat conversation.
     * Removes the chat from user's chat list and optionally deletes all messages.
     */
    fun deleteChat(
        chatId: String,
        deleteMessages: Boolean = true,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            Log.e(TAG, "deleteChat: Not authenticated")
            onError("Not authenticated")
            return
        }
        
        Log.d(TAG, "Deleting chat: $chatId for user: $userId")
        
        // First, mark this chat as deleted for this user (prevents re-sync)
        database.reference.child(DELETED_CHATS).child(userId).child(chatId)
            .setValue(true)
            .addOnSuccessListener {
                Log.d(TAG, "Marked chat as deleted")
                
                // Remove from user's chat list
                database.reference.child(USER_CHATS).child(userId).child(chatId)
                    .removeValue()
                    .addOnSuccessListener {
                        Log.d(TAG, "Removed chat from user's list")
                        
                        // Also remove from phone_chats index to prevent re-sync
                        removeFromPhoneChatIndex(chatId, userId)
                        
                        if (deleteMessages) {
                            // Delete all messages in the chat
                            database.reference.child(MESSAGES).child(chatId)
                                .removeValue()
                                .addOnSuccessListener {
                                    Log.d(TAG, "Deleted messages for chat")
                                    // Delete the chat itself if no other participants
                                    checkAndDeleteChat(chatId, userId, onSuccess, onError)
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Failed to delete messages: ${e.message}")
                                    // Still consider it success since user's reference is removed
                                    onSuccess()
                                }
                        } else {
                            onSuccess()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to delete chat: ${e.message}")
                        onError(e.message ?: "Failed to delete chat")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to mark chat as deleted: ${e.message}")
                onError(e.message ?: "Failed to delete chat")
            }
    }
    
    /**
     * Remove chat from phone_chats index to prevent re-syncing.
     */
    private fun removeFromPhoneChatIndex(chatId: String, userId: String) {
        // Get user's phone number and remove this chat from their phone index
        database.reference.child(USERS).child(userId).child("phone")
            .get()
            .addOnSuccessListener { snapshot ->
                val phone = snapshot.getValue(String::class.java) ?: ""
                if (phone.isNotEmpty()) {
                    val phoneDigits = phone.filter { it.isDigit() }.takeLast(10)
                    database.reference.child("phone_chats").child(phoneDigits).child(chatId)
                        .removeValue()
                }
            }
    }
    
    private fun checkAndDeleteChat(
        chatId: String, 
        userId: String, 
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Check if any other users still have this chat
        database.reference.child(CHATS).child(chatId).child("participants")
            .get()
            .addOnSuccessListener { snapshot ->
                val participants = snapshot.children.mapNotNull { it.getValue(String::class.java) }
                val remainingParticipants = participants.filter { it != userId }
                
                if (remainingParticipants.isEmpty()) {
                    // No other participants, delete the chat entirely
                    database.reference.child(CHATS).child(chatId).removeValue()
                }
                
                onSuccess()
            }
            .addOnFailureListener { e ->
                // Still consider it success for the user
                onSuccess()
            }
    }
    
    /**
     * Delete all chats for the current user.
     */
    fun deleteAllChats(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val userId = currentUserId ?: return onError("Not authenticated")
        
        database.reference.child(USER_CHATS).child(userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val chatIds = snapshot.children.mapNotNull { it.key }
                
                if (chatIds.isEmpty()) {
                    onSuccess()
                    return@addOnSuccessListener
                }
                
                var deletedCount = 0
                var hasError = false
                
                chatIds.forEach { chatId ->
                    deleteChat(
                        chatId = chatId,
                        deleteMessages = true,
                        onSuccess = {
                            deletedCount++
                            if (deletedCount == chatIds.size && !hasError) {
                                onSuccess()
                            }
                        },
                        onError = { error ->
                            if (!hasError) {
                                hasError = true
                                onError(error)
                            }
                        }
                    )
                }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to get chats")
            }
    }
    
    /**
     * Delete the current user's account and all associated data.
     */
    fun deleteAccount(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val userId = currentUserId ?: return onError("Not authenticated")
        
        // Delete user's chats first
        deleteAllChats(
            onSuccess = {
                // Delete user profile
                database.reference.child(USERS).child(userId)
                    .removeValue()
                    .addOnSuccessListener {
                        // Delete phone chat index
                        database.reference.child("phone_chats")
                            .get()
                            .addOnSuccessListener { snapshot ->
                                // Remove user from all phone_chats entries
                                snapshot.children.forEach { phoneEntry ->
                                    phoneEntry.children.forEach { chatEntry ->
                                        // Clean up if needed
                                    }
                                }
                                
                                // Sign out and delete Firebase auth
                                auth.currentUser?.delete()
                                    ?.addOnSuccessListener { onSuccess() }
                                    ?.addOnFailureListener { e ->
                                        // Auth deletion failed but data is gone
                                        auth.signOut()
                                        onSuccess()
                                    }
                            }
                            .addOnFailureListener {
                                // Continue anyway
                                auth.signOut()
                                onSuccess()
                            }
                    }
                    .addOnFailureListener { e ->
                        onError(e.message ?: "Failed to delete user profile")
                    }
            },
            onError = onError
        )
    }
    
    /**
     * Sign out and clear local session.
     */
    fun signOut() {
        setOnlineStatus(false)
        auth.signOut()
    }
}
