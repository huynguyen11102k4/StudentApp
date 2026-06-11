package com.examhub.student.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.examhub.student.MainActivity
import com.examhub.student.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.GlobalContext

class OmrFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        runCatching {
            GlobalContext.get().get<FcmTokenRegistrar>().registerToken(token, serviceScope)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        showNotification(message)
    }

    private fun showNotification(message: RemoteMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val data = message.data
        val preferences = runCatching { GlobalContext.get().get<NotificationPreferenceManager>() }.getOrNull()
        if (preferences?.isEnabled() == false) return

        val type = data["type"].orEmpty()
        val title = message.notification?.title
            ?: data["title"]
            ?: defaultTitleFor(type)
        val body = message.notification?.body
            ?: data["body"]
            ?: data["content"]
            ?: data["message"]
            ?: data["exam_name"]?.let { defaultTitleFor(type) + ": " + it }
            ?: defaultBodyFor(type)
        val notificationId = data["notification_id"]?.hashCode() ?: System.currentTimeMillis().toInt()

        ensureNotificationChannel(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_NOTIFICATION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun defaultTitleFor(type: String): String {
        return when (type.normalizedNotificationType()) {
            "exam_opened" -> getString(R.string.notifications_exam_opened_default_title)
            "exam_closed" -> getString(R.string.notifications_exam_closed_default_title)
            "submission_pending" -> getString(R.string.notifications_submission_pending_default_title)
            else -> getString(R.string.notifications_title)
        }
    }

    private fun defaultBodyFor(type: String): String {
        return when (type.normalizedNotificationType()) {
            "exam_opened" -> getString(R.string.notifications_exam_opened_default_body)
            "exam_closed" -> getString(R.string.notifications_exam_closed_default_body)
            "submission_pending" -> getString(R.string.notifications_submission_pending_default_body)
            else -> getString(R.string.notifications_subtitle)
        }
    }

    private fun String.normalizedNotificationType(): String {
        return trim().lowercase().replace('-', '_')
    }

    companion object {
        const val CHANNEL_ID = "student_updates"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.fcm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.fcm_channel_description)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
