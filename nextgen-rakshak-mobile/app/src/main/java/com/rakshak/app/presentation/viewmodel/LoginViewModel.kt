package com.rakshak.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.rakshak.app.data.auth.AuthFailure
import com.rakshak.app.data.auth.AuthService
import com.rakshak.app.data.auth.GoogleSignInClient
import com.rakshak.app.data.local.VolunteerStore
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.data.repository.VolunteerRepository
import com.rakshak.app.utils.SimCard
import com.rakshak.app.utils.SimPhoneProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the volunteer's own identity depends on: Google sign-in, the SIM
 * the officer would call back on, and keeping both in step with Firestore.
 *
 * The SIM half is re-run on every app open and every visit to the profile screen
 * rather than only at sign-in, because a volunteer can swap SIMs, port a number,
 * or pull the card this profile was built from between sessions — and a number
 * an officer cannot reach is indistinguishable from no number at all.
 */
class LoginViewModel(
    private val context: Context,
    private val store: VolunteerStore,
    private val authService: AuthService,
    private val volunteers: VolunteerRepository,
    private val googleSignIn: GoogleSignInClient,
) : ViewModel() {

    /** Everything the profile screen's phone-number section renders. */
    data class SimState(
        val permissionGranted: Boolean = false,
        val simPresent: Boolean = false,
        val cards: List<SimCard> = emptyList(),
        val selectedSubscriptionId: Int = SimPhoneProvider.NO_SUBSCRIPTION,
        val phone: String = "",
        val phoneIsManual: Boolean = false,
        val syncedToCloud: Boolean = false,
        val syncing: Boolean = false,
        val message: String? = null,
    ) {
        /** True when a SIM is in but the carrier does not expose its number. */
        val numberUnreadable: Boolean
            get() = simPresent && cards.all { it.number == null }

        val selectedCard: SimCard?
            get() = cards.firstOrNull { it.subscriptionId == selectedSubscriptionId }
    }

    val volunteer: StateFlow<Volunteer?> =
        store.volunteer.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _sim = MutableStateFlow(SimState())
    val sim: StateFlow<SimState> = _sim.asStateFlow()

    /**
     * Set when this device has no active SIM (or it cannot be confirmed) —
     * a volunteer an officer cannot call back is worth surfacing, not just
     * silently leaving the phone field blank.
     */
    private val _noSimWarning = MutableStateFlow(false)
    val noSimWarning: StateFlow<Boolean> = _noSimWarning.asStateFlow()

    init {
        // The local profile can outlive the Firebase session — the session is
        // revoked, cleared, or never re-established after a reinstall. The app
        // would then go straight to Home and every Firestore read would be denied.
        // Drop the stale profile so the volunteer is asked to sign in again.
        viewModelScope.launch {
            val current = store.volunteer.first()
            if (current != null && authService.currentUid == null) {
                store.clear()
                return@launch
            }
            if (current != null) {
                if (revokeStaleOfficerSession()) return@launch
                refreshIdentity(current)
                syncSim()
            }
        }
    }

    /**
     * A stored volunteer session whose account has since been granted the kiosk's
     * `police` claim is dead: firestore.rules denies every `volunteers/{uid}`
     * write, so the profile, the SIM number and the FCM token all silently stop
     * updating while the app still looks signed in.
     *
     * [rejectIfOfficer] only catches this at sign-in, which cannot cover an
     * account that became an officer afterwards — so it is re-checked on every
     * app open. Offline, the claim cannot be read and the session is left alone
     * rather than signing a volunteer out mid-event over a missing network.
     */
    private suspend fun revokeStaleOfficerSession(): Boolean {
        val isOfficer = runCatching { authService.hasPoliceClaim() }.getOrDefault(false)
        if (!isOfficer) return false
        authService.signOut()
        store.clear()
        _error.value = "This account is registered on the police kiosk. " +
            "Volunteers must sign in with a different account."
        return true
    }

    // --- SIM -----------------------------------------------------------------

    /**
     * Re-detect the inserted SIMs, keep or auto-pick the line to use, and push
     * its number to `volunteers/{uid}` when it is not already there.
     *
     * Auto-picking is deliberate: on a dual-SIM phone the volunteer is not asked
     * to choose before they can do anything, they just get the line the phone
     * itself treats as the default and can switch with [selectSim]. A choice they
     * have already made is preserved as long as that SIM is still inserted.
     */
    fun syncSim() {
        viewModelScope.launch { syncSimInternal(force = false) }
    }

    /** The profile screen's "Sync now" — pushes the current number regardless. */
    fun forceSyncSim() {
        viewModelScope.launch { syncSimInternal(force = true) }
    }

    /** Switch to another inserted SIM and publish its number. */
    fun selectSim(subscriptionId: Int) {
        viewModelScope.launch {
            val current = store.volunteer.first() ?: return@launch
            val card = SimPhoneProvider.listSims(context)
                .firstOrNull { it.subscriptionId == subscriptionId } ?: return@launch
            val number = card.number
            if (number == null) {
                // Selecting a SIM whose number the carrier withholds is still
                // meaningful — it records which line is the right one — but there
                // is nothing to publish, so say so instead of silently no-oping.
                store.savePhone(current.phone, subscriptionId, current.phoneIsManual, current.phoneSynced)
                _sim.update {
                    it.copy(
                        selectedSubscriptionId = subscriptionId,
                        message = "${card.displayName} does not expose its number — enter it below.",
                    )
                }
                return@launch
            }
            publishPhone(current.id, number, subscriptionId, manual = false)
        }
    }

    /**
     * Save a number the volunteer typed. Marked manual so the SIM re-read on the
     * next app open cannot overwrite it — on the many carriers that never expose
     * an MSISDN, a typed number is the only correct one this app will ever have.
     */
    fun savePhoneManually(raw: String) {
        val number = raw.trim()
        if (number.length < MIN_PHONE_DIGITS) {
            _sim.update { it.copy(message = "Enter at least $MIN_PHONE_DIGITS digits.") }
            return
        }
        viewModelScope.launch {
            val current = store.volunteer.first() ?: return@launch
            publishPhone(current.id, number, current.simSubscriptionId, manual = true)
        }
    }

    /** Drop a manually typed number and go back to whatever the SIM reports. */
    fun useSimNumber() {
        viewModelScope.launch {
            val current = store.volunteer.first() ?: return@launch
            store.savePhone("", current.simSubscriptionId, manual = false, synced = false)
            syncSimInternal(force = true, override = current.copy(phone = "", phoneIsManual = false))
        }
    }

    fun dismissSimMessage() {
        _sim.update { it.copy(message = null) }
    }

    private suspend fun syncSimInternal(force: Boolean, override: Volunteer? = null) {
        val granted = SimPhoneProvider.hasPermission(context)
        val present = SimPhoneProvider.simPresent(context)
        val cards = SimPhoneProvider.listSims(context)
        _noSimWarning.value = !present

        val current = override ?: store.volunteer.first()
        if (current == null) {
            _sim.value = SimState(
                permissionGranted = granted,
                simPresent = present,
                cards = cards,
                selectedSubscriptionId = cards.firstOrNull()?.subscriptionId
                    ?: SimPhoneProvider.NO_SUBSCRIPTION,
            )
            return
        }

        // Keep the volunteer's own choice while that SIM is still inserted;
        // otherwise fall back to the line the phone prefers for calls.
        val stillInserted = cards.any { it.subscriptionId == current.simSubscriptionId }
        val chosen = if (stillInserted) {
            current.simSubscriptionId
        } else {
            SimPhoneProvider.preferredSubscriptionId(context)
        }
        val simNumber = cards.firstOrNull { it.subscriptionId == chosen }?.number

        _sim.value = SimState(
            permissionGranted = granted,
            simPresent = present,
            cards = cards,
            selectedSubscriptionId = chosen,
            phone = current.phone,
            phoneIsManual = current.phoneIsManual,
            syncedToCloud = current.phoneSynced,
        )

        val manualStands = current.phoneIsManual && current.phone.isNotBlank()
        val target = if (manualStands) current.phone else simNumber ?: current.phone
        val changed = target != current.phone || chosen != current.simSubscriptionId
        // A number that never reached Firestore is retried even when nothing
        // changed locally — the first attempt may simply have been made offline.
        val needsPush = target.isNotBlank() && (force || changed || !current.phoneSynced)
        if (!needsPush) return

        publishPhone(current.id, target, chosen, manual = manualStands)
    }

    /** Persist a number locally, then push it to Firestore, reporting either way. */
    private suspend fun publishPhone(uid: String, number: String, subscriptionId: Int, manual: Boolean) {
        _sim.update { it.copy(syncing = true, message = null) }
        store.savePhone(number, subscriptionId, manual, synced = false)
        val outcome = runCatching { volunteers.updatePhone(uid, number) }
        val pushed = outcome.isSuccess
        if (pushed) store.savePhone(number, subscriptionId, manual, synced = true)
        _sim.update {
            it.copy(
                phone = number,
                phoneIsManual = manual,
                selectedSubscriptionId = subscriptionId,
                syncing = false,
                syncedToCloud = pushed,
                message = when {
                    pushed -> "Contact number updated."
                    // A rejection is not a retry-later situation: the number is
                    // saved here but will never reach the control room on its
                    // own, so it must not be reported as merely queued.
                    outcome.exceptionOrNull().isPermissionDenied() ->
                        "Saved on this phone, but the control room refused the update. " +
                            "Report this to your deployment officer."
                    else ->
                        "Saved on this phone — it will reach the control room when you are back online."
                },
            )
        }
    }

    private fun Throwable?.isPermissionDenied(): Boolean {
        val firestore = this as? FirebaseFirestoreException ?: return false
        return firestore.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    // --- identity ------------------------------------------------------------

    /**
     * Re-read the Google account's display name, email and avatar on app open and
     * push any change through. The photo is the one the officer sees next to a
     * reported sighting, so a volunteer who has since changed it should not still
     * be represented by the one captured at their first sign-in.
     *
     * Also retries whenever [Volunteer.identitySynced] is false, not just on a
     * changed profile — otherwise a `register()` call that failed once (offline,
     * a since-fixed rules rejection) never gets another attempt, because nothing
     * about the Google profile itself ever "changes" again. That leaves the
     * `volunteers/{uid}` doc permanently missing email/photo even though the
     * device has known them the whole time.
     */
    private suspend fun refreshIdentity(current: Volunteer) {
        val profile = authService.currentProfile ?: return
        val name = profile.displayName.orEmpty().ifBlank { current.name }
        val email = profile.email.orEmpty().ifBlank { current.email }
        val photoUrl = profile.photoUrl.orEmpty().ifBlank { current.photoUrl }
        val changed = name != current.name || email != current.email || photoUrl != current.photoUrl
        if (!changed && current.identitySynced) return
        store.saveIdentity(name, email, photoUrl, synced = false)
        val pushed = runCatching { volunteers.updateIdentity(current.id, name, email, photoUrl) }.isSuccess
        if (pushed) store.saveIdentity(name, email, photoUrl, synced = true)
    }

    // --- sign-in / sign-out --------------------------------------------------

    /**
     * Preferred sign-in: a real Google account, so a reported sighting is
     * attributable to an identifiable volunteer.
     *
     * [activityContext] must be the Activity — Credential Manager needs it to
     * show the account picker.
     */
    fun signInWithGoogle(activityContext: Context) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                val idToken = googleSignIn.requestIdToken(activityContext)
                val user = authService.signInWithGoogle(idToken)
                // Best-effort: plenty of carriers/devices leave the number
                // unreadable even with permission granted, so a blank phone
                // field here is expected, not a bug — see SimPhoneProvider.
                _noSimWarning.value = !SimPhoneProvider.simPresent(context)
                val subscriptionId = SimPhoneProvider.preferredSubscriptionId(context)
                val volunteer = Volunteer(
                    id = user.uid,
                    phone = SimPhoneProvider.numberFor(context, subscriptionId).orEmpty(),
                    role = "volunteer",
                    name = user.displayName.orEmpty(),
                    email = user.email.orEmpty(),
                    photoUrl = user.photoUrl.orEmpty(),
                    simSubscriptionId = subscriptionId,
                )
                rejectIfOfficer()
                store.save(volunteer)
                // Genuinely best-effort: a failed or slow FCM registration must
                // not fail a sign-in that already succeeded. onNewToken and the
                // next launch's register() both recover it.
                runCatching { volunteers.register(volunteer) }
                    .onSuccess {
                        store.saveIdentity(volunteer.name, volunteer.email, volunteer.photoUrl, synced = true)
                        if (volunteer.phone.isNotBlank()) {
                            store.savePhone(volunteer.phone, subscriptionId, manual = false, synced = true)
                        }
                    }
                syncSimInternal(force = false)
            }.onFailure { _error.value = it.message ?: "Google sign-in failed" }
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

    private companion object {
        const val MIN_PHONE_DIGITS = 6
    }
}
