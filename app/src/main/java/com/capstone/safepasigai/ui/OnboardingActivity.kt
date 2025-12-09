package com.capstone.safepasigai.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.repository.UserRepository
import com.capstone.safepasigai.databinding.ActivityOnboardingBinding
import com.capstone.safepasigai.databinding.ItemOnboardingPageBinding

/**
 * OnboardingActivity - Welcome flow for first-time users.
 * Shows a splash screen with the app logo when launched.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var userRepository: UserRepository
    
    private val pages = listOf(
        OnboardingPage(
            R.drawable.ic_shield_check,
            "Welcome to SafePasig.AI",
            "Your AI-powered safety companion for commuting in Pasig City",
            R.color.blue_bg
        ),
        OnboardingPage(
            R.drawable.ic_mic,
            "Voice Detection",
            "Say \"Saklolo\" or \"Tulong\" and our AI will automatically trigger emergency alerts",
            R.color.success_bg
        ),
        OnboardingPage(
            R.drawable.ic_location,
            "Live Location Tracking",
            "Share your real-time location with emergency contacts during Smart Escort mode",
            R.color.orange_bg
        ),
        OnboardingPage(
            R.drawable.ic_siren,
            "Instant SOS",
            "One tap to alert your emergency contacts and nearby barangay responders",
            R.color.alert_red
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen BEFORE calling super.onCreate()
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        userRepository = UserRepository(this)

        // Keep the splash screen visible while checking onboarding status
        var isReady = false
        splashScreen.setKeepOnScreenCondition { !isReady }
        
        // Check if already completed onboarding
        if (userRepository.isOnboardingComplete()) {
            isReady = true
            navigateToMain()
            return
        }
        
        isReady = true
        
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupIndicators()
        setupButtons()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = OnboardingAdapter(pages)
        
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                updateButtons(position)
            }
        })
    }

    private fun setupIndicators() {
        val indicators = mutableListOf<View>()
        
        for (i in pages.indices) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(8.dpToPx(), 8.dpToPx()).apply {
                    marginEnd = 8.dpToPx()
                }
                background = ContextCompat.getDrawable(context, R.drawable.bg_white_rounded)
                backgroundTintList = ContextCompat.getColorStateList(
                    context,
                    if (i == 0) R.color.pasig_dark else R.color.gray_200
                )
            }
            indicators.add(dot)
            binding.indicatorContainer.addView(dot)
        }
    }

    private fun updateIndicators(position: Int) {
        for (i in 0 until binding.indicatorContainer.childCount) {
            val dot = binding.indicatorContainer.getChildAt(i)
            dot.backgroundTintList = ContextCompat.getColorStateList(
                this,
                if (i == position) R.color.pasig_dark else R.color.gray_200
            )
            
            // Animate selected indicator
            val scale = if (i == position) 1.5f else 1.0f
            dot.animate().scaleX(scale).scaleY(scale).setDuration(200).start()
        }
    }

    private fun updateButtons(position: Int) {
        val isLastPage = position == pages.size - 1
        binding.btnNext.text = if (isLastPage) "Get Started" else "Next"
    }

    private fun setupButtons() {
        binding.btnSkip.setOnClickListener {
            userRepository.completeOnboarding()
            navigateToMain()
        }

        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < pages.size - 1) {
                binding.viewPager.setCurrentItem(currentItem + 1, true)
            } else {
                // Go to profile setup first
                navigateToProfileSetup()
            }
        }
    }

    private fun navigateToProfileSetup() {
        startActivity(Intent(this, ProfileSetupActivity::class.java))
        finish()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    data class OnboardingPage(
        val iconRes: Int,
        val title: String,
        val description: String,
        val bgColorRes: Int
    )

    inner class OnboardingAdapter(
        private val pages: List<OnboardingPage>
    ) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val binding = ItemOnboardingPageBinding.inflate(
                layoutInflater, parent, false
            )
            return PageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(pages[position])
        }

        override fun getItemCount() = pages.size

        inner class PageViewHolder(
            private val binding: ItemOnboardingPageBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(page: OnboardingPage) {
                binding.ivIcon.setImageResource(page.iconRes)
                binding.tvTitle.text = page.title
                binding.tvDescription.text = page.description
                binding.iconCard.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, page.bgColorRes)
                )
                
                // Tint icon appropriately
                val iconTint = when (page.bgColorRes) {
                    R.color.alert_red -> R.color.white
                    else -> R.color.pasig_dark
                }
                binding.ivIcon.setColorFilter(
                    ContextCompat.getColor(itemView.context, iconTint)
                )
            }
        }
    }
}
