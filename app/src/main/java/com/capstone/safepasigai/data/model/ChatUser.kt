package com.capstone.safepasigai.data.model

/**
 * Represents a user in the chat system.
 */
data class ChatUser(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val isTyping: Boolean = false,
    val typingInChat: String = "",  // Which chat they're typing in
    val role: UserRole = UserRole.USER,
    val barangay: String = "",
    val fcmToken: String = ""  // For push notifications
) {
    // No-arg constructor for Firebase
    constructor() : this("", "", "", "", false, 0L, false, "", UserRole.USER, "", "")
    
    /**
     * Get formatted last seen text
     */
    fun getLastSeenText(): String {
        if (isOnline) return "Online"
        if (lastSeen == 0L) return "Offline"
        
        val now = System.currentTimeMillis()
        val diff = now - lastSeen
        
        return when {
            diff < 60_000 -> "Last seen just now"
            diff < 3600_000 -> "Last seen ${diff / 60_000}m ago"
            diff < 86400_000 -> "Last seen ${diff / 3600_000}h ago"
            diff < 604800_000 -> "Last seen ${diff / 86400_000}d ago"
            else -> {
                val date = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                    .format(java.util.Date(lastSeen))
                "Last seen $date"
            }
        }
    }
}

enum class UserRole {
    USER,
    RESPONDER,
    ADMIN
}
