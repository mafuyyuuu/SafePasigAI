package com.capstone.safepasigai.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.capstone.safepasigai.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start Smart Escort button
        binding.btnStartEscort.setOnClickListener {
            Toast.makeText(context, "Initializing Sensors...", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireActivity(), MonitoringActivity::class.java)
            startActivity(intent)
        }
        
        // Settings button
        binding.btnSettings.setOnClickListener {
            val intent = Intent(requireActivity(), SettingsActivity::class.java)
            startActivity(intent)
        }
        
        // SOS button (floating)
        binding.fabSOS.setOnClickListener {
            val intent = Intent(requireActivity(), SOSActivity::class.java)
            intent.putExtra("REASON", "MANUAL SOS")
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}