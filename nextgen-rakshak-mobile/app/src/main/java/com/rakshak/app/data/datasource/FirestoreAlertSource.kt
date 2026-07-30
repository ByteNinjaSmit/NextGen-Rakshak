package com.rakshak.app.data.datasource

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.rakshak.app.data.model.Alert
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/**
 * One Firestore snapshot: the alerts that are active now, plus the ids that
 * dropped out of the query since the previous snapshot.
 *
 * [closedIds] is the only trustworthy "this case is over" signal available to a
 * device. Diffing consecutive [active] lists cannot substitute for it, because a
 * listener error also yields an empty list — so a dropped connection would be
 * indistinguishable from every child being found at once.
 */
data class AlertSnapshot(
    val active: List<Alert>,
    val closedIds: List<String> = emptyList(),
)

/** Streams active alerts from Firestore in real time. */
class FirestoreAlertSource(
    private val firestore: FirebaseFirestore,
) : AlertDataSource {

    override fun observeActiveAlerts(): Flow<List<Alert>> = observeChanges().map { it.active }

    /**
     * As [observeActiveAlerts], but also reporting which alerts left the active
     * set. Used to tell the offline mesh that a case is closed; peers with no
     * internet have no other way to find out.
     */
    fun observeChanges(): Flow<AlertSnapshot> = callbackFlow {
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
                    //
                    // No closedIds here: an error is not evidence that anything was
                    // resolved, and reporting one would purge live alerts from the mesh.
                    Log.w(TAG, "Alert listener error; treating as no alerts", error)
                    trySend(AlertSnapshot(emptyList()))
                    return@addSnapshotListener
                }
                val closed = snapshot?.documentChanges.orEmpty()
                    .filter { it.type == DocumentChange.Type.REMOVED }
                    .map { it.document.id }
                trySend(AlertSnapshot(snapshot?.documents.orEmpty().map(::toAlert), closed))
            }
        awaitClose { registration.remove() }
    }

    private fun toAlert(doc: DocumentSnapshot) = Alert(
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

    private companion object {
        const val TAG = "FirestoreAlertSource"
    }
}
