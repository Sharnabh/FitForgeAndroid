package com.fitforge.ishita.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fitforge.MainActivity

object CoachNotificationHelper {
    const val CHANNEL_ID = "fitforge_coach_responses"
    private const val CHANNEL_NAME = "AI Coach Responses"
    private const val NOTIFICATION_ID_ISHITA = 1001
    private const val NOTIFICATION_ID_CHATGPT = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when Ishita or AI Coach completes generating responses in the background."
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun showResponseNotification(
        context: Context,
        coachTitle: String,
        previewText: String,
        engine: String // "ISHITA" or "CHATGPT"
    ) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_open_coach", true)
            putExtra("extra_coach_engine", engine)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            if (engine == "ISHITA") NOTIFICATION_ID_ISHITA else NOTIFICATION_ID_CHATGPT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notificationId = if (engine == "ISHITA") NOTIFICATION_ID_ISHITA else NOTIFICATION_ID_CHATGPT

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(coachTitle)
            .setContentText(previewText.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(previewText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notif)
    }
}

