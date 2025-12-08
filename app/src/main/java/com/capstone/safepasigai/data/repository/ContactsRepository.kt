package com.capstone.safepasigai.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.capstone.safepasigai.data.model.EmergencyContact
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing emergency contacts.
 * Uses SharedPreferences for local storage.
 */
class ContactsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "emergency_contacts"
        private const val KEY_CONTACTS = "contacts_list"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Get all emergency contacts.
     */
    fun getContacts(): List<EmergencyContact> {
        val json = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()
        val type = object : TypeToken<List<EmergencyContact>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save a new contact or update existing one.
     */
    fun saveContact(contact: EmergencyContact): Boolean {
        val contacts = getContacts().toMutableList()
        
        // Check if updating existing
        val existingIndex = contacts.indexOfFirst { it.id == contact.id }
        if (existingIndex >= 0) {
            contacts[existingIndex] = contact
        } else {
            // New contact - generate ID
            val newContact = contact.copy(id = System.currentTimeMillis().toString())
            contacts.add(newContact)
        }
        
        return saveContacts(contacts)
    }

    /**
     * Delete a contact by ID.
     */
    fun deleteContact(contactId: String): Boolean {
        val contacts = getContacts().toMutableList()
        contacts.removeAll { it.id == contactId }
        return saveContacts(contacts)
    }

    /**
     * Get primary contact for SOS.
     */
    fun getPrimaryContact(): EmergencyContact? {
        return getContacts().firstOrNull { it.isPrimary }
            ?: getContacts().firstOrNull()
    }

    /**
     * Get all contacts that should be notified on SOS.
     */
    fun getSOSContacts(): List<EmergencyContact> {
        return getContacts().filter { it.notifyOnSOS }
    }

    /**
     * Set a contact as primary.
     */
    fun setPrimaryContact(contactId: String): Boolean {
        val contacts = getContacts().map { contact ->
            contact.copy(isPrimary = contact.id == contactId)
        }
        return saveContacts(contacts)
    }

    private fun saveContacts(contacts: List<EmergencyContact>): Boolean {
        return try {
            val json = gson.toJson(contacts)
            prefs.edit().putString(KEY_CONTACTS, json).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if user has at least one emergency contact.
     */
    fun hasContacts(): Boolean = getContacts().isNotEmpty()

    /**
     * Add sample contacts for demo purposes.
     */
    fun addDemoContacts() {
        if (hasContacts()) return
        
        val demoContacts = listOf(
            EmergencyContact(
                id = "1",
                name = "Mom",
                phone = "+639171234567",
                relationship = "Mother",
                isPrimary = true
            ),
            EmergencyContact(
                id = "2",
                name = "Dad",
                phone = "+639181234567",
                relationship = "Father"
            )
        )
        
        saveContacts(demoContacts)
    }
}
