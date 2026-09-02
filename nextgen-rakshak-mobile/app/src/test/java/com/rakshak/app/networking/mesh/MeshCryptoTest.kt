package com.rakshak.app.networking.mesh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshCryptoTest {

    private fun signedPacket(body: ByteArray): ByteArray = body + MeshCrypto.mac(body, from = 1)

    @Test
    fun verify_acceptsAGenuinePacket() {
        val body = byteArrayOf(6, 1, 9, 9, 9, 9)
        assertTrue(MeshCrypto.verify(signedPacket(body)))
    }

    @Test
    fun verify_ignoresByteZero_soRelayedTtlStillPasses() {
        val body = byteArrayOf(6, 1, 42, 7)
        val packet = signedPacket(body)
        packet[0] = 5 // relay decremented the TTL
        assertTrue(MeshCrypto.verify(packet))
    }

    @Test
    fun verify_rejectsATamperedBody() {
        val packet = signedPacket(byteArrayOf(6, 1, 42, 7))
        packet[2] = (packet[2] + 1).toByte()
        assertFalse(MeshCrypto.verify(packet))
    }

    @Test
    fun verify_rejectsTooShort() {
        assertFalse(MeshCrypto.verify(byteArrayOf(1, 2, 3)))
    }
}
