package com.rakshak.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
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

    /** Sign in with an existing email/password account. */
    suspend fun signInWithEmail(email: String, password: String): SignedInUser

    /** Create a new email/password account and sign in with it. */
    suspend fun registerWithEmail(email: String, password: String): SignedInUser

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

    override suspend fun signInWithEmail(email: String, password: String): SignedInUser =
        runAuth("Sign-in failed") {
            auth.signInWithEmailAndPassword(email, password).await().user
        }

    override suspend fun registerWithEmail(email: String, password: String): SignedInUser =
        runAuth("Could not create the account") {
            auth.createUserWithEmailAndPassword(email, password).await().user
        }

    override suspend fun signOut() {
        auth.signOut()
    }

    /**
     * Runs an email/password call and turns Firebase's error codes into text a
     * volunteer can act on. The raw exceptions surface as opaque codes such as
     * ERROR_INVALID_CREDENTIAL, which is not something to put on screen.
     */
    private suspend fun runAuth(
        fallback: String,
        block: suspend () -> FirebaseUser?,
    ): SignedInUser {
        val user = try {
            block()
        } catch (e: FirebaseAuthWeakPasswordException) {
            throw AuthFailure("Password is too weak — use at least 6 characters.", e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw AuthFailure("That email address or password isn't valid.", e)
        } catch (e: FirebaseAuthInvalidUserException) {
            throw AuthFailure("No account exists for that email address.", e)
        } catch (e: FirebaseAuthUserCollisionException) {
            throw AuthFailure("An account already exists for that email address.", e)
        } catch (e: FirebaseAuthException) {
            throw AuthFailure(e.message ?: fallback, e)
        }
        return requireNotNull(user) { fallback }
            .let { SignedInUser(uid = it.uid, displayName = it.displayName, email = it.email) }
    }
}

/** An authentication failure carrying a message fit to show the user. */
class AuthFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
