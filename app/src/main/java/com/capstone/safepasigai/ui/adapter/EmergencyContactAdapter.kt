package com.capstone.safepasigai.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.capstone.safepasigai.data.model.EmergencyContact
import com.capstone.safepasigai.databinding.ItemEmergencyContactBinding
import java.io.File

class EmergencyContactAdapter(
    private val onCallClick: (EmergencyContact) -> Unit,
    private val onMoreClick: (EmergencyContact, View) -> Unit
) : ListAdapter<EmergencyContact, EmergencyContactAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemEmergencyContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(
        private val binding: ItemEmergencyContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: EmergencyContact) {
            binding.tvName.text = contact.name
            binding.tvPhone.text = contact.phone
            binding.tvRelationship.text = contact.getDisplayRelationship()
            
            // Avatar - show image if available, otherwise initial
            if (contact.avatarUri.isNotEmpty()) {
                val file = File(contact.avatarUri)
                if (file.exists()) {
                    binding.imgAvatar.visibility = View.VISIBLE
                    binding.tvInitial.visibility = View.GONE
                    Glide.with(binding.root.context)
                        .load(file)
                        .circleCrop()
                        .into(binding.imgAvatar)
                } else {
                    showInitial(contact)
                }
            } else {
                showInitial(contact)
            }
            
            // Primary badge
            binding.badgePrimary.visibility = if (contact.isPrimary) View.VISIBLE else View.GONE
            
            // Click listeners
            binding.btnCall.setOnClickListener { onCallClick(contact) }
            binding.btnMore.setOnClickListener { onMoreClick(contact, it) }
        }
        
        private fun showInitial(contact: EmergencyContact) {
            binding.imgAvatar.visibility = View.GONE
            binding.tvInitial.visibility = View.VISIBLE
            binding.tvInitial.text = contact.getInitial()
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<EmergencyContact>() {
        override fun areItemsTheSame(oldItem: EmergencyContact, newItem: EmergencyContact) = 
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: EmergencyContact, newItem: EmergencyContact) = 
            oldItem == newItem
    }
}
