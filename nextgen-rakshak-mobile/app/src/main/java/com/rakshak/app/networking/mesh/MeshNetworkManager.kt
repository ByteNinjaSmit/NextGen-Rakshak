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
import com.rakshak.app.networking.mesh.MeshPayloadCodec.MeshMessage
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/**
 * Offline alert distribution over Google Nearby Connections (P2P_CLUSTER).
 * Every device advertises and discovers; received payloads are re-broadcast
 * (store-and-forward) to extend reach hop-by-hop without internet. Flooding is
 * bounded by three controls: a per-id seen-set (relay each packet at most once),
 * a hop-count/TTL decremented at every relay (dropped at zero), and an expiry
 * check that drops packets tied to an already-expired alert.
 *
 * This class owns the device's view of the mesh: [alerts] is the authoritative,
 * de-duplicated set of alerts learned over the radio. It is held here rather
 * than rebuilt per collector so that navigating between screens — or leaving
 * Home long enough for its `WhileSubscribed` window to lapse — does not lose
 * alerts that will never be sent again.
 */
class MeshNetworkManager(context: Context) {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val strategy = Strategy.P2P_CLUSTER
    private val localName = "Rakshak-${android.os.Build.MODEL}"

    private val connected = Collections.synchronizedSet(mutableSetOf<String>())
    private val seenIds = Collections.synchronizedSet(mutableSetOf<String>())
    /** Alert ids known to be closed. Stops a still-circulating packet re-adding one. */
    private val resolvedIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var running = false

    /**
     * Alerts learned over the mesh, keyed by id. Guarded by `this` because the
     * Nearby callbacks arrive on a background thread while the UI reads [alerts].
     */
    private val known = LinkedHashMap<String, Alert>()

    /**
     * Alerts this device wants to put on the wire but could not yet, because no
     * peer was connected at the time. Flushed when a connection comes up.
     */
    private val outbox = LinkedHashMap<String, Alert>()

    /** Closures awaiting a peer, for the same reason as [outbox]. */
    private val resolveOutbox = LinkedHashSet<String>()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    /** Every unexpired alert this device has heard over the mesh. */
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    // Replay: the app's uploader bridge suspends on sign-in before it starts
    // collecting, and a match relayed in that window would otherwise be dropped
    // by a zero-replay SharedFlow — losing the sighting entirely.
    private val _matches = MutableSharedFlow<MatchReport>(replay = 32, extraBufferCapacity = 32)
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
     * computed yet (offline peers can't match without it).
     *
     * An alert is marked seen only once it is actually on the wire. Marking it up
     * front reads like harmless de-duplication but silently destroys the offline
     * path: this is called for every alert on the first Firestore snapshot, which
     * lands well before Nearby has discovered anybody, so every alert would be
     * recorded as sent while [sendBytes] dropped it for want of a peer — and
     * never be offered again. Unsendable alerts go to the [outbox] instead and
     * are flushed the moment a peer connects.
     */
    fun broadcast(alert: Alert) {
        if (alert.embedding.isEmpty()) return
        if (isExpired(alert)) return
        if (alert.id in resolvedIds) return
        if (alert.id in seenIds) return

        if (!sendBytes(MeshPayloadCodec.encode(alert), exclude = null)) {
            synchronized(this) { outbox[alert.id] = alert }
            return
        }
        seenIds.add(alert.id)
    }

    /**
     * Drop a resolved alert and tell the mesh to do the same.
     *
     * Called when the kiosk closes the case. Offline peers cannot observe that
     * themselves — the alert simply stops appearing in a Firestore query they
     * cannot reach — so without this packet a volunteer keeps scanning for a
     * child who is already safe, until the 8 h expiry eventually clears it.
     */
    fun forget(alertId: String) {
        resolvedIds.add(alertId)
        val changed = synchronized(this) {
            outbox.remove(alertId)
            known.remove(alertId) != null
        }
        if (changed) publish()

        // Same rule as broadcast(): a closure is only "sent" once it reached a
        // peer. Recorded as seen with nobody listening, it would never be
        // offered again and the mesh would keep hunting for a child already found.
        if ("resolve:$alertId" in seenIds) return
        if (sendBytes(MeshPayloadCodec.encodeResolve(alertId), exclude = null)) {
            seenIds.add("resolve:$alertId")
        } else {
            synchronized(this) { resolveOutbox.add(alertId) }
        }
    }

    /** Relay a match report back toward a connected/online peer. */
    fun relayMatch(report: MatchReport) {
        sendBytes(MeshPayloadCodec.encode(report), exclude = null)
    }

    /** @return true if the payload was handed to at least one peer. */
    private fun sendBytes(bytes: ByteArray, exclude: String?): Boolean {
        val targets = connected.toList().filter { it != exclude }
        if (targets.isEmpty()) return false
        client.sendPayload(targets, Payload.fromBytes(bytes))
            .addOnFailureListener { Log.w(TAG, "sendPayload failed", it) }
        return true
    }

    /** Re-offer everything that had no peer to go to when it was first sent. */
    private fun flushOutbox() {
        // Closures first: no point handing a peer an alert this device already
        // knows is closed.
        val closures = synchronized(this) { resolveOutbox.toList() }
        for (alertId in closures) {
            if (sendBytes(MeshPayloadCodec.encodeResolve(alertId), exclude = null)) {
                seenIds.add("resolve:$alertId")
                synchronized(this) { resolveOutbox.remove(alertId) }
            }
        }

        val queued = synchronized(this) { outbox.values.toList() }
        if (queued.isEmpty()) return
        Log.i(TAG, "flushing ${queued.size} queued alert(s) to the mesh")
        for (alert in queued) {
            if (isExpired(alert) || alert.id in resolvedIds) {
                synchronized(this) { outbox.remove(alert.id) }
                continue
            }
            if (sendBytes(MeshPayloadCodec.encode(alert), exclude = null)) {
                seenIds.add(alert.id)
                synchronized(this) { outbox.remove(alert.id) }
            }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback) // auto-accept trusted mesh
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (!resolution.status.isSuccess) return
            connected.add(endpointId)
            // A peer at last: everything gathered while alone can go now.
            flushOutbox()
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
                is MeshMessage.ResolveMessage ->
                    handleResolve(msg.alertId, bytes, envelope.ttl, endpointId)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private fun handleAlert(alert: Alert, raw: ByteArray, ttl: Int, from: String) {
        if (isExpired(alert)) return            // packet tied to an expired alert — drop, don't relay
        if (alert.id in resolvedIds) return     // case already closed — don't resurrect it
        if (!seenIds.add(alert.id)) return      // already seen — don't loop
        synchronized(this) { known[alert.id] = alert }
        publish()
        relay(raw, ttl, from)                   // store-and-forward onward
    }

    private fun handleMatch(report: MatchReport, raw: ByteArray, ttl: Int, from: String) {
        val key = "match:${report.alertId}:${report.volunteerId}"
        if (!seenIds.add(key)) return
        _matches.tryEmit(report)
        relay(raw, ttl, from)
    }

    private fun handleResolve(alertId: String, raw: ByteArray, ttl: Int, from: String) {
        if (!seenIds.add("resolve:$alertId")) return
        resolvedIds.add(alertId)
        val changed = synchronized(this) {
            outbox.remove(alertId)
            known.remove(alertId) != null
        }
        if (changed) publish()
        relay(raw, ttl, from)
    }

    /** Decrement the hop-count and re-broadcast; a packet at TTL <= 1 is the last hop and isn't relayed. */
    private fun relay(raw: ByteArray, ttl: Int, from: String) {
        if (ttl <= 1) return
        sendBytes(MeshPayloadCodec.withDecrementedTtl(raw), exclude = from)
    }

    /**
     * Re-emit the known set, dropping anything past its TTL. Pruning on every
     * change keeps the map from growing across a multi-day event; collectors
     * re-filter on a timer as well, since expiry arrives with the clock and not
     * in response to any packet.
     */
    private fun publish() {
        _alerts.value = synchronized(this) {
            known.values.removeAll { isExpired(it) }
            known.values.toList()
        }
    }

    private fun isExpired(alert: Alert): Boolean =
        System.currentTimeMillis() - alert.timestamp > Constants.ALERT_EXPIRY_MILLIS

    companion object {
        private const val TAG = "MeshNetwork"
    }
}
