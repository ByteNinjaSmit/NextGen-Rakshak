package com.rakshak.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.app.data.auth.AuthService
import com.rakshak.app.data.auth.GoogleSignInClient
import com.rakshak.app.data.local.VolunteerStore
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.data.repository.VolunteerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                store.save(volunteer)
                volunteers.register(volunteer) // best-effort FCM registration
            }.onFailure { _error.value = it.message ?: "Google sign-in failed" }
            _busy.value = false
        }
    }

    /** Demo fallback used when Google sign-in has not been configured yet. */
    fun signIn(phone: String, role: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                val uid = authService.ensureSignedIn()
                val volunteer = Volunteer(id = uid, phone = phone, role = role)
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
}
