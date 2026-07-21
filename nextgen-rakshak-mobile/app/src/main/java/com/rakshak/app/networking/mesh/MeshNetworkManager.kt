package com.rakshak.app.networking.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.networking.mesh.MeshPayloadCodec.MeshEnvelope
import com.rakshak.app.networking.mesh.MeshPayloadCodec.MeshMessage
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.Collections

/**
 * Offline alert distribution over Google Nearby Connections (P2P_CLUSTER).
 * Every device advertises and discovers; received payloads are re-broadcast
 * (store-and-forward) to extend reach hop-by-hop without internet. Flooding is
 * bounded by three controls: a per-id seen-set (relay each packet at most once),
 * a hop-count/TTL decremented at every relay (dropped at zero), and an expiry
 * check that drops packets tied to an already-expired alert.
 */
class MeshNetworkManager(context: Context) {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val strategy = Strategy.P2P_CLUSTER
    private val localName = "Rakshak-${android.os.Build.MODEL}"

    private val connected = Collections.synchronizedSet(mutableSetOf<String>())
    private val seenIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var running = false

    private val _alerts = MutableSharedFlow<Alert>(extraBufferCapacity = 32)
    val alerts: SharedFlow<Alert> = _alerts

    private val _matches = MutableSharedFlow<MatchReport>(extraBufferCapacity = 32)
    val matches: SharedFlow<MatchReport> = _matches

    fun start() {
        if (running) return
        running = true
        val advertising = AdvertisingOptions.Builder().setStrategy(strategy).build()
        val discovery = DiscoveryOptions.Builder().setStrategy(strategy).build()
        client.startAdvertising(localName, Constants.MESH_SERVICE_ID, connectionCallback, advertising)
            .addOnFailureListener { Log.w(TAG, "advertising failed", it) }
        client.startDiscovery(Constants.MESH_SERVICE_ID, discoveryCallback, discovery)
            .addOnFailureListener { Log.w(TAG, "discovery failed", it) }
    }

    fun stop() {
        if (!running) return
        running = false
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connected.clear()
    }

    /**
     * Broadcast an alert to the mesh. Skips alerts whose embedding hasn't been
     * computed yet (offline peers can't match without it) and de-dupes by id, so
     * the first embedding-carrying copy is the one that propagates.
     */
    fun broadcast(alert: Alert) {
        if (alert.embedding.isEmpty()) return
        if (isExpired(alert)) return
        if (!seenIds.add(alert.id)) return
        sendBytes(MeshPayloadCodec.encode(alert), exclude = null)
    }

    /** Relay a match report back toward a connected/online peer. */
    fun relayMatch(report: MatchReport) {
        sendBytes(MeshPayloadCodec.encode(report), exclude = null)
    }

    private fun sendBytes(bytes: ByteArray, exclude: String?) {
        val targets = connected.toList().filter { it != exclude }
        if (targets.isEmpty()) return
        client.sendPayload(targets, Payload.fromBytes(bytes))
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback) // auto-accept trusted mesh
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) connected.add(endpointId)
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            client.requestConnection(localName, endpointId, connectionCallback)
                .addOnFailureListener { Log.w(TAG, "requestConnection failed", it) }
        }

        override fun onEndpointLost(endpointId: String) {
            connected.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val envelope = MeshPayloadCodec.decode(bytes)
            if (envelope == null) {
                Log.w(TAG, "undecodable mesh payload")
                return
            }
            when (val msg = envelope.message) {
                is MeshMessage.AlertMessage -> handleAlert(msg.alert, bytes, envelope.ttl, endpointId)
                is MeshMessage.MatchMessage -> handleMatch(msg.report, bytes, envelope.ttl, endpointId)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private fun handleAlert(alert: Alert, raw: ByteArray, ttl: Int, from: String) {
        if (isExpired(alert)) return          // packet tied to an expired alert — drop, don't relay
        if (!seenIds.add(alert.id)) return    // already seen — don't loop
        _alerts.tryEmit(alert)
        relay(raw, ttl, from)                 // store-and-forward onward
    }

    private fun handleMatch(report: MatchReport, raw: ByteArray, ttl: Int, from: String) {
        val key = "match:${report.alertId}:${report.volunteerId}"
        if (!seenIds.add(key)) return
        _matches.tryEmit(report)
        relay(raw, ttl, from)
    }

    /** Decrement the hop-count and re-broadcast; a packet at TTL <= 1 is the last hop and isn't relayed. */
    private fun relay(raw: ByteArray, ttl: Int, from: String) {
        if (ttl <= 1) return
        sendBytes(MeshPayloadCodec.withDecrementedTtl(raw), exclude = from)
    }

    private fun isExpired(alert: Alert): Boolean =
        System.currentTimeMillis() - alert.timestamp > Constants.ALERT_EXPIRY_MILLIS

    companion object {
        private const val TAG = "MeshNetwork"
    }
}
