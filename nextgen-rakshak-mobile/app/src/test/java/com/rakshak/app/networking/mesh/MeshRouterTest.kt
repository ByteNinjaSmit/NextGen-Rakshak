package com.rakshak.app.networking.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRouterTest {

    @Test
    fun shouldRelay_stopsAtTheLastHop() {
        assertTrue(MeshRouter.shouldRelay(6))
        assertTrue(MeshRouter.shouldRelay(2))
        assertFalse(MeshRouter.shouldRelay(1))
        assertFalse(MeshRouter.shouldRelay(0))
    }

    @Test
    fun broadcastTargets_excludeTheSender() {
        val out = MeshRouter.broadcastTargets(setOf("a", "b", "c"), exclude = "b")
        assertEquals(setOf("a", "c"), out.toSet())
    }

    @Test
    fun matchTargets_preferOnlinePeers() {
        val out = MeshRouter.matchTargets(
            connected = setOf("a", "b", "c"),
            onlinePeers = setOf("b"),
            exclude = null,
        )
        assertEquals(listOf("b"), out)
    }

    @Test
    fun matchTargets_floodWhenNoGatewayIsConnected() {
        val out = MeshRouter.matchTargets(
            connected = setOf("a", "b", "c"),
            onlinePeers = emptySet(),
            exclude = "a",
        )
        assertEquals(setOf("b", "c"), out.toSet())
    }
}
