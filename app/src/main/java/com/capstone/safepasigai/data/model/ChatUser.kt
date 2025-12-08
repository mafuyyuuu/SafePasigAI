package com.capstone.safepasigai.data.model

/**
 * Represents a user in the chat system.
 */
data class ChatUser(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val role: UserRole = UserRole.USER,
    val barangay: String = ""
) {
    // No-arg constructor for Firebase
    constructor() : this("", "", "", false, 0L, UserRole.USER, "")
}

enum class UserRole {
    USER,
    RESPONDER,
    ADMIN
}
