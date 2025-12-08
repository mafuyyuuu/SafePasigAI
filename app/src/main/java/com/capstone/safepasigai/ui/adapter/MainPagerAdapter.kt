package com.capstone.safepasigai.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.capstone.safepasigai.ui.ChatsFragment
import com.capstone.safepasigai.ui.ContactsFragment
import com.capstone.safepasigai.ui.HomeFragment
import com.capstone.safepasigai.ui.SettingsFragment

/**
 * ViewPager2 adapter for main navigation tabs.
 * Tab order: Home (0), Contacts (1), Chats (2), Settings (3)
 * Note: SOS button is handled separately (center of nav bar)
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    companion object {
        const val TAB_HOME = 0
        const val TAB_CONTACTS = 1
        const val TAB_CHATS = 2
        const val TAB_SETTINGS = 3
        const val TOTAL_TABS = 4
    }

    override fun getItemCount(): Int = TOTAL_TABS

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            TAB_HOME -> HomeFragment()
            TAB_CONTACTS -> ContactsFragment()
            TAB_CHATS -> ChatsFragment()
            TAB_SETTINGS -> SettingsFragment()
            else -> HomeFragment()
        }
    }
}
