package com.capstone.safepasigai.data.model

/**
 * Emergency contact for SOS alerts.
 */
data class EmergencyContact(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val relationship: String = "",
    val avatarUri: String = "",  // Contact photo URI
    val isPrimary: Boolean = false,
    val notifyOnSOS: Boolean = true,
    val shareLocation: Boolean = true
) {
    constructor() : this("", "", "", "", "", false, true, true)
    
    fun getInitial(): String = name.firstOrNull()?.uppercase() ?: "?"
    
    fun getDisplayRelationship(): String = relationship.ifEmpty { "Emergency Contact" }
}
