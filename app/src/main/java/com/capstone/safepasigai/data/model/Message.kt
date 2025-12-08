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
    val isRead: Boolean = false,
    val locationLat: Double? = null,
    val locationLng: Double? = null
) {
    // No-arg constructor for Firebase
    constructor() : this("", "", "", "", "", 0L, MessageType.TEXT, false, null, null)
}

enum class MessageType {
    TEXT,
    LOCATION,
    ALERT,
    SYSTEM
}
