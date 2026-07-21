package com.rakshak.app.networking

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/** Fetches this device's current FCM registration token. */
class FcmTokenProvider(
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) {
    suspend fun token(): String = messaging.token.await()
}
