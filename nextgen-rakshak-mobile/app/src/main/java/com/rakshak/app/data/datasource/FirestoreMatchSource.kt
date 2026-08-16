package com.rakshak.app.data.datasource

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.rakshak.app.data.auth.AuthService
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.data.model.MatchStatus
import com.rakshak.app.data.model.MatchStatusReport
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Writes match reports to the Firestore `matches` collection. */
class FirestoreMatchSource(
    private val firestore: FirebaseFirestore,
    private val auth: AuthService,
) : MatchDataSource {

    override suspend fun submit(report: MatchReport) {
        val uid = auth.currentUid
        val data = buildMap<String, Any> {
            put("alertId", report.alertId)
            put("childName", report.childName)
            put("imageUrl", report.imageUrl)
            put("volunteerId", report.volunteerId)
            put("volunteerRole", report.volunteerRole)
            put("confidence", report.confidence)
            put("location", GeoPoint(report.latitude, report.longitude))
            put("status", "pending")
            put("timestamp", FieldValue.serverTimestamp())

            // A sighting this device did not make: it arrived over the mesh from
            // an offline volunteer and is being carried to Firestore on their
            // behalf. Stamping the relay is what lets firestore.rules insist that
            // a match name its own author — a direct report must match the
            // caller's uid, and this is the one documented exception. It also
            // tells the kiosk that the attribution came via the mesh and is only
            // as trustworthy as the mesh is.
            if (uid != null && uid != report.volunteerId) put("relayedBy", uid)
        }
        firestore.collection(Constants.COLLECTION_MATCHES).add(data).await()
    }

    override fun observeMyMatches(volunteerId: String): Flow<List<MatchStatusReport>> = callbackFlow {
        val registration = firestore.collection(Constants.COLLECTION_MATCHES)
            .whereEqualTo("volunteerId", volunteerId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "My-matches listener error; treating as empty", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val reports = snapshot?.documents.orEmpty().map { doc ->
                    MatchStatusReport(
                        id = doc.id,
                        alertId = doc.getString("alertId").orEmpty(),
                        childName = doc.getString("childName").orEmpty(),
                        imageUrl = doc.getString("imageUrl").orEmpty(),
                        status = when (doc.getString("status")) {
                            "dispatched" -> MatchStatus.DISPATCHED
                            "accepted" -> MatchStatus.ACCEPTED
                            "dismissed" -> MatchStatus.DISMISSED
                            else -> MatchStatus.PENDING
                        },
                        timestampMillis = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                    )
                }
                trySend(reports)
            }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val TAG = "FirestoreMatchSource"
    }
}
