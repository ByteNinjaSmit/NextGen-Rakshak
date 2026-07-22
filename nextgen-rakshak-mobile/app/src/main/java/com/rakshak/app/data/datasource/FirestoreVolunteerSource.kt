package com.rakshak.app.data.datasource

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import com.rakshak.app.data.model.Volunteer
import kotlinx.coroutines.tasks.await

/** Writes the volunteer profile + FCM token to Firestore `volunteers/{uid}`. */
class FirestoreVolunteerSource(
    private val firestore: FirebaseFirestore,
) {
    private fun doc(uid: String) = firestore.collection("volunteers").document(uid)

    /** Create/update the volunteer document (id == uid so rules allow the write). */
    suspend fun upsert(volunteer: Volunteer, fcmToken: String) {
        doc(volunteer.id).set(
            mapOf(
                "phone" to volunteer.phone,
                "role" to volunteer.role,
                "name" to volunteer.name,
                "email" to volunteer.email,
                "fcmToken" to fcmToken,
                "registeredAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    /** Refresh just the token (on FCM token rotation). */
    suspend fun updateToken(uid: String, fcmToken: String) {
        doc(uid).set(mapOf("fcmToken" to fcmToken), SetOptions.merge()).await()
    }

    /** Publish the volunteer's last known position so alerts can be geofenced (FR-03). */
    suspend fun updateLocation(uid: String, latitude: Double, longitude: Double) {
        doc(uid).set(
            mapOf(
                "lastLocation" to GeoPoint(latitude, longitude),
                "locationUpdatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }
}
