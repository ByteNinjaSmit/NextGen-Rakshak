package com.rakshak.app.networking

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rakshak.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM pushes for new alerts and keeps the device's token fresh in
 * Firestore so the backend can reach this volunteer.
 */
class RakshakMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only messages (see functions/src/notify.ts) — onMessageReceived
        // runs in every app state, so the notification is always built here.
        val data = message.data
        val childName = data["childName"] ?: "a child"
        val alertId = data["alertId"]
        if (alertId == null) {
            Log.w(TAG, "alert push with no alertId; ignoring")
            return
        }
        Log.i(TAG, "New alert push: $childName ($alertId)")
        NotificationHelper.showAlert(
            context = applicationContext,
            alertId = alertId,
            title = data["title"] ?: "Child Missing — Tap to Scan",
            body = data["body"] ?: "Searching for $childName. Tap to help.",
        )
    }

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching { ServiceLocator.volunteerSource().updateToken(uid, token) }
                .onFailure { Log.w(TAG, "token update failed", it) }
        }
    }

    companion object {
        private const val TAG = "RakshakFCM"
    }
}
