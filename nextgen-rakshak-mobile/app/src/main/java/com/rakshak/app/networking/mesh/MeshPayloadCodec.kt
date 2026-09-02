package com.rakshak.app.networking.mesh

import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.utils.Constants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Serializes mesh payloads to/from bytes. Wire layout:
 *   byte[0]       = TTL / remaining hop-count (store-and-forward routing)
 *   byte[1]       = 1-byte type tag (alert / match / resolve)
 *   byte[2..]     = payload fields
 *
 * TTL is decremented at each relay ([withDecrementedTtl]) and a packet is no
 * longer re-broadcast once it reaches zero, capping how far a packet floods.
 * ~700 bytes for an alert with a 128-float embedding, ~2.7 KB with a 512-float
 * one — both well under the Nearby Connections 32 KB BytesPayload limit. The
 * embedding length is written on the wire and read back, so either model works
 * without a format change.
 */
object MeshPayloadCodec {

    private const val TYPE_ALERT: Byte = 0x01
    private const val TYPE_MATCH: Byte = 0x02
    private const val TYPE_RESOLVE: Byte = 0x03

    /** A decoded packet plus its remaining hop-count. */
    data class MeshEnvelope(val ttl: Int, val message: MeshMessage)

    sealed interface MeshMessage {
        data class AlertMessage(val alert: Alert) : MeshMessage
        data class MatchMessage(val report: MatchReport) : MeshMessage

        /**
         * "This case is closed — stop scanning for it." Floods the mesh the same
         * way an alert does, because an offline device has no other way to learn
         * that the child was found: the alert simply disappears from the kiosk's
         * Firestore query, and absence is not a signal that reaches a peer with
         * no internet.
         */
        data class ResolveMessage(val alertId: String) : MeshMessage
    }

    fun encode(alert: Alert, ttl: Int = Constants.MESH_INITIAL_TTL): ByteArray = build { out ->
        out.writeByte(ttl)
        out.writeByte(TYPE_ALERT.toInt())
        out.writeUTF(alert.id)
        out.writeUTF(alert.childName)
        out.writeUTF(alert.imageUrl)
        out.writeInt(alert.age)
        out.writeUTF(alert.gender)
        out.writeUTF(alert.clothingDesc)
        // Where the child was last seen: the one field that tells an offline
        // volunteer which way to walk. parentContact is deliberately left out —
        // mesh packets are unauthenticated and reach any nearby device, and a
        // parent's phone number is not something to flood across a festival.
        out.writeUTF(alert.lastSeen)
        out.writeInt(alert.embedding.size)
        alert.embedding.forEach(out::writeFloat)
        out.writeLong(alert.timestamp)
    }

    /** Encode a "case closed" packet for [alertId]. */
    fun encodeResolve(alertId: String, ttl: Int = Constants.MESH_INITIAL_TTL): ByteArray = build { out ->
        out.writeByte(ttl)
        out.writeByte(TYPE_RESOLVE.toInt())
        out.writeUTF(alertId)
    }

    fun encode(report: MatchReport, ttl: Int = Constants.MESH_INITIAL_TTL): ByteArray = build { out ->
        out.writeByte(ttl)
        out.writeByte(TYPE_MATCH.toInt())
        out.writeUTF(report.alertId)
        out.writeUTF(report.childName)
        out.writeUTF(report.imageUrl)
        out.writeUTF(report.volunteerId)
        out.writeUTF(report.volunteerRole)
        out.writeFloat(report.confidence)
        out.writeDouble(report.latitude)
        out.writeDouble(report.longitude)
    }

    fun decode(bytes: ByteArray): MeshEnvelope? = runCatching {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val ttl = input.readByte().toInt()
            val message = when (input.readByte()) {
                TYPE_ALERT -> MeshMessage.AlertMessage(
                    Alert(
                        id = input.readUTF(),
                        childName = input.readUTF(),
                        imageUrl = input.readUTF(),
                        age = input.readInt(),
                        gender = input.readUTF(),
                        clothingDesc = input.readUTF(),
                        lastSeen = input.readUTF(),
                        embedding = FloatArray(input.readInt()) { input.readFloat() },
                        timestamp = input.readLong(),
                    )
                )
                TYPE_MATCH -> MeshMessage.MatchMessage(
                    MatchReport(
                        alertId = input.readUTF(),
                        childName = input.readUTF(),
                        imageUrl = input.readUTF(),
                        volunteerId = input.readUTF(),
                        volunteerRole = input.readUTF(),
                        confidence = input.readFloat(),
                        latitude = input.readDouble(),
                        longitude = input.readDouble(),
                    )
                )
                TYPE_RESOLVE -> MeshMessage.ResolveMessage(input.readUTF())
                else -> return@use null
            }
            MeshEnvelope(ttl, message)
        }
    }.getOrNull()

    /** Remaining hop-count on a raw packet, or 0 if it can't be read. */
    fun ttlOf(bytes: ByteArray): Int = bytes.firstOrNull()?.toInt() ?: 0

    /**
     * Returns a copy of the packet with its TTL decremented by one, for relaying
     * onward. Only byte[0] changes; the payload is untouched so it isn't re-serialized.
     */
    fun withDecrementedTtl(bytes: ByteArray): ByteArray =
        bytes.copyOf().also { it[0] = (it[0] - 1).toByte() }

    private inline fun build(block: (DataOutputStream) -> Unit): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use(block)
        return bos.toByteArray()
    }
}
