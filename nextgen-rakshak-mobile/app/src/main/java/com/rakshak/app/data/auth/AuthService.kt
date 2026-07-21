package com.rakshak.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Authentication for the volunteer app. (SOLID: consumers depend on the interface.) */
interface AuthService {
    /** Signed-in user id, or null if not authenticated. */
    val currentUid: String?

    /** Ensure the device is authenticated (anonymous). Returns the uid. */
    suspend fun ensureSignedIn(): String
}

/**
 * Anonymous Firebase Auth. Gives each device a stable uid so Firestore security
 * rules can require `request.auth != null` without a full login flow.
 */
class FirebaseAuthService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthService {

    override val currentUid: String?
        get() = auth.currentUser?.uid

    override suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return requireNotNull(result.user).uid
    }
}
