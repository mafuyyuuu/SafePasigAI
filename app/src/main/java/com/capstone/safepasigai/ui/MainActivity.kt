package com.capstone.safepasigai.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.capstone.safepasigai.R
import com.capstone.safepasigai.databinding.ActivityMainBinding
import com.capstone.safepasigai.ui.adapter.MainPagerAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupBottomNavigation()
        setupSOSButton()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = MainPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 2
        
        // Sync bottom nav with ViewPager swipes
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabSelection(position)
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.tabHome.setOnClickListener { 
            binding.viewPager.setCurrentItem(MainPagerAdapter.TAB_HOME, true)
        }
        binding.tabContacts.setOnClickListener { 
            binding.viewPager.setCurrentItem(MainPagerAdapter.TAB_CONTACTS, true)
        }
        binding.tabChats.setOnClickListener { 
            binding.viewPager.setCurrentItem(MainPagerAdapter.TAB_CHATS, true)
        }
        binding.tabSettings.setOnClickListener { 
            binding.viewPager.setCurrentItem(MainPagerAdapter.TAB_SETTINGS, true)
        }
    }

    private fun setupSOSButton() {
        binding.fabSOS.setOnClickListener {
            val intent = Intent(this, SOSActivity::class.java)
            intent.putExtra("REASON", "MANUAL SOS")
            startActivity(intent)
        }
    }

    private fun updateTabSelection(selectedPosition: Int) {
        val activeColor = ContextCompat.getColor(this, R.color.pasig_dark)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)
        val activeBg = ContextCompat.getColor(this, R.color.blue_bg)
        val transparentBg = android.graphics.Color.TRANSPARENT

        // Reset all icons
        binding.iconHome.apply {
            setColorFilter(inactiveColor)
            backgroundTintList = android.content.res.ColorStateList.valueOf(transparentBg)
            setPadding(0, 0, 0, 0)
            layoutParams.width = resources.getDimensionPixelSize(R.dimen.icon_normal)
            layoutParams.height = resources.getDimensionPixelSize(R.dimen.icon_normal)
        }
        binding.iconContacts.setColorFilter(inactiveColor)
        binding.iconChats.setColorFilter(inactiveColor)
        binding.iconSettings.setColorFilter(inactiveColor)

        // Highlight selected
        when (selectedPosition) {
            MainPagerAdapter.TAB_HOME -> {
                binding.iconHome.apply {
                    setColorFilter(activeColor)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(activeBg)
                    val padding = resources.getDimensionPixelSize(R.dimen.icon_padding)
                    setPadding(padding, padding, padding, padding)
                    layoutParams.width = resources.getDimensionPixelSize(R.dimen.icon_selected)
                    layoutParams.height = resources.getDimensionPixelSize(R.dimen.icon_selected)
                }
            }
            MainPagerAdapter.TAB_CONTACTS -> binding.iconContacts.setColorFilter(activeColor)
            MainPagerAdapter.TAB_CHATS -> binding.iconChats.setColorFilter(activeColor)
            MainPagerAdapter.TAB_SETTINGS -> binding.iconSettings.setColorFilter(activeColor)
        }
    }
}