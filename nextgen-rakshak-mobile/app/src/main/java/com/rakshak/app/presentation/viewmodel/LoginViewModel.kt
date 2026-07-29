package com.rakshak.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.app.data.auth.AuthFailure
import com.rakshak.app.data.auth.AuthService
import com.rakshak.app.data.auth.GoogleSignInClient
import com.rakshak.app.data.local.VolunteerStore
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.data.repository.VolunteerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sign-in: captures phone + role, signs the device in anonymously (so Firestore
 * writes satisfy `request.auth != null`), and registers the volunteer + FCM
 * token so they receive alert pushes. Volunteer id = anonymous uid.
 */
class LoginViewModel(
    private val store: VolunteerStore,
    private val authService: AuthService,
    private val volunteers: VolunteerRepository,
    private val googleSignIn: GoogleSignInClient,
) : ViewModel() {

    val volunteer: StateFlow<Volunteer?> =
        store.volunteer.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        // The local profile can outlive the Firebase session — the session is
        // revoked, cleared, or never re-established after a reinstall. The app
        // would then go straight to Home and every Firestore read would be denied.
        // Drop the stale profile so the volunteer is asked to sign in again.
        viewModelScope.launch {
            if (store.volunteer.first() != null && authService.currentUid == null) {
                store.clear()
            }
        }
    }

    /**
     * Preferred sign-in: a real Google account, so a reported sighting is
     * attributable to an identifiable volunteer.
     *
     * [activityContext] must be the Activity — Credential Manager needs it to
     * show the account picker.
     */
    fun signInWithGoogle(activityContext: Context, role: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                val idToken = googleSignIn.requestIdToken(activityContext)
                val user = authService.signInWithGoogle(idToken)
                val volunteer = Volunteer(
                    id = user.uid,
                    phone = "",
                    role = role,
                    name = user.displayName.orEmpty(),
                    email = user.email.orEmpty(),
                )
                rejectIfOfficer()
                store.save(volunteer)
                volunteers.register(volunteer) // best-effort FCM registration
            }.onFailure { _error.value = it.message ?: "Google sign-in failed" }
            _busy.value = false
        }
    }

    /**
     * Email/password sign-in. [register] creates the account first, for a
     * volunteer who has not been issued one.
     */
    fun signInWithEmail(email: String, password: String, role: String, register: Boolean) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                val user =
                    if (register) authService.registerWithEmail(email, password)
                    else authService.signInWithEmail(email, password)
                val volunteer = Volunteer(
                    id = user.uid,
                    phone = "",
                    role = role,
                    name = user.displayName.orEmpty(),
                    email = user.email.orEmpty(),
                )
                rejectIfOfficer()
                store.save(volunteer)
                volunteers.register(volunteer) // best-effort FCM registration
            }.onFailure { _error.value = it.message ?: "Sign-in failed" }
            _busy.value = false
        }
    }

    /** Demo fallback: anonymous account, no verified identity. */
    fun signIn(phone: String, role: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                val uid = authService.ensureSignedIn()
                val volunteer = Volunteer(id = uid, phone = phone, role = role)
                rejectIfOfficer()
                store.save(volunteer)
                volunteers.register(volunteer) // best-effort FCM registration
            }.onFailure { _error.value = it.message ?: "Sign-in failed" }
            _busy.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching {
                authService.signOut()
                store.clear()
            }.onFailure { _error.value = it.message ?: "Sign-out failed" }
        }
    }

    /**
     * Stop a police kiosk account from also registering as a volunteer device.
     * Both apps share one Firebase Auth pool, and firestore.rules denies the
     * `volunteers/{uid}` write for a `police` account — catching it here means a
     * readable message instead of a permission error, and no local profile left
     * behind for a session that cannot report a sighting.
     */
    private suspend fun rejectIfOfficer() {
        if (!authService.hasPoliceClaim()) return
        authService.signOut()
        store.clear()
        throw AuthFailure(
            "This account is registered on the police kiosk. " +
                "Volunteers must sign in with a different account.",
        )
    }
}
