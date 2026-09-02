package com.rakshak.app.data.datasource

import com.rakshak.app.data.local.MeshAlertEntity
import com.rakshak.app.data.local.MeshDao
import com.rakshak.app.data.local.MeshSeenEntity
import com.rakshak.app.utils.Constants

/**
 * Durable backing for [com.rakshak.app.networking.mesh.MeshNetworkManager]: the
 * alerts it has heard and the message ids it has processed. Without this, closing
 * and reopening the app mid-event drops every mesh-learned alert until the flood
 * happens to re-deliver it.
 */
class MeshStore(private val dao: MeshDao) {

    data class Loaded(
        val alertPackets: List<ByteArray>,
        val seen: Map<String, Long>,
    )

    /** Prune anything past the alert lifetime, then return what remains. */
    suspend fun load(): Loaded {
        val cutoff = System.currentTimeMillis() - Constants.ALERT_EXPIRY_MILLIS
        dao.pruneAlerts(cutoff)
        dao.pruneSeen(cutoff)
        return Loaded(
            alertPackets = dao.allAlerts().map { it.packet },
            seen = dao.allSeen().associate { it.messageId to it.seenAt },
        )
    }

    suspend fun saveAlert(alertId: String, packet: ByteArray) =
        dao.putAlert(MeshAlertEntity(alertId, packet, System.currentTimeMillis()))

    suspend fun deleteAlert(alertId: String) = dao.deleteAlert(alertId)

    suspend fun saveSeen(messageId: String) =
        dao.putSeen(MeshSeenEntity(messageId, System.currentTimeMillis()))
}
