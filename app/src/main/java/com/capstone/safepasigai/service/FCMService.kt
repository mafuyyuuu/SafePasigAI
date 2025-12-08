package com.capstone.safepasigai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.capstone.safepasigai.R
import com.capstone.safepasigai.ui.ChatActivity
import com.capstone.safepasigai.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging Service for push notifications.
 * 
 * Handles:
 * - New message notifications
 * - Emergency alerts from responders
 * - System notifications
 */
class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID_MESSAGES = "chat_messages"
        private const val CHANNEL_ID_ALERTS = "emergency_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM Token refreshed: $token")
        // TODO: Send token to your server for targeting this device
        saveTokenLocally(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM Message received from: ${message.from}")

        // Check if message contains data payload
        message.data.isNotEmpty().let {
            Log.d(TAG, "Message data: ${message.data}")
            handleDataMessage(message.data)
        }

        // Check if message contains notification payload
        message.notification?.let {
            Log.d(TAG, "Message notification: ${it.body}")
            showNotification(it.title ?: "SafePasig.AI", it.body ?: "")
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: "message"
        val title = data["title"] ?: "New Message"
        val body = data["body"] ?: ""
        val chatId = data["chatId"]
        val chatName = data["chatName"]
        val isEmergency = data["isEmergency"] == "true"

        when (type) {
            "message" -> {
                if (chatId != null && chatName != null) {
                    showChatNotification(chatId, chatName, body)
                } else {
                    showNotification(title, body)
                }
            }
            "alert" -> {
                showEmergencyNotification(title, body)
            }
            "responder_update" -> {
                showResponderNotification(title, body)
            }
            else -> {
                showNotification(title, body)
            }
        }
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showChatNotification(chatId: String, chatName: String, message: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
            putExtra(ChatActivity.EXTRA_CHAT_NAME, chatName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, chatId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_chats)
            .setContentTitle(chatName)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(chatId.hashCode(), notification)
    }

    private fun showEmergencyNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_siren)
            .setContentTitle("🚨 $title")
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(999, notification)
    }

    private fun showResponderNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "chats")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle("👮 $title")
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(998, notification)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Messages channel
        val messagesChannel = NotificationChannel(
            CHANNEL_ID_MESSAGES,
            "Chat Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for new chat messages"
            enableVibration(true)
        }

        // Emergency alerts channel
        val alertsChannel = NotificationChannel(
            CHANNEL_ID_ALERTS,
            "Emergency Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical emergency notifications"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
        }

        manager.createNotificationChannels(listOf(messagesChannel, alertsChannel))
    }

    private fun saveTokenLocally(token: String) {
        getSharedPreferences("fcm", Context.MODE_PRIVATE)
            .edit()
            .putString("token", token)
            .apply()
    }
}
