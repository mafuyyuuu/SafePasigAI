package com.capstone.safepasigai.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.capstone.safepasigai.data.model.UserProfile
import com.google.gson.Gson

/**
 * Repository for user profile management.
 */
class UserRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "user_profile"
        private const val KEY_PROFILE = "profile"
        private const val KEY_ONBOARDING = "onboarding_complete"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Get the current user profile.
     */
    fun getProfile(): UserProfile? {
        val json = prefs.getString(KEY_PROFILE, null) ?: return null
        return try {
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save user profile.
     */
    fun saveProfile(profile: UserProfile): Boolean {
        return try {
            val json = gson.toJson(profile)
            prefs.edit()
                .putString(KEY_PROFILE, json)
                .putBoolean(KEY_ONBOARDING, profile.isOnboardingComplete)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if onboarding is complete.
     */
    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING, false)
    }

    /**
     * Mark onboarding as complete.
     */
    fun completeOnboarding() {
        val profile = getProfile()?.copy(isOnboardingComplete = true)
            ?: UserProfile(isOnboardingComplete = true)
        saveProfile(profile)
    }

    /**
     * Get user name for display.
     */
    fun getUserName(): String {
        return getProfile()?.name ?: "User"
    }

    /**
     * Get user's barangay.
     */
    fun getUserBarangay(): String {
        return getProfile()?.barangay ?: "Unknown"
    }

    /**
     * Clear all user data (for logout/reset).
     */
    fun clearData() {
        prefs.edit().clear().apply()
    }
}
