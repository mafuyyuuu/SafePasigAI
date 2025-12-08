package com.capstone.safepasigai

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

/**
 * Application class for SafePasig AI.
 * Initializes Firebase with the correct Realtime Database URL.
 */
class SafePasigApplication : Application() {

    companion object {
        private const val TAG = "SafePasigApp"
        const val DATABASE_URL = "https://safepasigai-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Initializing SafePasig AI...")
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized")
            
            // Initialize Realtime Database with correct URL and enable persistence
            val database = FirebaseDatabase.getInstance(DATABASE_URL)
            try {
                database.setPersistenceEnabled(true)
                Log.d(TAG, "Firebase persistence enabled")
            } catch (e: Exception) {
                // Persistence already enabled, ignore
                Log.w(TAG, "Persistence already set: ${e.message}")
            }
            
            Log.d(TAG, "Firebase Database URL: $DATABASE_URL")
            
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed: ${e.message}", e)
        }
    }
}
