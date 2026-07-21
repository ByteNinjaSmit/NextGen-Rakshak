package com.rakshak.app.networking.mesh

import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.networking.mesh.MeshPayloadCodec.MeshMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshPayloadCodecTest {

    @Test
    fun alert_roundTrips() {
        val alert = Alert(
            id = "a1",
            childName = "Priya",
            age = 5,
            gender = "Female",
            clothingDesc = "red frock",
            imageUrl = "https://x/y.jpg",
            embedding = FloatArray(128) { it * 0.01f },
            timestamp = 1_720_000_000L,
        )

        val decoded = MeshPayloadCodec.decode(MeshPayloadCodec.encode(alert))?.message
        assertTrue(decoded is MeshMessage.AlertMessage)
        val out = (decoded as MeshMessage.AlertMessage).alert
        assertEquals(alert.id, out.id)
        assertEquals(alert.childName, out.childName)
        assertEquals(alert.age, out.age)
        assertArrayEquals(alert.embedding, out.embedding, 1e-6f)
        assertEquals(alert.timestamp, out.timestamp)
    }

    @Test
    fun match_roundTrips() {
        val report = MatchReport(
            alertId = "a1",
            childName = "Priya",
            imageUrl = "https://x/y.jpg",
            volunteerId = "v9",
            volunteerRole = "ncc",
            confidence = 0.87f,
            latitude = 18.52,
            longitude = 73.85,
        )

        val decoded = MeshPayloadCodec.decode(MeshPayloadCodec.encode(report))?.message
        assertTrue(decoded is MeshMessage.MatchMessage)
        val out = (decoded as MeshMessage.MatchMessage).report
        assertEquals(report.volunteerId, out.volunteerId)
        assertEquals(report.confidence, out.confidence, 1e-6f)
        assertEquals(report.latitude, out.latitude, 1e-9)
        assertEquals(report.longitude, out.longitude, 1e-9)
    }

    @Test
    fun garbage_returnsNull() {
        assertNull(MeshPayloadCodec.decode(byteArrayOf(0x7F, 0x00, 0x01)))
    }

    @Test
    fun ttl_defaultsAndDecrements() {
        val alert = Alert(
            id = "a1", childName = "Priya", age = 5, gender = "Female",
            clothingDesc = "red frock", imageUrl = "https://x/y.jpg",
            embedding = FloatArray(128) { it * 0.01f }, timestamp = 1_720_000_000L,
        )

        val raw = MeshPayloadCodec.encode(alert) // default TTL
        assertEquals(6, MeshPayloadCodec.ttlOf(raw))

        val relayed = MeshPayloadCodec.withDecrementedTtl(raw)
        assertEquals(5, MeshPayloadCodec.ttlOf(relayed))
        // Decrementing TTL must not corrupt the payload — it still decodes to the same alert.
        assertEquals("a1", (MeshPayloadCodec.decode(relayed)?.message as MeshMessage.AlertMessage).alert.id)
    }
}
