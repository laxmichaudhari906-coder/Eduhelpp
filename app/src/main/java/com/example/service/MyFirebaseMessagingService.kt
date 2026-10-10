package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "edu_help_channel"
        const val NOTIFICATION_ID = 4829

        // Foreground suppression tracker
        @Volatile
        var isAppInForeground: Boolean = false
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("EduHelpFCM", "Refreshed FCM token: $token")
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val dbId = try { getString(R.string.firestore_database_id) } catch (_: Exception) { "" }
            val db = if (dbId.isNotBlank()) FirebaseFirestore.getInstance(dbId) else FirebaseFirestore.getInstance()
            db.collection("users").document(user.uid)
                .update("fcmToken", token)
                .addOnFailureListener {
                    db.collection("users").document(user.uid)
                        .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data

        val pairId = data["pairId"] ?: data["roomId"] ?: ""
        val messageId = data["messageId"] ?: ""

        // 2 Gray Ticks (Delivered): update status = "DELIVERED" in Firestore at /pairs/{pairId}/messages/{messageId}
        if (pairId.isNotBlank() && messageId.isNotBlank()) {
            try {
                val dbId = try { getString(R.string.firestore_database_id) } catch (_: Exception) { "" }
                val db = if (dbId.isNotBlank()) FirebaseFirestore.getInstance(dbId) else FirebaseFirestore.getInstance()
                db.collection("pairs").document(pairId)
                    .collection("messages").document(messageId)
                    .update("status", "DELIVERED")
                    .addOnFailureListener {
                        // Fallback in case roomId prefix was used
                        val rawId = if (pairId.startsWith("room_")) pairId.substring(5) else "room_$pairId"
                        db.collection("pairs").document(rawId)
                            .collection("messages").document(messageId)
                            .update("status", "DELIVERED")
                    }
            } catch (e: Exception) {
                Log.w("EduHelpFCM", "Could not update delivered status: ${e.message}")
            }
        }

        // Foreground Suppression:
        // If the app is in the foreground, MUST NOT post a local Android notification banner.
        if (isAppInForeground) {
            Log.d("EduHelpFCM", "App in foreground; suppressed notification banner.")
            return
        }

        // If backgrounded: build and post a masked notification banner
        val maskedTitle = data["title"] ?: "Edu Help"
        val maskedBody = data["body"] ?: "New quiz questions available for your study cohort."

        postMaskedNotification(maskedTitle, maskedBody)
    }

    private fun postMaskedNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure NotificationChannel with ID "edu_help_channel" is initialized with IMPORTANCE_HIGH before calling notify()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Study Cohort Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily academic study reminders and quiz alerts"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // The PendingIntent MUST use FLAG_ACTIVITY_CLEAR_TOP to launch MainActivity (never chat)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
