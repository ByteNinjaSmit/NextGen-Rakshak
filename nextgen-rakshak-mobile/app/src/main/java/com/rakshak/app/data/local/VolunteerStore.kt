package com.rakshak.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
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
    }

    /** Emits the current volunteer, or null when signed out. */
    val volunteer: Flow<Volunteer?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.ID] ?: return@map null
        Volunteer(
            id = id,
            phone = prefs[Keys.PHONE].orEmpty(),
            role = prefs[Keys.ROLE] ?: "community",
            name = prefs[Keys.NAME].orEmpty(),
            email = prefs[Keys.EMAIL].orEmpty(),
        )
    }

    suspend fun save(volunteer: Volunteer) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ID] = volunteer.id
            prefs[Keys.PHONE] = volunteer.phone
            prefs[Keys.ROLE] = volunteer.role
            prefs[Keys.NAME] = volunteer.name
            prefs[Keys.EMAIL] = volunteer.email
        }
    }

    /** Forget the local profile on sign-out. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
