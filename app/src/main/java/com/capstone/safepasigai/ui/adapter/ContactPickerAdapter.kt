package com.capstone.safepasigai.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.model.EmergencyContact
import java.io.File

/**
 * Adapter for contact picker dialog in chat.
 */
class ContactPickerAdapter(
    private val contacts: List<EmergencyContact>,
    private val onContactSelected: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<ContactPickerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_picker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount() = contacts.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)
        private val tvInitial: TextView = view.findViewById(R.id.tvInitial)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvRelationship: TextView = view.findViewById(R.id.tvRelationship)

        fun bind(contact: EmergencyContact) {
            tvName.text = contact.name
            tvRelationship.text = contact.getDisplayRelationship()
            
            // Avatar
            if (contact.avatarUri.isNotEmpty()) {
                val file = File(contact.avatarUri)
                if (file.exists()) {
                    imgAvatar.visibility = View.VISIBLE
                    tvInitial.visibility = View.GONE
                    Glide.with(itemView.context)
                        .load(file)
                        .circleCrop()
                        .into(imgAvatar)
                } else {
                    showInitial(contact)
                }
            } else {
                showInitial(contact)
            }
            
            itemView.setOnClickListener {
                onContactSelected(contact)
            }
        }
        
        private fun showInitial(contact: EmergencyContact) {
            imgAvatar.visibility = View.GONE
            tvInitial.visibility = View.VISIBLE
            tvInitial.text = contact.getInitial()
        }
    }
}
