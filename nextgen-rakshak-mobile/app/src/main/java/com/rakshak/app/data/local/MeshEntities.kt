package com.rakshak.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A signed alert packet learned over the mesh, kept so a mid-event app restart
 * does not drop every alert until the flood happens to re-deliver it.
 *
 * The whole packet is stored verbatim; on reload it is MAC-verified and decoded
 * back into an [com.rakshak.app.data.model.Alert], thumbnail included.
 */
@Entity(tableName = "mesh_alerts")
class MeshAlertEntity(
    @PrimaryKey val alertId: String,
    val packet: ByteArray,
    val receivedAt: Long,
)

/**
 * A processed message id (or a `resolve:`/`match:` key), persisted so
 * duplicate-suppression and "case already closed" survive a restart.
 */
@Entity(tableName = "mesh_seen")
class MeshSeenEntity(
    @PrimaryKey val messageId: String,
    val seenAt: Long,
)
