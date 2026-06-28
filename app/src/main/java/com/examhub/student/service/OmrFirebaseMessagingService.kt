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
import com.examhub.student.util.helper.NotificationTextResolver
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

        val titleLocKey = message.notification?.titleLocalizationKey
            ?: data["title_loc_key"]
            ?: data["title-loc-key"]
            ?: data["titleLocKey"]
        val titleLocArgs = message.notification?.titleLocalizationArgs
            ?.toList()
            ?: NotificationTextResolver.parseLocArgs(data["title_loc_args"])
            ?: NotificationTextResolver.parseLocArgs(data["title-loc-args"])
            ?: NotificationTextResolver.parseLocArgs(data["titleLocArgs"])

        val bodyLocKey = message.notification?.bodyLocalizationKey
            ?: data["body_loc_key"]
            ?: data["body-loc-key"]
            ?: data["bodyLocKey"]
        val bodyLocArgs = message.notification?.bodyLocalizationArgs
            ?.toList()
            ?: NotificationTextResolver.parseLocArgs(data["body_loc_args"])
            ?: NotificationTextResolver.parseLocArgs(data["body-loc-args"])
            ?: NotificationTextResolver.parseLocArgs(data["bodyLocArgs"])

        val text = NotificationTextResolver.resolve(
            NotificationTextResolver.Payload(
                type = type,
                notificationTitle = message.notification?.title,
                notificationBody = message.notification?.body,
                titleLocKey = titleLocKey,
                titleLocArgs = titleLocArgs,
                bodyLocKey = bodyLocKey,
                bodyLocArgs = bodyLocArgs,
                data = data
            ),
            AndroidNotificationStrings()
        )

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
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun resolveLocString(locKey: String, args: List<String>): String? {
        val resId = resources.getIdentifier(locKey, "string", packageName)
        if (resId == 0) return null
        return if (args.isEmpty()) {
            getString(resId)
        } else {
            runCatching {
                getString(resId, *args.toTypedArray())
            }.getOrElse {
                getString(resId)
            }
        }
    }

    private inner class AndroidNotificationStrings : NotificationTextResolver.Strings {
        override fun resolve(key: String, args: List<String>): String? =
            resolveLocString(key, args)

        override fun defaultTitle(type: String): String = defaultTitleFor(type)

        override fun defaultBody(type: String): String = defaultBodyFor(type)
    }

    private fun defaultTitleFor(type: String): String {
        return when (NotificationTextResolver.normalizeType(type)) {
            "exam_started" -> getString(R.string.notif_title_exam_started)
            "exam_opened" -> getString(R.string.notifications_exam_opened_default_title)
            "exam_closed" -> getString(R.string.notifications_exam_closed_default_title)
            "exam_graded" -> getString(R.string.notif_title_exam_graded)
            "appeal_responded" -> getString(R.string.notif_title_appeal_responded)
            "submission_pending" -> getString(R.string.notifications_submission_pending_default_title)
            else -> getString(R.string.notifications_title)
        }
    }

    private fun defaultBodyFor(type: String): String {
        return when (NotificationTextResolver.normalizeType(type)) {
            "exam_started" -> getString(R.string.notif_body_exam_started)
            "exam_opened" -> getString(R.string.notifications_exam_opened_default_body)
            "exam_closed" -> getString(R.string.notifications_exam_closed_default_body)
            "exam_graded" -> getString(R.string.notif_body_exam_graded)
            "appeal_responded" -> getString(R.string.notif_body_appeal_responded)
            "submission_pending" -> getString(R.string.notifications_submission_pending_default_body)
            else -> getString(R.string.notifications_subtitle)
        }
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
