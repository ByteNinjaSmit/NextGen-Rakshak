package com.rakshak.app.networking.mesh

import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.utils.Constants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/**
 * Serializes mesh payloads to/from bytes. Wire layout:
 * ```
 *   byte[0]        TTL / remaining hop-count   (mutable — decremented at each relay)
 *   byte[1]        1-byte type tag             (alert / match / resolve / hello / ack)
 *   byte[2..]      message id (UUID, writeUTF) — present on every packet type
 *   ...            type-specific fields
 *   last 32 bytes  HMAC-SHA256 over byte[1 .. end-32]   (MeshCrypto)
 * ```
 *
 * The **message id** is a fresh UUID per packet; [MeshNetworkManager] keys its
 * short-lived seen-set on it, so a flooded packet is relayed at most once (FR-14)
 * while a genuinely re-issued alert (new id) is allowed to propagate again.
 *
 * The **TTL** is decremented at each relay ([withDecrementedTtl]) and a packet is
 * no longer re-broadcast once it reaches 1. Byte 0 is deliberately outside the
 * HMAC so a relay need not re-sign.
 *
 * An alert packet is ~700 bytes for a 128-float embedding, ~2.7 KB for 512, plus
 * a ≤8 KB face thumbnail — still well under the Nearby Connections 32 KB
 * BytesPayload limit. The embedding length and the thumbnail length are written
 * on the wire and read back, so either model width works without a format change
 * and a thumbnail-less alert costs 4 bytes.
 */
object MeshPayloadCodec {

    private const val TYPE_ALERT: Byte = 0x01
    private const val TYPE_MATCH: Byte = 0x02
    private const val TYPE_RESOLVE: Byte = 0x03
    private const val TYPE_HELLO: Byte = 0x04
    private const val TYPE_ACK: Byte = 0x05

    /** A decoded, MAC-verified packet plus its remaining hop-count and message id. */
    data class MeshEnvelope(val ttl: Int, val messageId: String, val message: MeshMessage)

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

        /** Peer capability announcement, exchanged on connect: does this device have internet? */
        data class HelloMessage(val hasInternet: Boolean) : MeshMessage

        /** Delivery receipt: the match packet with id [ackFor] reached an online device. */
        data class AckMessage(val ackFor: String) : MeshMessage
    }

    fun newMessageId(): String = UUID.randomUUID().toString()

    fun encode(
        alert: Alert,
        messageId: String = newMessageId(),
        ttl: Int = Constants.MESH_INITIAL_TTL,
    ): ByteArray = sign { out ->
        out.writeByte(ttl)
        out.writeByte(TYPE_ALERT.toInt())
        out.writeUTF(messageId)
        out.writeUTF(alert.id)
        out.writeUTF(alert.childName)
        out.writeUTF(alert.imageUrl)
        out.writeInt(alert.age)
        out.writeUTF(alert.gender)
        out.writeUTF(alert.clothingDesc)
        // Where the child was last seen: the one field that tells an offline
        // volunteer which way to walk. parentContact is deliberately left out —
        // mesh packets reach any nearby device, and a parent's phone number is
        // not something to flood across a festival.
        out.writeUTF(alert.lastSeen)
        out.writeInt(alert.embedding.size)
        alert.embedding.forEach(out::writeFloat)
        out.writeLong(alert.timestamp)
        val thumb = alert.thumbnail ?: ByteArray(0)
        out.writeInt(thumb.size)
        out.write(thumb)
    }

    fun encode(
        report: MatchReport,
        messageId: String = newMessageId(),
        ttl: Int = Constants.MESH_INITIAL_TTL,
    ): ByteArray = sign { out ->
        out.writeByte(ttl)
        out.writeByte(TYPE_MATCH.toInt())
        out.writeUTF(messageId)
        out.writeUTF(report.alertId)
        out.writeUTF(report.childName)
        out.writeUTF(report.imageUrl)
        out.writeUTF(report.volunteerId)
        out.writeUTF(report.volunteerRole)
        out.writeFloat(report.confidence)
        out.writeDouble(report.latitude)
        out.writeDouble(report.longitude)
        out.writeBoolean(report.hasLocation)
    }

    /** Encode a "case closed" packet for [alertId]. */
    fun encodeResolve(
        alertId: String,
        messageId: String = newMessageId(),
        ttl: Int = Constants.MESH_INITIAL_TTL,
    ): ByteArray = sign { out ->
        out.writeByte(ttl)
        out.writeByte(TYPE_RESOLVE.toInt())
        out.writeUTF(messageId)
        out.writeUTF(alertId)
    }

    /** Encode a peer HELLO. TTL 1 — HELLO is never relayed, it describes this hop only. */
    fun encodeHello(hasInternet: Boolean, messageId: String = newMessageId()): ByteArray = sign { out ->
        out.writeByte(1)
        out.writeByte(TYPE_HELLO.toInt())
        out.writeUTF(messageId)
        out.writeBoolean(hasInternet)
    }

    /** Encode a delivery receipt for the match packet [ackFor]. */
    fun encodeAck(
        ackFor: String,
        messageId: String = newMessageId(),
        ttl: Int = Constants.MESH_INITIAL_TTL,
    ): ByteArray = sign { out ->
        out.writeByte(ttl)
        out.writeByte(TYPE_ACK.toInt())
        out.writeUTF(messageId)
        out.writeUTF(ackFor)
    }

    /** Decode and MAC-verify a packet. Returns null on a bad MAC or malformed bytes. */
    fun decode(bytes: ByteArray): MeshEnvelope? {
        if (!MeshCrypto.verify(bytes)) return null
        val bodyLen = bytes.size - MeshCrypto.MAC_LEN
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes, 0, bodyLen)).use { input ->
                val ttl = input.readByte().toInt()
                val type = input.readByte()
                val messageId = input.readUTF()
                val message: MeshMessage = when (type) {
                    TYPE_ALERT -> {
                        val id = input.readUTF()
                        val childName = input.readUTF()
                        val imageUrl = input.readUTF()
                        val age = input.readInt()
                        val gender = input.readUTF()
                        val clothingDesc = input.readUTF()
                        val lastSeen = input.readUTF()
                        val embedding = FloatArray(input.readInt()) { input.readFloat() }
                        val timestamp = input.readLong()
                        val thumb = ByteArray(input.readInt()).also { input.readFully(it) }
                        MeshMessage.AlertMessage(
                            Alert(
                                id = id,
                                childName = childName,
                                imageUrl = imageUrl,
                                age = age,
                                gender = gender,
                                clothingDesc = clothingDesc,
                                lastSeen = lastSeen,
                                embedding = embedding,
                                timestamp = timestamp,
                                thumbnail = thumb.takeIf { it.isNotEmpty() },
                            )
                        )
                    }

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
                            hasLocation = input.readBoolean(),
                        )
                    )

                    TYPE_RESOLVE -> MeshMessage.ResolveMessage(input.readUTF())
                    TYPE_HELLO -> MeshMessage.HelloMessage(input.readBoolean())
                    TYPE_ACK -> MeshMessage.AckMessage(input.readUTF())
                    else -> return@use null
                }
                MeshEnvelope(ttl, messageId, message)
            }
        }.getOrNull()
    }

    /** Remaining hop-count on a raw packet, or 0 if it can't be read. */
    fun ttlOf(bytes: ByteArray): Int = bytes.firstOrNull()?.toInt() ?: 0

    /**
     * A copy of the packet with its TTL decremented by one, for relaying onward.
     * Only byte[0] changes; the body and its HMAC are untouched, so the relayed
     * packet still verifies.
     */
    fun withDecrementedTtl(bytes: ByteArray): ByteArray =
        bytes.copyOf().also { it[0] = (it[0] - 1).toByte() }

    private inline fun sign(block: (DataOutputStream) -> Unit): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use(block)
        val body = bos.toByteArray()
        return body + MeshCrypto.mac(body, from = 1)
    }
}
