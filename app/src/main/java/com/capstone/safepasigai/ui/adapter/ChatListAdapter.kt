package com.capstone.safepasigai.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.model.Chat
import com.capstone.safepasigai.data.model.ChatType
import com.capstone.safepasigai.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatListAdapter(
    private val onChatClick: (Chat) -> Unit
) : ListAdapter<Chat, ChatListAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(
        private val binding: ItemChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            binding.tvName.text = chat.name
            binding.tvLastMessage.text = chat.lastMessage.ifEmpty { "Start a conversation" }
            binding.tvInitial.text = chat.name.firstOrNull()?.uppercase() ?: "?"
            
            // Format time
            if (chat.lastMessageTime > 0) {
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                binding.tvTime.text = sdf.format(Date(chat.lastMessageTime))
                binding.tvTime.visibility = View.VISIBLE
            } else {
                binding.tvTime.visibility = View.GONE
            }
            
            // Unread count
            if (chat.unreadCount > 0) {
                binding.tvUnread.text = chat.unreadCount.toString()
                binding.tvUnread.visibility = View.VISIBLE
            } else {
                binding.tvUnread.visibility = View.GONE
            }
            
            // Online indicator
            binding.onlineIndicator.visibility = if (chat.isOnline) View.VISIBLE else View.GONE
            
            // Background tint based on type
            val bgColor = when (chat.type) {
                ChatType.BARANGAY -> R.color.success_bg
                ChatType.GROUP -> R.color.orange_bg
                else -> R.color.pasig_light
            }
            binding.tvInitial.setBackgroundColor(
                itemView.context.getColor(bgColor)
            )
            
            itemView.setOnClickListener { onChatClick(chat) }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Chat, newItem: Chat) = oldItem == newItem
    }
}
