package com.capstone.safepasigai.data.model

/**
 * Represents a chat conversation.
 */
data class Chat(
    val id: String = "",
    val name: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastMessageSenderId: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val type: ChatType = ChatType.CONTACT,
    val participants: List<String> = emptyList(),
    val participantPhones: List<String> = emptyList(),  // Phone numbers of participants
    val typingUsers: List<String> = emptyList()  // Users currently typing
) {
    // No-arg constructor for Firebase
    constructor() : this("", "", "", 0L, "", 0, false, 0L, ChatType.CONTACT, emptyList(), emptyList(), emptyList())
    
    /**
     * Get formatted time for last message
     */
    fun getFormattedTime(): String {
        if (lastMessageTime == 0L) return ""
        
        val now = System.currentTimeMillis()
        val diff = now - lastMessageTime
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m"
            diff < 86400_000 -> "${diff / 3600_000}h"
            diff < 604800_000 -> "${diff / 86400_000}d"
            else -> {
                java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                    .format(java.util.Date(lastMessageTime))
            }
        }
    }
}

enum class ChatType {
    CONTACT,      // Emergency contact
    BARANGAY,     // Barangay responder
    GROUP         // Group chat
}
