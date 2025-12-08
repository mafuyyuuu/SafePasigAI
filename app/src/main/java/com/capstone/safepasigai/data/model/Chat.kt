package com.capstone.safepasigai.data.model

/**
 * Represents a chat conversation.
 */
data class Chat(
    val id: String = "",
    val name: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val type: ChatType = ChatType.CONTACT,
    val participants: List<String> = emptyList()
) {
    // No-arg constructor for Firebase
    constructor() : this("", "", "", 0L, 0, false, ChatType.CONTACT, emptyList())
}

enum class ChatType {
    CONTACT,      // Emergency contact
    BARANGAY,     // Barangay responder
    GROUP         // Group chat
}
