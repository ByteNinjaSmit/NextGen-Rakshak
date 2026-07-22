package com.rakshak.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** A signed-in identity: the Firebase uid plus whatever the provider told us. */
data class SignedInUser(
    val uid: String,
    val displayName: String? = null,
    val email: String? = null,
)

/** Authentication for the volunteer app. (SOLID: consumers depend on the interface.) */
interface AuthService {
    /** Signed-in user id, or null if not authenticated. */
    val currentUid: String?

    /** Ensure the device is authenticated (anonymous). Returns the uid. */
    suspend fun ensureSignedIn(): String

    /**
     * Exchange a Google ID token for a Firebase session. Preferred over anonymous
     * sign-in because it ties a volunteer to a real, re-identifiable account —
     * the synopsis's "pre-registered, credible volunteers" model.
     */
    suspend fun signInWithGoogle(idToken: String): SignedInUser

    /** Sign out of Firebase. */
    suspend fun signOut()
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

    override suspend fun signInWithGoogle(idToken: String): SignedInUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = requireNotNull(auth.signInWithCredential(credential).await().user) {
            "Google sign-in returned no user"
        }
        return SignedInUser(uid = user.uid, displayName = user.displayName, email = user.email)
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}
