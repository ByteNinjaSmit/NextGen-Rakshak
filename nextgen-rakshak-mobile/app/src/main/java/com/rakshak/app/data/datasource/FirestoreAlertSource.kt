package com.rakshak.app.data.datasource

import com.google.firebase.firestore.FirebaseFirestore
import com.rakshak.app.data.model.Alert
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Streams active alerts from Firestore in real time. */
class FirestoreAlertSource(
    private val firestore: FirebaseFirestore,
) : AlertDataSource {

    override fun observeActiveAlerts(): Flow<List<Alert>> = callbackFlow {
        val registration = firestore.collection(Constants.COLLECTION_ALERTS)
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val alerts = snapshot?.documents.orEmpty().map { doc ->
                    Alert(
                        id = doc.id,
                        childName = doc.getString("childName").orEmpty(),
                        age = (doc.getLong("age") ?: 0L).toInt(),
                        gender = doc.getString("gender").orEmpty(),
                        clothingDesc = doc.getString("clothingDesc").orEmpty(),
                        parentContact = doc.getString("parentContact").orEmpty(),
                        imageUrl = doc.getString("imageUrl").orEmpty(),
                        embedding = (doc.get("embedding") as? List<*>)
                            ?.mapNotNull { (it as? Number)?.toFloat() }
                            ?.toFloatArray() ?: FloatArray(0),
                        lastSeen = doc.getString("lastSeen").orEmpty(),
                        status = doc.getString("status") ?: "active",
                        // Milliseconds, not seconds: Alert.timestamp is compared against
                        // System.currentTimeMillis() for expiry and elapsed-time display.
                        timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                    )
                }
                trySend(alerts)
            }
        awaitClose { registration.remove() }
    }
}
