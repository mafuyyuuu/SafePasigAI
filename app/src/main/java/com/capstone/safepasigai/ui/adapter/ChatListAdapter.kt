package com.capstone.safepasigai.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
    private val onChatClick: (Chat) -> Unit,
    private val onChatLongClick: ((Chat) -> Unit)? = null,
    private val resolveContactName: ((Chat) -> String)? = null
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
            val context = itemView.context
            
            // Resolve the display name using local contacts if resolver is provided
            val displayName = resolveContactName?.invoke(chat) ?: chat.name
            
            binding.tvName.text = displayName
            binding.tvLastMessage.text = chat.lastMessage.ifEmpty { "Start a conversation" }
            binding.tvInitial.text = displayName.firstOrNull()?.uppercase() ?: "?"
            
            // Format time using the Chat's helper method or manual format
            if (chat.lastMessageTime > 0) {
                binding.tvTime.text = chat.getFormattedTime()
                binding.tvTime.visibility = View.VISIBLE
            } else {
                binding.tvTime.visibility = View.GONE
            }
            
            // Unread count
            if (chat.unreadCount > 0) {
                binding.tvUnread.text = chat.unreadCount.toString()
                binding.tvUnread.visibility = View.VISIBLE
                // Make last message bold when there are unread messages
                binding.tvLastMessage.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            } else {
                binding.tvUnread.visibility = View.GONE
                binding.tvLastMessage.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            
            // Online indicator
            binding.onlineIndicator.visibility = if (chat.isOnline) View.VISIBLE else View.GONE
            
            // Typing indicator in last message
            if (chat.typingUsers.isNotEmpty()) {
                binding.tvLastMessage.text = "typing..."
                binding.tvLastMessage.setTextColor(ContextCompat.getColor(context, R.color.pasig_dark))
            }
            
            // Background tint based on type
            val bgColor = when (chat.type) {
                ChatType.BARANGAY -> R.color.success_bg
                ChatType.GROUP -> R.color.orange_bg
                else -> R.color.pasig_light
            }
            binding.tvInitial.setBackgroundColor(context.getColor(bgColor))
            
            itemView.setOnClickListener { onChatClick(chat) }
            
            // Long press for delete
            itemView.setOnLongClickListener {
                onChatLongClick?.invoke(chat)
                true
            }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Chat, newItem: Chat) = oldItem == newItem
    }
}
