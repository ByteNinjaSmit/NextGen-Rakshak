package com.rakshak.app.networking.mesh

import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.networking.mesh.MeshPayloadCodec.MeshMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshPayloadCodecTest {

    private fun sampleAlert(thumbnail: ByteArray? = null) = Alert(
        id = "a1",
        childName = "Priya",
        age = 5,
        gender = "Female",
        clothingDesc = "red frock",
        imageUrl = "https://x/y.jpg",
        lastSeen = "near Gate 3 food court",
        parentContact = "+919000000000",
        embedding = FloatArray(128) { it * 0.01f },
        timestamp = 1_720_000_000L,
        thumbnail = thumbnail,
    )

    @Test
    fun alert_roundTrips_withThumbnail() {
        val thumb = ByteArray(2500) { (it % 251).toByte() }
        val alert = sampleAlert(thumb)

        val envelope = MeshPayloadCodec.decode(MeshPayloadCodec.encode(alert))
        assertNotNull(envelope)
        val out = (envelope!!.message as MeshMessage.AlertMessage).alert
        assertEquals(alert.id, out.id)
        assertEquals(alert.childName, out.childName)
        assertEquals(alert.age, out.age)
        assertEquals(alert.lastSeen, out.lastSeen)
        assertArrayEquals(alert.embedding, out.embedding, 1e-6f)
        assertEquals(alert.timestamp, out.timestamp)
        assertArrayEquals(thumb, out.thumbnail)
    }

    @Test
    fun alert_withoutThumbnail_decodesNull() {
        val out = (MeshPayloadCodec.decode(MeshPayloadCodec.encode(sampleAlert()))!!
            .message as MeshMessage.AlertMessage).alert
        assertNull(out.thumbnail)
    }

    @Test
    fun everyPacket_carriesAStableMessageId() {
        val id = MeshPayloadCodec.newMessageId()
        val raw = MeshPayloadCodec.encode(sampleAlert(), messageId = id)

        assertEquals(id, MeshPayloadCodec.decode(raw)!!.messageId)
        // Survives a relay (TTL decrement must not disturb the body or its MAC).
        assertEquals(id, MeshPayloadCodec.decode(MeshPayloadCodec.withDecrementedTtl(raw))!!.messageId)
    }

    @Test
    fun tamperedPacket_isRejected() {
        val raw = MeshPayloadCodec.encode(sampleAlert())
        // Flip a byte inside the body (not the TTL, not the trailing MAC).
        raw[10] = (raw[10] + 1).toByte()
        assertNull(MeshPayloadCodec.decode(raw))
    }

    @Test
    fun truncatedMac_isRejected() {
        val raw = MeshPayloadCodec.encode(sampleAlert())
        assertNull(MeshPayloadCodec.decode(raw.copyOf(raw.size - 1)))
    }

    @Test
    fun alert_doesNotCarryParentContact() {
        val raw = MeshPayloadCodec.encode(sampleAlert())
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("+919000000000"))
        val out = (MeshPayloadCodec.decode(raw)!!.message as MeshMessage.AlertMessage).alert
        assertEquals("", out.parentContact)
    }

    @Test
    fun match_roundTrips() {
        val report = MatchReport(
            alertId = "a1", childName = "Priya", imageUrl = "https://x/y.jpg",
            volunteerId = "v9", volunteerRole = "ncc", confidence = 0.87f,
            latitude = 18.52, longitude = 73.85, hasLocation = true,
        )
        val out = (MeshPayloadCodec.decode(MeshPayloadCodec.encode(report))!!
            .message as MeshMessage.MatchMessage).report
        assertEquals(report.volunteerId, out.volunteerId)
        assertEquals(report.confidence, out.confidence, 1e-6f)
        assertEquals(report.latitude, out.latitude, 1e-9)
        assertEquals(report.longitude, out.longitude, 1e-9)
        assertTrue(out.hasLocation)
    }

    @Test
    fun match_carriesTheNoLocationFlag() {
        val report = MatchReport(
            alertId = "a1", childName = "Priya", imageUrl = "", volunteerId = "v9",
            volunteerRole = "ncc", confidence = 0.5f, latitude = 0.0, longitude = 0.0,
            hasLocation = false,
        )
        val out = (MeshPayloadCodec.decode(MeshPayloadCodec.encode(report))!!
            .message as MeshMessage.MatchMessage).report
        assertFalse(out.hasLocation)
    }

    @Test
    fun resolve_roundTrips_andTtlDecrements() {
        val raw = MeshPayloadCodec.encodeResolve("a1")
        assertEquals(6, MeshPayloadCodec.ttlOf(raw))

        val relayed = MeshPayloadCodec.withDecrementedTtl(raw)
        assertEquals(5, MeshPayloadCodec.ttlOf(relayed))
        assertEquals(
            "a1",
            (MeshPayloadCodec.decode(relayed)!!.message as MeshMessage.ResolveMessage).alertId,
        )
    }

    @Test
    fun hello_roundTrips() {
        val out = MeshPayloadCodec.decode(MeshPayloadCodec.encodeHello(hasInternet = true))!!
        assertTrue((out.message as MeshMessage.HelloMessage).hasInternet)
        assertEquals(1, out.ttl)
    }

    @Test
    fun ack_roundTrips() {
        val out = MeshPayloadCodec.decode(MeshPayloadCodec.encodeAck("msg-123"))!!
        assertEquals("msg-123", (out.message as MeshMessage.AckMessage).ackFor)
    }

    @Test
    fun garbage_returnsNull() {
        assertNull(MeshPayloadCodec.decode(byteArrayOf(0x7F, 0x00, 0x01)))
    }

    @Test
    fun ttl_defaultsAndDecrements() {
        val raw = MeshPayloadCodec.encode(sampleAlert())
        assertEquals(6, MeshPayloadCodec.ttlOf(raw))
        assertEquals(5, MeshPayloadCodec.ttlOf(MeshPayloadCodec.withDecrementedTtl(raw)))
    }
}
