package com.rakshak.app.networking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.rakshak.app.MainActivity
import com.rakshak.app.R

/** Builds and posts the "child missing" alert notification. */
object NotificationHelper {

    private const val CHANNEL_ID = "rakshak_alerts"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Missing-child alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Tap to open the camera and scan the crowd" }
        manager.createNotificationChannel(channel)
    }

    fun showAlert(context: Context, childName: String) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Child Missing — Tap to Scan")
            .setContentText("Searching for $childName. Tap to help.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        context.getSystemService<NotificationManager>()
            ?.notify(childName.hashCode(), notification)
    }
}
