package com.capstone.safepasigai.ui.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.capstone.safepasigai.R
import com.capstone.safepasigai.data.model.Message
import com.capstone.safepasigai.data.model.MessageType
import com.capstone.safepasigai.data.model.MessageStatus
import com.capstone.safepasigai.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val currentUserId: String,
    private val onLocationClick: ((Double, Double) -> Unit)? = null
) : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            val context = itemView.context
            val isMe = message.senderId == currentUserId
            
            binding.tvMessage.text = message.text
            
            // Format time with status
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            val timeText = sdf.format(Date(message.timestamp))
            
            // Add status indicator for sent messages
            if (isMe) {
                val statusIcon = when (message.status) {
                    MessageStatus.SENDING -> "⏳"
                    MessageStatus.SENT -> "✓"
                    MessageStatus.DELIVERED -> "✓✓"
                    MessageStatus.SEEN -> "✓✓"
                }
                binding.tvTime.text = "$timeText $statusIcon"
                
                // Blue check marks for seen
                if (message.status == MessageStatus.SEEN) {
                    binding.tvTime.setTextColor(ContextCompat.getColor(context, R.color.pasig_light))
                }
            } else {
                binding.tvTime.text = timeText
            }
            
            // Sender name (only for received messages)
            if (!isMe && message.senderName.isNotEmpty()) {
                binding.tvSender.text = message.senderName
                binding.tvSender.visibility = View.VISIBLE
            } else {
                binding.tvSender.visibility = View.GONE
            }
            
            // Style based on sender
            val params = binding.messageCard.layoutParams as FrameLayout.LayoutParams
            if (isMe) {
                params.gravity = Gravity.END
                binding.messageCard.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.pasig_dark)
                )
                binding.tvMessage.setTextColor(
                    ContextCompat.getColor(context, R.color.white)
                )
                binding.tvTime.setTextColor(
                    ContextCompat.getColor(context, R.color.white)
                )
                binding.tvTime.alpha = 0.7f
            } else {
                params.gravity = Gravity.START
                binding.messageCard.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.white)
                )
                binding.tvMessage.setTextColor(
                    ContextCompat.getColor(context, R.color.text_primary)
                )
                binding.tvTime.setTextColor(
                    ContextCompat.getColor(context, R.color.text_secondary)
                )
                binding.tvTime.alpha = 1.0f
            }
            binding.messageCard.layoutParams = params
            
            // Special styling for alerts and locations
            when (message.type) {
                MessageType.ALERT -> {
                    binding.messageCard.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.alert_red)
                    )
                    binding.tvMessage.setTextColor(
                        ContextCompat.getColor(context, R.color.white)
                    )
                }
                MessageType.LOCATION -> {
                    binding.locationPreview.visibility = View.VISIBLE
                    binding.locationPreview.setOnClickListener {
                        message.locationLat?.let { lat ->
                            message.locationLng?.let { lng ->
                                onLocationClick?.invoke(lat, lng)
                            }
                        }
                    }
                }
                else -> {
                    binding.locationPreview.visibility = View.GONE
                }
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
    }
}
