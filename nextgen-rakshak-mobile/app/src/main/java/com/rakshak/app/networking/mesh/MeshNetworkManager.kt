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
import com.rakshak.app.data.datasource.MeshStore
import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.networking.mesh.MeshPayloadCodec.MeshMessage
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Offline alert distribution over Google Nearby Connections (P2P_CLUSTER).
 * Nearby links pairs of nearby devices; this class is the application-level
 * store-and-forward layer that turns those pairwise links into multi-hop reach:
 * every received packet is re-broadcast (minus its sender) until its hop-count
 * runs out.
 *
 * Flooding is bounded by four controls:
 *  - a per-message-id [MeshSeenCache] — each packet is relayed at most once, and
 *    the cache evicts old ids so it does not grow across a multi-day event;
 *  - a hop-count / TTL decremented at every relay, dropped at 1;
 *  - an expiry check that drops packets whose parent alert has aged out (8 h);
 *  - a resolved-id set so a still-circulating packet cannot resurrect a case the
 *    kiosk has already closed.
 *
 * This class owns the device's view of the mesh: [alerts] is the authoritative,
 * de-duplicated set of alerts learned over the radio, held here (and mirrored to
 * [MeshStore]) so navigating between screens — or restarting the app mid-event —
 * does not lose alerts that may not be flooded again.
 *
 * It is also the only window onto a subsystem with no other observable output.
 * A mesh either relays or silently does not, so [peers], [stats] and [events]
 * are first-class state rather than debug extras: they are what a volunteer (and
 * the VER-08 field trial) has to go on when deciding whether the mesh is up.
 */
class MeshNetworkManager(
    context: Context,
    private val store: MeshStore? = null,
    private val connectivity: StateFlow<Boolean>? = null,
) {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val strategy = Strategy.P2P_CLUSTER
    private val localName = "Rakshak-${android.os.Build.MODEL}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One connected mesh neighbour, as the mesh screen lists it. */
    data class PeerInfo(
        val endpointId: String,
        val name: String,
        val isGateway: Boolean,
        val connectedAtMillis: Long,
    )

    /** Packet counters and queue depths for the mesh screen. */
    data class MeshStats(
        val sent: Long = 0,
        val received: Long = 0,
        val relayed: Long = 0,
        val dropped: Long = 0,
        val outbox: Int = 0,
        val pendingAcks: Int = 0,
    )

    /** What a log line is about, so the screen can colour and filter it. */
    enum class EventKind { SYSTEM, PEER, ALERT, MATCH, RESOLVE, ACK, RELAY, ERROR }

    /** One line of the rolling mesh activity log. */
    data class MeshEvent(
        val seq: Long,
        val atMillis: Long,
        val kind: EventKind,
        val text: String,
    )

    private data class PeerRecord(val name: String, val connectedAtMillis: Long)

    /** Connected endpoints, with the name and connect time the UI shows. */
    private val connected = ConcurrentHashMap<String, PeerRecord>()

    /** Names learned while discovering / accepting, before [connected] has a record. */
    private val endpointNames = ConcurrentHashMap<String, String>()

    /** Peers that announced internet access in their HELLO — preferred targets for match reports. */
    private val onlinePeers = Collections.synchronizedSet(mutableSetOf<String>())

    /** Short-lived: relay-loop and duplicate-upload suppression, keyed by message id. */
    private val seen = MeshSeenCache()

    /** Alert ids known to be closed. Stops a still-circulating packet re-adding one. */
    private val resolvedIds = Collections.synchronizedSet(mutableSetOf<String>())
    /**
     * Alert / resolve ids this device has itself put on the wire — outbound
     * de-dup so `broadcast()`, called for every alert on every Firestore
     * snapshot, does not re-send. Time-windowed like [seen] so it cannot grow
     * across a multi-day event; an entry aging out is harmless because the alert
     * it names has expired by then.
     */
    private val broadcasted = MeshSeenCache()

    private val _running = MutableStateFlow(false)
    /** Whether advertising/discovery are up — the mesh screen's master switch reads this. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var isRunning: Boolean
        get() = _running.value
        set(value) { _running.value = value }

    private val _selfOnline = MutableStateFlow(false)
    /** Whether this device can reach the internet, i.e. can act as the mesh's gateway. */
    val selfOnline: StateFlow<Boolean> = _selfOnline.asStateFlow()

    private val selfOnlineNow: Boolean get() = _selfOnline.value

    /**
     * Alerts learned over the mesh, keyed by id. Guarded by `this` because the
     * Nearby callbacks arrive on a background thread while the UI reads [alerts].
     */
    private val known = LinkedHashMap<String, Alert>()

    /** Alerts this device wants on the wire but could not send yet (no peer). */
    private val outbox = LinkedHashMap<String, Alert>()
    /** Closures awaiting a peer, for the same reason as [outbox]. */
    private val resolveOutbox = LinkedHashSet<String>()

    /** In-flight payloads, so a FAILURE in [onPayloadTransferUpdate] can be retried once. */
    private data class PendingSend(val bytes: ByteArray, val endpointId: String, val attempts: Int)
    private val pendingSends = ConcurrentHashMap<Long, PendingSend>()

    /**
     * Match packets this device originated that have not yet been ACKed by an
     * online peer. Keyed by the packet's message id. Re-sent on a timer until an
     * ACK arrives, this device itself comes online, or the attempt budget runs
     * out — at which point the origin's Room queue is still the safety net.
     */
    private data class PendingMatchAck(val bytes: ByteArray, val attempts: Int)
    private val pendingMatchAcks = ConcurrentHashMap<String, PendingMatchAck>()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    /** Every unexpired alert this device has heard over the mesh. */
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    // Replay: the app's uploader bridge suspends on sign-in before it starts
    // collecting, and a match relayed in that window would otherwise be dropped
    // by a zero-replay SharedFlow — losing the sighting entirely.
    private val _matches = MutableSharedFlow<RelayedMatch>(replay = 32, extraBufferCapacity = 32)
    val matches: SharedFlow<RelayedMatch> = _matches

    private val _peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    /** Connected neighbours, newest link last. */
    val peers: StateFlow<List<PeerInfo>> = _peers.asStateFlow()

    private val _peerCount = MutableStateFlow(0)
    /** How many mesh peers are connected right now — surfaced in the foreground notification and the mesh screen. */
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    private val _stats = MutableStateFlow(MeshStats())
    /** Packet counters and queue depths. */
    val stats: StateFlow<MeshStats> = _stats.asStateFlow()

    private val _events = MutableStateFlow<List<MeshEvent>>(emptyList())
    /** Rolling packet log for the mesh screen and for VER-08 hop timing. */
    val events: StateFlow<List<MeshEvent>> = _events.asStateFlow()

    private val eventSeq = AtomicLong(0)

    /** A match plus the id of the packet that carried it, so the uploader can ACK delivery. */
    data class RelayedMatch(val report: MatchReport, val messageId: String)

    init {
        connectivity?.let { flow ->
            scope.launch {
                flow.collect { online ->
                    val changed = online != selfOnlineNow
                    _selfOnline.value = online
                    // This device can now upload its own queued matches (Room +
                    // MatchSyncWorker); stop chasing mesh ACKs for them.
                    if (changed && online) {
                        pendingMatchAcks.clear()
                        publishStats()
                    }
                    if (changed && isRunning) broadcastHello()
                }
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch { loadPersisted() }
        beginAdvertising()
        beginDiscovery()
        logLine(EventKind.SYSTEM, "mesh started, online=$selfOnlineNow")
        scope.launch { watchdog() }
    }

    /**
     * Tear down and immediately re-arm advertising + discovery. The mesh screen's
     * "Rescan": Nearby can sit on a stale BLE scan after the volunteer walks
     * between crowds, and this is the same nudge [watchdog] applies on its own
     * once a device has been peerless for a while — just on demand.
     */
    fun rescan() {
        if (!isRunning) {
            start()
            return
        }
        client.stopAdvertising()
        client.stopDiscovery()
        beginAdvertising()
        beginDiscovery()
        logLine(EventKind.SYSTEM, "rescan requested")
    }

    /**
     * A cold-start failure here (e.g. a runtime permission grant that has not yet
     * propagated to Nearby, or a transient GMS error) used to be permanent: the
     * only call site was [start], so one failed attempt disabled that half of
     * discovery for the rest of the session with nothing but a log line. Retried
     * with backoff instead, and also re-armed by [watchdog] whenever this device
     * is running with zero peers.
     */
    private fun beginAdvertising() {
        if (!isRunning) return
        val advertising = AdvertisingOptions.Builder().setStrategy(strategy).build()
        client.startAdvertising(localName, Constants.MESH_SERVICE_ID, connectionCallback, advertising)
            .addOnFailureListener {
                Log.w(TAG, "advertising failed", it)
                logLine(EventKind.ERROR, "advertising failed: ${it.message}")
                scope.launch {
                    delay(RETRY_DELAY_MS)
                    beginAdvertising()
                }
            }
    }

    private fun beginDiscovery() {
        if (!isRunning) return
        val discovery = DiscoveryOptions.Builder().setStrategy(strategy).build()
        client.startDiscovery(Constants.MESH_SERVICE_ID, discoveryCallback, discovery)
            .addOnFailureListener {
                Log.w(TAG, "discovery failed", it)
                logLine(EventKind.ERROR, "discovery failed: ${it.message}")
                scope.launch {
                    delay(RETRY_DELAY_MS)
                    beginDiscovery()
                }
            }
    }

    /**
     * A connected peer can vanish for purely physical reasons (out of BT range,
     * interference) with no failure event to react to — Nearby just calls
     * [onDisconnected]. Without this, a device that lost its only peer stayed
     * silent for the rest of the session even though advertising/discovery may
     * simply need a nudge to find someone again.
     */
    private suspend fun watchdog() {
        while (isRunning) {
            delay(WATCHDOG_INTERVAL_MS)
            if (!isRunning) return
            if (connected.isEmpty()) {
                logLine(EventKind.SYSTEM, "no mesh peers; restarting advertise+discovery")
                client.stopAdvertising()
                client.stopDiscovery()
                beginAdvertising()
                beginDiscovery()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connected.clear()
        endpointNames.clear()
        onlinePeers.clear()
        pendingSends.clear()
        pendingMatchAcks.clear()
        publishPeers()
        publishStats()
        logLine(EventKind.SYSTEM, "mesh stopped")
    }

    /**
     * Broadcast an alert to the mesh. Skips alerts with no embedding yet (offline
     * peers can't match without it) and alerts this device has already sent.
     *
     * An alert is recorded as sent only once it is actually on the wire —
     * marking it up front (this is called for every alert on the first Firestore
     * snapshot, before Nearby has found anyone) would record every alert as sent
     * with nobody listening and it would never be offered again. Unsendable
     * alerts go to the [outbox] and are flushed when a peer connects.
     */
    fun broadcast(alert: Alert) {
        if (alert.embedding.isEmpty()) return
        if (isExpired(alert)) return
        if (alert.id in resolvedIds) return
        if (broadcasted.contains(ALERT_OUT + alert.id)) return

        val messageId = MeshPayloadCodec.newMessageId()
        seen.markIfNew(messageId) // never relay our own packet back
        val bytes = MeshPayloadCodec.encode(alert, messageId)
        if (!sendBytes(bytes, MeshRouter.broadcastTargets(connectedSnapshot(), exclude = null))) {
            synchronized(this) { outbox[alert.id] = alert }
            publishStats()
            return
        }
        broadcasted.markIfNew(ALERT_OUT + alert.id)
        logLine(EventKind.ALERT, "broadcast alert ${alert.id.take(8)} msg=${messageId.take(8)}")
    }

    /**
     * Drop a resolved alert and tell the mesh to do the same. Offline peers
     * cannot observe a case closing — the alert just stops appearing in a
     * Firestore query they cannot reach — so without this packet a volunteer
     * keeps scanning for a child who is already safe until the 8 h expiry.
     */
    fun forget(alertId: String) {
        resolvedIds.add(alertId)
        val changed = synchronized(this) {
            outbox.remove(alertId)
            known.remove(alertId) != null
        }
        if (changed) publish()
        scope.launch { store?.deleteAlert(alertId) }

        if (broadcasted.contains(RESOLVE_OUT + alertId)) return
        val messageId = MeshPayloadCodec.newMessageId()
        seen.markIfNew(messageId)
        seen.markIfNew(RESOLVE_KEY + alertId)
        scope.launch { store?.saveSeen(RESOLVE_KEY + alertId) }
        if (sendBytes(MeshPayloadCodec.encodeResolve(alertId, messageId), MeshRouter.broadcastTargets(connectedSnapshot(), null))) {
            broadcasted.markIfNew(RESOLVE_OUT + alertId)
            logLine(EventKind.RESOLVE, "resolve ${alertId.take(8)} sent")
        } else {
            synchronized(this) { resolveOutbox.add(alertId) }
            publishStats()
        }
    }

    /**
     * Relay a match report toward a connected online peer (or flood if none is),
     * then chase an ACK: re-send on a timer until an online peer confirms it
     * uploaded the sighting, this device comes online, or the attempt budget
     * runs out. The origin device's Room queue is the backstop the whole time.
     */
    fun relayMatch(report: MatchReport) {
        if (!isRunning) return // mesh is down; the Room queue + MatchSyncWorker own it
        val messageId = MeshPayloadCodec.newMessageId()
        seen.markIfNew(messageId)
        seen.markIfNew(matchKey(report))
        val bytes = MeshPayloadCodec.encode(report, messageId)
        val targets = MeshRouter.matchTargets(connectedSnapshot(), onlinePeersSnapshot(), exclude = null)
        sendBytes(bytes, targets)
        pendingMatchAcks[messageId] = PendingMatchAck(bytes, attempts = 0)
        publishStats()
        scheduleMatchAckRetry(messageId)
        logLine(
            EventKind.MATCH,
            "relay match ${report.alertId.take(8)} msg=${messageId.take(8)} -> ${targets.size} peer(s)",
        )
    }

    private fun scheduleMatchAckRetry(messageId: String) {
        scope.launch {
            delay(MATCH_ACK_TIMEOUT_MS)
            val pending = pendingMatchAcks[messageId] ?: return@launch // ACKed or cleared
            if (selfOnlineNow) {
                pendingMatchAcks.remove(messageId)
                publishStats()
                return@launch
            }
            if (pending.attempts >= MAX_MATCH_ACK_ATTEMPTS) {
                pendingMatchAcks.remove(messageId)
                publishStats()
                logLine(
                    EventKind.ERROR,
                    "match msg=${messageId.take(8)} unacked after $MAX_MATCH_ACK_ATTEMPTS tries; " +
                        "left to the Room queue",
                )
                return@launch
            }
            pendingMatchAcks[messageId] = pending.copy(attempts = pending.attempts + 1)
            val targets = MeshRouter.matchTargets(connectedSnapshot(), onlinePeersSnapshot(), exclude = null)
            sendBytes(pending.bytes, targets)
            logLine(
                EventKind.MATCH,
                "match msg=${messageId.take(8)} resend (attempt ${pending.attempts + 2}) -> ${targets.size} peer(s)",
            )
            scheduleMatchAckRetry(messageId)
        }
    }

    /** Called by the uploader bridge once a mesh-relayed match is safely in Firestore. */
    fun ackMatch(messageId: String) {
        if (!isRunning) return
        val ackId = MeshPayloadCodec.newMessageId()
        seen.markIfNew(ackId)
        sendBytes(MeshPayloadCodec.encodeAck(messageId, ackId), MeshRouter.broadcastTargets(connectedSnapshot(), null))
        logLine(EventKind.ACK, "ack match msg=${messageId.take(8)}")
    }

    /** Clear the on-screen activity log. Counters and peers are untouched. */
    fun clearEvents() {
        _events.value = emptyList()
    }

    // --- sending -------------------------------------------------------------

    /**
     * Send [bytes] to each of [targets] as its own payload, tracked for retry.
     * @return true if at least one target was addressed.
     */
    private fun sendBytes(bytes: ByteArray, targets: List<String>): Boolean {
        if (targets.isEmpty()) return false
        for (endpointId in targets) {
            val payload = Payload.fromBytes(bytes)
            pendingSends[payload.id] = PendingSend(bytes, endpointId, attempts = 0)
            client.sendPayload(endpointId, payload).addOnFailureListener {
                pendingSends.remove(payload.id)
                Log.w(TAG, "sendPayload to $endpointId failed", it)
                logLine(EventKind.ERROR, "send to ${short(endpointId)} rejected")
            }
        }
        _stats.update { it.copy(sent = it.sent + targets.size) }
        return true
    }

    private fun broadcastHello() {
        sendBytes(MeshPayloadCodec.encodeHello(selfOnlineNow), MeshRouter.broadcastTargets(connectedSnapshot(), null))
    }

    /** Re-offer everything that had no peer when it was first sent. */
    private fun flushOutbox() {
        val closures = synchronized(this) { resolveOutbox.toList() }
        for (alertId in closures) {
            if (sendBytes(MeshPayloadCodec.encodeResolve(alertId), MeshRouter.broadcastTargets(connectedSnapshot(), null))) {
                broadcasted.markIfNew(RESOLVE_OUT + alertId)
                synchronized(this) { resolveOutbox.remove(alertId) }
            }
        }

        val queued = synchronized(this) { outbox.values.toList() }
        if (queued.isEmpty()) {
            publishStats()
            return
        }
        Log.i(TAG, "flushing ${queued.size} queued alert(s) to the mesh")
        logLine(EventKind.SYSTEM, "flushing ${queued.size} queued alert(s)")
        for (alert in queued) {
            if (isExpired(alert) || alert.id in resolvedIds) {
                synchronized(this) { outbox.remove(alert.id) }
                continue
            }
            val messageId = MeshPayloadCodec.newMessageId()
            seen.markIfNew(messageId)
            if (sendBytes(MeshPayloadCodec.encode(alert, messageId), MeshRouter.broadcastTargets(connectedSnapshot(), null))) {
                broadcasted.markIfNew(ALERT_OUT + alert.id)
                synchronized(this) { outbox.remove(alert.id) }
            }
        }
        publishStats()
    }

    // --- Nearby callbacks ---------------------------------------------------

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            endpointNames[endpointId] = info.endpointName
            client.acceptConnection(endpointId, payloadCallback) // auto-accept trusted mesh
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (!resolution.status.isSuccess) {
                logLine(EventKind.PEER, "connection to ${short(endpointId)} failed")
                return
            }
            connected[endpointId] = PeerRecord(
                name = endpointNames[endpointId] ?: endpointId,
                connectedAtMillis = System.currentTimeMillis(),
            )
            publishPeers()
            logLine(EventKind.PEER, "peer connected ${peerLabel(endpointId)}, ${connected.size} total")
            // Tell the new peer whether we can act as an internet gateway.
            sendBytes(MeshPayloadCodec.encodeHello(selfOnlineNow), listOf(endpointId))
            // A peer at last: everything gathered while alone can go now.
            flushOutbox()
        }

        override fun onDisconnected(endpointId: String) {
            dropEndpoint(endpointId)
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            endpointNames[endpointId] = info.endpointName
            // Nearby re-surfaces an endpoint this device is already linked to on
            // every discovery cycle. Asking to connect again only returns
            // STATUS_ALREADY_CONNECTED_TO_ENDPOINT, so both the attempt and its
            // "failure" are noise — and logging that as an error buries the real
            // ones under a line every few seconds for a link that is perfectly up.
            if (connected.containsKey(endpointId)) return
            logLine(EventKind.PEER, "found ${peerLabel(endpointId)}")
            client.requestConnection(localName, endpointId, connectionCallback)
                .addOnFailureListener {
                    Log.w(TAG, "requestConnection failed", it)
                    val alreadyLinked = it.message?.contains("ALREADY_CONNECTED") == true
                    if (alreadyLinked) return@addOnFailureListener
                    logLine(EventKind.ERROR, "connect to ${short(endpointId)} failed: ${it.message}")
                }
        }

        override fun onEndpointLost(endpointId: String) {
            // This fires when the BLE advertisement is no longer seen, which is
            // not the same as the connection dying — Nearby commonly throttles or
            // stops advertising once a peer is paired, so an already-connected
            // endpoint routinely "gets lost" here while its KEEP_ALIVE traffic
            // keeps succeeding both ways. Treating this as a disconnect zeroed
            // the peer count (and the confirmed-match mesh relay with it) for a
            // link that was never actually down. Only real drops — reported by
            // ConnectionLifecycleCallback.onDisconnected — should count.
            if (!connected.containsKey(endpointId)) dropEndpoint(endpointId)
        }
    }

    private fun dropEndpoint(endpointId: String) {
        val was = connected.remove(endpointId)
        onlinePeers.remove(endpointId)
        // A vanished peer never reports SUCCESS/FAILURE for its in-flight
        // payloads; clear them so the retry map cannot leak over a long session.
        pendingSends.entries.removeAll { it.value.endpointId == endpointId }
        publishPeers()
        if (was != null) {
            logLine(EventKind.PEER, "peer disconnected ${peerLabel(endpointId)}, ${connected.size} total")
        }
        endpointNames.remove(endpointId)
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            _stats.update { it.copy(received = it.received + 1) }
            val envelope = MeshPayloadCodec.decode(bytes)
            if (envelope == null) {
                _stats.update { it.copy(dropped = it.dropped + 1) }
                Log.w(TAG, "dropped mesh payload: bad MAC or malformed")
                logLine(EventKind.ERROR, "dropped packet from ${short(endpointId)} (bad MAC / malformed)")
                return
            }
            when (val msg = envelope.message) {
                is MeshMessage.AlertMessage ->
                    handleAlert(msg.alert, bytes, envelope.ttl, envelope.messageId, endpointId)
                is MeshMessage.MatchMessage ->
                    handleMatch(msg.report, bytes, envelope.ttl, envelope.messageId, endpointId)
                is MeshMessage.ResolveMessage ->
                    handleResolve(msg.alertId, bytes, envelope.ttl, envelope.messageId, endpointId)
                is MeshMessage.HelloMessage -> {
                    if (msg.hasInternet) onlinePeers.add(endpointId) else onlinePeers.remove(endpointId)
                    publishPeers()
                    logLine(
                        EventKind.PEER,
                        "hello from ${peerLabel(endpointId)}, internet=${msg.hasInternet}",
                    )
                }
                is MeshMessage.AckMessage -> {
                    if (!seen.markIfNew(envelope.messageId)) return
                    if (pendingMatchAcks.remove(msg.ackFor) != null) {
                        publishStats()
                        logLine(EventKind.ACK, "match msg=${msg.ackFor.take(8)} delivery confirmed")
                    } else {
                        logLine(EventKind.ACK, "ack for msg=${msg.ackFor.take(8)} (passing through)")
                    }
                    relay(bytes, envelope.ttl, envelope.messageId, endpointId)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> pendingSends.remove(update.payloadId)
                PayloadTransferUpdate.Status.FAILURE -> {
                    val record = pendingSends.remove(update.payloadId)
                    Log.w(TAG, "transfer to $endpointId failed (payload ${update.payloadId})")
                    logLine(EventKind.ERROR, "transfer to ${short(endpointId)} FAILED")
                    if (record != null && record.attempts < MAX_SEND_ATTEMPTS - 1 &&
                        connected.containsKey(endpointId)
                    ) {
                        val retry = Payload.fromBytes(record.bytes)
                        pendingSends[retry.id] = record.copy(attempts = record.attempts + 1)
                        client.sendPayload(endpointId, retry).addOnFailureListener {
                            pendingSends.remove(retry.id)
                        }
                        logLine(
                            EventKind.RELAY,
                            "retrying transfer to ${short(endpointId)} (attempt ${record.attempts + 2})",
                        )
                    }
                }
                else -> Unit // IN_PROGRESS / CANCELED
            }
        }
    }

    private fun handleAlert(alert: Alert, raw: ByteArray, ttl: Int, messageId: String, from: String) {
        if (isExpired(alert)) return
        if (alert.id in resolvedIds) return
        if (!seen.markIfNew(messageId)) return
        scope.launch { store?.saveSeen(messageId) }
        synchronized(this) { known[alert.id] = alert }
        scope.launch { store?.saveAlert(alert.id, raw) }
        publish()
        logLine(
            EventKind.ALERT,
            "alert ${alert.id.take(8)} received ttl=$ttl from ${peerLabel(from)}" +
                if (alert.thumbnail != null) " (+thumb)" else "",
        )
        relay(raw, ttl, messageId, from)
    }

    private fun handleMatch(report: MatchReport, raw: ByteArray, ttl: Int, messageId: String, from: String) {
        // Only the device that can actually upload takes ownership of the
        // sighting. A middle offline relay that emitted it here would queue it in
        // Room too, so every hop between the volunteer and a gateway would
        // produce another duplicate match document once it came online. The
        // semantic key is still marked on every device so a re-issued packet is
        // not re-processed.
        val firstSighting = seen.markIfNew(matchKey(report))
        if (firstSighting && selfOnlineNow) _matches.tryEmit(RelayedMatch(report, messageId))
        if (!seen.markIfNew(messageId)) return
        // Persist the dedup keys so a restart cannot re-process (and, if online,
        // re-upload) a match packet that is still circulating.
        scope.launch {
            store?.saveSeen(messageId)
            store?.saveSeen(matchKey(report))
        }
        logLine(
            EventKind.MATCH,
            "match ${report.alertId.take(8)} received ttl=$ttl from ${peerLabel(from)}",
        )
        relay(raw, ttl, messageId, from)
    }

    private fun handleResolve(alertId: String, raw: ByteArray, ttl: Int, messageId: String, from: String) {
        if (!seen.markIfNew(messageId)) return
        seen.markIfNew(RESOLVE_KEY + alertId)
        scope.launch {
            store?.saveSeen(messageId)
            store?.saveSeen(RESOLVE_KEY + alertId)
            store?.deleteAlert(alertId)
        }
        resolvedIds.add(alertId)
        val changed = synchronized(this) {
            outbox.remove(alertId)
            known.remove(alertId) != null
        }
        if (changed) publish()
        logLine(EventKind.RESOLVE, "resolve ${alertId.take(8)} received from ${peerLabel(from)}")
        relay(raw, ttl, messageId, from)
    }

    /** Decrement the hop-count and re-broadcast; a packet at TTL <= 1 is the last hop. */
    private fun relay(raw: ByteArray, ttl: Int, messageId: String, from: String) {
        if (!MeshRouter.shouldRelay(ttl)) return
        val targets = MeshRouter.broadcastTargets(connectedSnapshot(), exclude = from)
        if (sendBytes(MeshPayloadCodec.withDecrementedTtl(raw), targets)) {
            _stats.update { it.copy(relayed = it.relayed + 1) }
            logLine(
                EventKind.RELAY,
                "relay msg=${messageId.take(8)} ttl=${ttl - 1} -> ${targets.size} peer(s)",
            )
        }
    }

    // --- persistence ------------------------------------------------------

    private suspend fun loadPersisted() {
        val loaded = runCatching { store?.load() }.getOrNull() ?: return
        seen.restore(loaded.seen)
        loaded.seen.keys
            .filter { it.startsWith(RESOLVE_KEY) }
            .forEach { resolvedIds.add(it.removePrefix(RESOLVE_KEY)) }

        var added = false
        for (packet in loaded.alertPackets) {
            val alert = (MeshPayloadCodec.decode(packet)?.message as? MeshMessage.AlertMessage)?.alert ?: continue
            if (isExpired(alert) || alert.id in resolvedIds) continue
            synchronized(this) { known[alert.id] = alert }
            added = true
        }
        if (added) publish()
        logLine(EventKind.SYSTEM, "restored ${known.size} alert(s) from disk")
    }

    // --- helpers --------------------------------------------------------

    private fun connectedSnapshot(): Set<String> = connected.keys.toSet()
    private fun onlinePeersSnapshot(): Set<String> = onlinePeers.toSet()

    private fun matchKey(report: MatchReport) = "match:${report.alertId}:${report.volunteerId}"

    /** Short endpoint id, for a log line that has to stay readable on a phone. */
    private fun short(endpointId: String) = endpointId.take(4)

    /** "Pixel 7 (A1B2)" — the device name a volunteer recognises, plus the id. */
    private fun peerLabel(endpointId: String): String {
        val name = connected[endpointId]?.name ?: endpointNames[endpointId]
        val pretty = name?.removePrefix("Rakshak-")
        return if (pretty.isNullOrBlank()) short(endpointId) else "$pretty (${short(endpointId)})"
    }

    private fun publishPeers() {
        val gateways = onlinePeersSnapshot()
        _peers.value = connected.entries
            .map { (endpointId, record) ->
                PeerInfo(
                    endpointId = endpointId,
                    name = record.name.removePrefix("Rakshak-").ifBlank { endpointId },
                    isGateway = endpointId in gateways,
                    connectedAtMillis = record.connectedAtMillis,
                )
            }
            .sortedBy { it.connectedAtMillis }
        _peerCount.value = connected.size
    }

    private fun publishStats() {
        val queued = synchronized(this) { outbox.size + resolveOutbox.size }
        _stats.update { it.copy(outbox = queued, pendingAcks = pendingMatchAcks.size) }
    }

    private fun publish() {
        _alerts.value = synchronized(this) {
            known.values.removeAll { isExpired(it) }
            known.values.toList()
        }
    }

    private fun isExpired(alert: Alert): Boolean =
        System.currentTimeMillis() - alert.timestamp > Constants.ALERT_EXPIRY_MILLIS

    private fun logLine(kind: EventKind, line: String) {
        Log.i(TAG, line)
        val event = MeshEvent(
            seq = eventSeq.incrementAndGet(),
            atMillis = System.currentTimeMillis(),
            kind = kind,
            text = line,
        )
        _events.update { (it + event).takeLast(LOG_CAP) }
    }

    companion object {
        private const val TAG = "MeshNetwork"
        private const val RESOLVE_KEY = "resolve:"
        private const val ALERT_OUT = "out-alert:"
        private const val RESOLVE_OUT = "out-resolve:"
        private const val LOG_CAP = 200
        private const val MAX_SEND_ATTEMPTS = 2
        private const val MATCH_ACK_TIMEOUT_MS = 15_000L
        private const val MAX_MATCH_ACK_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 5_000L
        private const val WATCHDOG_INTERVAL_MS = 15_000L
    }
}
