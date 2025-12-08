package com.capstone.safepasigai.data.model

/**
 * Represents a single message in a chat.
 */
data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val deliveredAt: Long = 0L,
    val seenAt: Long = 0L,
    val seenBy: List<String> = emptyList(),
    val locationLat: Double? = null,
    val locationLng: Double? = null
) {
    // No-arg constructor for Firebase
    constructor() : this("", "", "", "", "", 0L, MessageType.TEXT, MessageStatus.SENT, 0L, 0L, emptyList(), null, null)
    
    // Backward compatibility
    val isRead: Boolean get() = status == MessageStatus.SEEN
}

enum class MessageType {
    TEXT,
    LOCATION,
    ALERT,
    SYSTEM
}

enum class MessageStatus {
    SENDING,    // Message is being sent
    SENT,       // Message sent to server
    DELIVERED,  // Message delivered to recipient's device
    SEEN        // Message has been read by recipient
}
