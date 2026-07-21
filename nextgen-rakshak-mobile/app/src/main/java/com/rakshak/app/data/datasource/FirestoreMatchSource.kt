package com.rakshak.app.data.datasource

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.tasks.await

/** Writes match reports to the Firestore `matches` collection. */
class FirestoreMatchSource(
    private val firestore: FirebaseFirestore,
) : MatchDataSource {

    override suspend fun submit(report: MatchReport) {
        val data = mapOf(
            "alertId" to report.alertId,
            "childName" to report.childName,
            "imageUrl" to report.imageUrl,
            "volunteerId" to report.volunteerId,
            "volunteerRole" to report.volunteerRole,
            "confidence" to report.confidence,
            "location" to GeoPoint(report.latitude, report.longitude),
            "status" to "pending",
            "timestamp" to FieldValue.serverTimestamp(),
        )
        firestore.collection(Constants.COLLECTION_MATCHES).add(data).await()
    }
}
