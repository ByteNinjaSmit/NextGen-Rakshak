package com.rakshak.app.networking.mesh

/**
 * Pure routing decisions for [MeshNetworkManager], split out so the flood /
 * relay / gateway logic is unit-testable without a Nearby client.
 *
 * Nearby Connections links pairs of devices; multi-hop reach is this
 * application-level store-and-forward layer on top of it.
 */
object MeshRouter {

    /**
     * A packet is relayed only while its remaining hop-count is above 1: at
     * TTL 1 this device is the last hop and re-broadcasting would just add a
     * duplicate that every neighbour drops.
     */
    fun shouldRelay(ttl: Int): Boolean = ttl > 1

    /** Every connected peer except the one a packet came from. */
    fun broadcastTargets(connected: Set<String>, exclude: String?): List<String> =
        connected.filter { it != exclude }

    /**
     * Where to send a match report. A confirmed sighting only matters once it
     * reaches a device with internet, so prefer peers that announced internet
     * access in their HELLO. If none is connected, fall back to flooding every
     * peer — the report then moves toward a gateway hop by hop.
     */
    fun matchTargets(
        connected: Set<String>,
        onlinePeers: Set<String>,
        exclude: String?,
    ): List<String> {
        val pool = connected.filter { it != exclude }
        val gateways = pool.filter { it in onlinePeers }
        return gateways.ifEmpty { pool }
    }
}
