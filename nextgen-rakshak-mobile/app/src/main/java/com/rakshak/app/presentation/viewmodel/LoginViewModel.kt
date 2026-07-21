package com.rakshak.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakshak.app.data.auth.AuthService
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
) : ViewModel() {

    val volunteer: StateFlow<Volunteer?> =
        store.volunteer.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun signIn(phone: String, role: String) {
        viewModelScope.launch {
            runCatching {
                val uid = authService.ensureSignedIn()
                val volunteer = Volunteer(id = uid, phone = phone, role = role)
                store.save(volunteer)
                volunteers.register(volunteer) // best-effort FCM registration
            }.onFailure { _error.value = it.message ?: "Sign-in failed" }
        }
    }
}
