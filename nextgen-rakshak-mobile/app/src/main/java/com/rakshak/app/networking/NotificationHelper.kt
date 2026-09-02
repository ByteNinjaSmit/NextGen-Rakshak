package com.rakshak.app.networking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.rakshak.app.MainActivity
import com.rakshak.app.R

/** Builds and posts the "child missing" alert notification. */
object NotificationHelper {

    private const val CHANNEL_ID = "rakshak_alerts"

    /** Low-importance channel for the persistent [com.rakshak.app.networking.mesh.MeshService] notification. */
    const val MESH_CHANNEL_ID = "rakshak_mesh"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return // channels arrived in API 26
        createChannel(
            context,
            CHANNEL_ID,
            "Missing-child alerts",
            NotificationManager.IMPORTANCE_HIGH,
            "Tap to open the camera and scan the crowd",
        )
    }

    fun ensureMeshChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createChannel(
            context,
            MESH_CHANNEL_ID,
            "Offline mesh",
            NotificationManager.IMPORTANCE_LOW,
            "Shows that the device is relaying alerts to nearby volunteers offline",
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(
        context: Context,
        id: String,
        name: String,
        importance: Int,
        description: String,
    ) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(id, name, importance).apply { this.description = description }
        )
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
            .setSmallIcon(R.drawable.ic_stat_rakshak)
            .setColor(context.getColor(R.color.notification_accent))
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
