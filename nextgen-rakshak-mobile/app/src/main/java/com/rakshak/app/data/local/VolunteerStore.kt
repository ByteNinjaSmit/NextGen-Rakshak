package com.rakshak.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rakshak.app.data.model.Volunteer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "volunteer")

/** Persists the signed-in volunteer locally (mock auth for the MVP). */
class VolunteerStore(private val context: Context) {

    private object Keys {
        val ID = stringPreferencesKey("id")
        val PHONE = stringPreferencesKey("phone")
        val ROLE = stringPreferencesKey("role")
        val NAME = stringPreferencesKey("name")
        val EMAIL = stringPreferencesKey("email")
        val PHOTO_URL = stringPreferencesKey("photoUrl")
        val SIM_SUB_ID = intPreferencesKey("simSubscriptionId")
        val PHONE_MANUAL = booleanPreferencesKey("phoneIsManual")
        val PHONE_SYNCED = booleanPreferencesKey("phoneSynced")
    }

    /** Emits the current volunteer, or null when signed out. */
    val volunteer: Flow<Volunteer?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.ID] ?: return@map null
        Volunteer(
            id = id,
            phone = prefs[Keys.PHONE].orEmpty(),
            role = prefs[Keys.ROLE] ?: "volunteer",
            name = prefs[Keys.NAME].orEmpty(),
            email = prefs[Keys.EMAIL].orEmpty(),
            photoUrl = prefs[Keys.PHOTO_URL].orEmpty(),
            simSubscriptionId = prefs[Keys.SIM_SUB_ID] ?: -1,
            phoneIsManual = prefs[Keys.PHONE_MANUAL] ?: false,
            phoneSynced = prefs[Keys.PHONE_SYNCED] ?: false,
        )
    }

    suspend fun save(volunteer: Volunteer) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ID] = volunteer.id
            prefs[Keys.PHONE] = volunteer.phone
            prefs[Keys.ROLE] = volunteer.role
            prefs[Keys.NAME] = volunteer.name
            prefs[Keys.EMAIL] = volunteer.email
            prefs[Keys.PHOTO_URL] = volunteer.photoUrl
            prefs[Keys.SIM_SUB_ID] = volunteer.simSubscriptionId
            prefs[Keys.PHONE_MANUAL] = volunteer.phoneIsManual
            prefs[Keys.PHONE_SYNCED] = volunteer.phoneSynced
        }
    }

    /**
     * Patch only the contact number and the SIM it came from.
     *
     * Separate from [save] because the SIM is re-read on every app open, while
     * the rest of the profile is owned by the Google account — a full save from
     * that path would need to reconstruct fields it has no business touching.
     */
    suspend fun savePhone(phone: String, subscriptionId: Int, manual: Boolean, synced: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PHONE] = phone
            prefs[Keys.SIM_SUB_ID] = subscriptionId
            prefs[Keys.PHONE_MANUAL] = manual
            prefs[Keys.PHONE_SYNCED] = synced
        }
    }

    /** Patch the fields Google owns, so a changed display name or avatar lands. */
    suspend fun saveIdentity(name: String, email: String, photoUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NAME] = name
            prefs[Keys.EMAIL] = email
            prefs[Keys.PHOTO_URL] = photoUrl
        }
    }

    /** Forget the local profile on sign-out. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
