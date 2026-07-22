package com.rakshak.app.data.datasource

import android.util.Log
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
                    // Report an empty list and keep the flow open rather than failing it.
                    // Closing with the exception rethrows it in every collector, and since
                    // these are collected in a ViewModel coroutine that took the whole app
                    // down — a signed-out moment, a rules change, or a dropped connection
                    // would kill the app mid-search. The mesh can still deliver alerts
                    // when Firestore cannot.
                    Log.w(TAG, "Alert listener error; treating as no alerts", error)
                    trySend(emptyList())
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

    private companion object {
        const val TAG = "FirestoreAlertSource"
    }
}
