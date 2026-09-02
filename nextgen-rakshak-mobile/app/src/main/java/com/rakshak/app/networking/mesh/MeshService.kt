package com.rakshak.app.networking.mesh

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.rakshak.app.MainActivity
import com.rakshak.app.R
import com.rakshak.app.di.ServiceLocator
import com.rakshak.app.networking.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the offline mesh alive when the app is backgrounded or the screen is
 * locked. Nearby Connections is throttled hard once the hosting process has no
 * foreground component, so without this a volunteer walking a crowd with the
 * phone in a pocket relays nothing — which is the exact scenario the mesh exists
 * for.
 *
 * A low-importance notification is mandatory for a foreground service; it doubles
 * as the volunteer's only signal that the mesh is up, showing the live peer count.
 * The "Stop" action lets them turn it off without uninstalling.
 */
class MeshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastPeerCount = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureMeshChannel(this)
        // A connectedDevice foreground service needs a Bluetooth runtime
        // permission on Android 14+. If it was revoked while the service was
        // stopped, startForeground throws — stop cleanly instead of crash-looping
        // (START_STICKY would restart us straight into the same exception).
        try {
            startForeground(NOTIF_ID, buildNotification(0))
        } catch (e: Exception) {
            android.util.Log.w("MeshService", "startForeground refused; stopping", e)
            stopSelf()
            return
        }
        val mesh = ServiceLocator.mesh(this)
        mesh.start()
        scope.launch {
            mesh.peerCount.collectLatest { count ->
                if (count != lastPeerCount) {
                    lastPeerCount = count
                    getSystemService<android.app.NotificationManager>()
                        ?.notify(NOTIF_ID, buildNotification(count))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        ServiceLocator.mesh(this).stop()
        super.onDestroy()
    }

    private fun buildNotification(peerCount: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MeshService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            peerCount <= 0 -> "Searching for nearby volunteers…"
            peerCount == 1 -> "Linked to 1 nearby volunteer"
            else -> "Linked to $peerCount nearby volunteers"
        }
        return NotificationCompat.Builder(this, NotificationHelper.MESH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rakshak)
            .setContentTitle("Rakshak mesh active")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 4711
        const val ACTION_STOP = "com.rakshak.app.action.MESH_STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MeshService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MeshService::class.java).setAction(ACTION_STOP))
        }
    }
}
