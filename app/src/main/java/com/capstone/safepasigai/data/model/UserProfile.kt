package com.capstone.safepasigai.data.model

/**
 * User profile for the app.
 */
data class UserProfile(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val barangay: String = "",
    val bloodType: String = "",
    val medicalConditions: String = "",
    val allergies: String = "",
    val emergencyNotes: String = "",
    val avatarUri: String = "",  // Profile photo URI
    val isOnboardingComplete: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
    
    fun getInitial(): String = name.firstOrNull()?.uppercase() ?: "?"
}
