package com.rakshak.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Raised with a message worth showing the user when Google sign-in can't proceed. */
class GoogleSignInUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Obtains a Google ID token using Credential Manager.
 *
 * Credential Manager is the current API; the old `GoogleSignIn` client is
 * deprecated. It shows the system account picker (or the one-tap bottom sheet)
 * rather than a custom screen, so the volunteer signs in with an account already
 * on the device.
 *
 * Requires an OAuth **web** client ID. The Google Services Gradle plugin
 * generates `default_web_client_id` from `google-services.json` — but only if
 * that file contains an `oauth_client` entry, which requires the app's SHA-1 to
 * be registered in the Firebase console. We therefore look the resource up by
 * name at runtime instead of referencing `R.string.default_web_client_id`
 * directly: that keeps the project compiling before OAuth is configured, and
 * starts working automatically once a corrected `google-services.json` is
 * dropped in, with no code change.
 */
class GoogleSignInClient(private val context: Context) {

    /**
     * @return a Google ID token for the chosen account.
     * @throws GoogleSignInUnavailable if OAuth isn't configured or the user cancels.
     */
    suspend fun requestIdToken(activityContext: Context): String {
        val clientId = webClientId()
            ?: throw GoogleSignInUnavailable(
                "Google sign-in isn't configured yet. Add this app's SHA-1 fingerprint in " +
                    "the Firebase console, then replace app/google-services.json."
            )

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(clientId)
                    // false => also offer accounts that have not used this app before,
                    // so a volunteer can sign in on a freshly issued device.
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

        val response = try {
            CredentialManager.create(context).getCredential(activityContext, request)
        } catch (e: GetCredentialException) {
            throw GoogleSignInUnavailable(
                e.message ?: "Google sign-in was cancelled or unavailable.", e
            )
        }

        val credential = response.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw GoogleSignInUnavailable("Unexpected credential type: ${credential.type}")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    /**
     * The OAuth web client ID, preferring the one generated from
     * `google-services.json` and falling back to `res/values/strings.xml`.
     */
    private fun webClientId(): String? {
        val resources = context.resources
        val generated = resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val id = if (generated != 0) resources.getString(generated) else context.getString(com.rakshak.app.R.string.web_client_id)
        return id.takeIf { it.isNotBlank() }
    }
}
