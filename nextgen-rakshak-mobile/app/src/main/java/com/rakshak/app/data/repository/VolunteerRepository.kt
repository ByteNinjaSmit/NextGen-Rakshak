package com.rakshak.app.data.repository

import com.rakshak.app.data.auth.AuthService
import com.rakshak.app.data.datasource.FirestoreVolunteerSource
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.networking.FcmTokenProvider
import com.rakshak.app.utils.LocationProvider

interface VolunteerRepository {
    /** Register the volunteer and their current FCM token so they receive alerts. */
    suspend fun register(volunteer: Volunteer)

    /**
     * Publish the volunteer's current GPS position so the server can geofence
     * alerts to nearby volunteers (FR-03). No-op if location/uid is unavailable.
     */
    suspend fun publishLocation()
}

class DefaultVolunteerRepository(
    private val source: FirestoreVolunteerSource,
    private val fcmToken: FcmTokenProvider,
    private val auth: AuthService,
    private val locationProvider: LocationProvider,
) : VolunteerRepository {

    override suspend fun register(volunteer: Volunteer) {
        val token = fcmToken.token()
        source.upsert(volunteer, token)
    }

    override suspend fun publishLocation() {
        val uid = auth.currentUid ?: return
        val location = locationProvider.current() ?: return
        source.updateLocation(uid, location.latitude, location.longitude)
    }
}
