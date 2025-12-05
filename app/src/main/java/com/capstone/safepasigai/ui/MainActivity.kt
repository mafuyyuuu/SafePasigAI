package com.capstone.safepasigai.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.capstone.safepasigai.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // HomeFragment is automatically loaded via android:name in activity_main.xml
    }
}