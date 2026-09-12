package com.rakshak.app.presentation.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rakshak.app.networking.mesh.MeshNetworkManager
import com.rakshak.app.networking.mesh.MeshService
import com.rakshak.app.presentation.theme.PillShape
import com.rakshak.app.presentation.theme.RakshakExtras
import com.rakshak.app.presentation.theme.Spacing
import com.rakshak.app.presentation.theme.rememberWindowInfo
import com.rakshak.app.utils.Constants
import com.rakshak.app.utils.ElapsedTime
import com.rakshak.app.utils.LocationSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The volunteer's window onto the offline mesh.
 *
 * The mesh is the one subsystem with no other visible output: it either relays a
 * sighting device-to-device or it silently does not, and a volunteer standing in
 * a dead spot has no way to tell which. So this screen is built to answer three
 * questions without reading a log — is the mesh up, who am I linked to, and is
 * anything actually moving — and only then offers the packet trace that the
 * multi-device field trial (VER-08) needs for hop timing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshNetworkScreen(mesh: MeshNetworkManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val windowInfo = rememberWindowInfo()

    val running by mesh.running.collectAsStateWithLifecycle()
    val peers by mesh.peers.collectAsStateWithLifecycle()
    val stats by mesh.stats.collectAsStateWithLifecycle()
    val events by mesh.events.collectAsStateWithLifecycle()
    val alerts by mesh.alerts.collectAsStateWithLifecycle()
    val selfOnline by mesh.selfOnline.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(LogFilter.ALL) }

    // Re-checked whenever the screen is recomposed (e.g. returning from settings).
    var locationOn by remember { mutableStateOf(LocationSettings.enabled(context)) }
    LaunchedEffect(Unit) { locationOn = LocationSettings.enabled(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mesh Network", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { mesh.rescan() }) {
                        Icon(Icons.Filled.Radar, contentDescription = "Rescan for peers")
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(events.joinToString("\n") { it.text }))
                        },
                        enabled = events.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy activity log")
                    }
                    IconButton(onClick = { mesh.clearEvents() }, enabled = events.isNotEmpty()) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear activity log")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        val summary: @Composable () -> Unit = {
            Column {
                if (!locationOn) {
                    LocationOffBanner(
                        onTurnOn = {
                            context.startActivity(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                }
                MeshHeroCard(
                    running = running,
                    peers = peers,
                    selfOnline = selfOnline,
                    onToggle = { on ->
                        if (on) MeshService.start(context) else MeshService.stop(context)
                    },
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                StatsBlock(stats = stats, alertCount = alerts.size)
                Spacer(modifier = Modifier.height(Spacing.md))
                PeersBlock(peers = peers, running = running, onRescan = { mesh.rescan() })
            }
        }

        if (windowInfo.isLandscape) {
            Row(modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg)) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                ) { summary() }
                Spacer(modifier = Modifier.width(Spacing.lg))
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ActivityBlock(
                        events = events,
                        filter = filter,
                        onFilter = { filter = it },
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState()),
            ) {
                summary()
                Spacer(modifier = Modifier.height(Spacing.md))
                ActivityBlock(
                    events = events,
                    filter = filter,
                    onFilter = { filter = it },
                    modifier = Modifier.height(320.dp),
                )
            }
        }
    }
}

@Composable
private fun LocationOffBanner(onTurnOn: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            Text(
                "Location is off — the mesh may not find nearby volunteers even with " +
                    "permission granted.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onTurnOn) { Text("Turn on") }
        }
    }
}

/**
 * The answer to "is the mesh up, and who is on it" in one glance: a radar whose
 * sweep only turns while the mesh is actually running, a dot per linked peer, and
 * the switch that starts or stops the relay service.
 */
@Composable
private fun MeshHeroCard(
    running: Boolean,
    peers: List<MeshNetworkManager.PeerInfo>,
    selfOnline: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val gateways = peers.count { it.isGateway }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.xxs),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MeshRadar(peerCount = peers.size, active = running)

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                when {
                    !running -> "Mesh is off"
                    peers.isEmpty() -> "Searching for nearby volunteers…"
                    peers.size == 1 -> "Linked to 1 nearby volunteer"
                    else -> "Linked to ${peers.size} nearby volunteers"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (running) {
                    "Packets relay device-to-device with no internet, up to " +
                        "${Constants.MESH_INITIAL_TTL} hops."
                } else {
                    "Turn the mesh on to relay alerts and sightings without internet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusPill(
                    icon = if (selfOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    text = if (selfOnline) "This phone is a gateway" else "No internet here",
                    tint = if (selfOnline) RakshakExtras.current.success else RakshakExtras.current.warning,
                )
                if (gateways > 0) {
                    StatusPill(
                        icon = Icons.Filled.Router,
                        text = if (gateways == 1) "1 peer gateway" else "$gateways peer gateways",
                        tint = RakshakExtras.current.success,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Relay service",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = running, onCheckedChange = onToggle)
            }
        }
    }
}

/**
 * Concentric rings with this device at the centre and one dot per linked peer.
 *
 * The sweep is animated only while the mesh is running, which makes "off" and
 * "on but alone" visibly different states — the peer count alone reads as 0 in
 * both cases.
 */
@Composable
private fun MeshRadar(peerCount: Int, active: Boolean) {
    val ringColor = MaterialTheme.colorScheme.outlineVariant
    val sweepColor = MaterialTheme.colorScheme.primary
    val selfColor = MaterialTheme.colorScheme.primary
    val peerColor = RakshakExtras.current.success
    val idleColor = MaterialTheme.colorScheme.outline

    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Canvas(modifier = Modifier.size(168.dp)) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f - 6f

        for (step in 1..3) {
            drawCircle(
                color = ringColor,
                radius = maxRadius * step / 3f,
                center = centre,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
            )
        }

        if (active) {
            // Expanding ping, fading as it grows.
            drawCircle(
                color = sweepColor.copy(alpha = (1f - pulse) * 0.35f),
                radius = maxRadius * pulse,
                center = centre,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )
            val radians = (sweep - 90f) * PI.toFloat() / 180f
            drawLine(
                color = sweepColor.copy(alpha = 0.7f),
                start = centre,
                end = Offset(
                    centre.x + cos(radians) * maxRadius,
                    centre.y + sin(radians) * maxRadius,
                ),
                strokeWidth = 2f,
            )
        }

        // One dot per peer, spread evenly around the middle ring.
        if (peerCount > 0) {
            val ring = maxRadius * 0.68f
            repeat(peerCount) { index ->
                val angle = (index * 360f / peerCount - 90f) * PI.toFloat() / 180f
                val position = Offset(centre.x + cos(angle) * ring, centre.y + sin(angle) * ring)
                drawCircle(color = peerColor.copy(alpha = 0.25f), radius = 14f, center = position)
                drawCircle(color = peerColor, radius = 7f, center = position)
            }
        }

        drawCircle(
            color = if (active) selfColor else idleColor,
            radius = 10f,
            center = centre,
        )
    }
}

@Composable
private fun StatusPill(icon: ImageVector, text: String, tint: Color) {
    Row(
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), PillShape)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Packet counters. What distinguishes "linked but idle" from "actually relaying". */
@Composable
private fun StatsBlock(stats: MeshNetworkManager.MeshStats, alertCount: Int) {
    Column {
        SectionHeader("Traffic")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(Icons.Filled.Upload, "Sent", stats.sent.toString(), Modifier.weight(1f))
            StatTile(Icons.Filled.Download, "Received", stats.received.toString(), Modifier.weight(1f))
            StatTile(Icons.Filled.Router, "Relayed", stats.relayed.toString(), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(
                Icons.Filled.NotificationsActive,
                "Alerts held",
                alertCount.toString(),
                Modifier.weight(1f),
            )
            StatTile(Icons.Filled.Outbox, "Queued", stats.outbox.toString(), Modifier.weight(1f))
            StatTile(
                Icons.Filled.WarningAmber,
                "Dropped",
                stats.dropped.toString(),
                Modifier.weight(1f),
                emphasis = stats.dropped > 0,
            )
        }
        if (stats.pendingAcks > 0) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                "${stats.pendingAcks} sighting(s) waiting for a gateway to confirm upload.",
                style = MaterialTheme.typography.bodySmall,
                color = RakshakExtras.current.warning,
            )
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    val accent = if (emphasis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Who this device is actually linked to, by device name rather than endpoint id. */
@Composable
private fun PeersBlock(
    peers: List<MeshNetworkManager.PeerInfo>,
    running: Boolean,
    onRescan: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Connected volunteers", modifier = Modifier.weight(1f))
            TextButton(onClick = onRescan) { Text("Rescan") }
        }
        if (peers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Text(
                    if (running) {
                        "No peers yet. Both phones need the app open with Bluetooth, " +
                            "Wi-Fi and Location on, within about 30 m."
                    } else {
                        "The mesh is off, so no peers can be found."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        } else {
            peers.forEach { peer ->
                PeerRow(peer)
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
        }
    }
}

@Composable
private fun PeerRow(peer: MeshNetworkManager.PeerInfo) {
    val success = RakshakExtras.current.success
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(success.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = success,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    peer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${peer.endpointId} · linked ${ElapsedTime.since(peer.connectedAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (peer.isGateway) {
                StatusPill(Icons.Filled.CloudDone, "Gateway", success)
            }
        }
    }
}

/** Filters over the rolling log. Grouped by what a reader is looking for, not one chip per kind. */
private enum class LogFilter(val label: String, val kinds: Set<MeshNetworkManager.EventKind>?) {
    ALL("All", null),
    PEERS("Peers", setOf(MeshNetworkManager.EventKind.PEER, MeshNetworkManager.EventKind.SYSTEM)),
    ALERTS(
        "Alerts",
        setOf(MeshNetworkManager.EventKind.ALERT, MeshNetworkManager.EventKind.RESOLVE),
    ),
    MATCHES(
        "Sightings",
        setOf(MeshNetworkManager.EventKind.MATCH, MeshNetworkManager.EventKind.ACK),
    ),
    RELAY("Relay", setOf(MeshNetworkManager.EventKind.RELAY)),
    ERRORS("Problems", setOf(MeshNetworkManager.EventKind.ERROR)),
}

@Composable
private fun ActivityBlock(
    events: List<MeshNetworkManager.MeshEvent>,
    filter: LogFilter,
    onFilter: (LogFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = remember(events, filter) {
        events.filter { filter.kinds == null || it.kind in filter.kinds }
    }
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(visible.size, autoScroll) {
        if (autoScroll && visible.isNotEmpty()) listState.animateScrollToItem(visible.lastIndex)
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Activity", modifier = Modifier.weight(1f))
            TextButton(onClick = { autoScroll = !autoScroll }) {
                Text(if (autoScroll) "Following" else "Paused")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            LogFilter.entries.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { onFilter(option) },
                    shape = PillShape,
                    label = { Text(option.label) },
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            shape = MaterialTheme.shapes.small,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
            if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (events.isEmpty()) "No mesh activity yet." else "Nothing under this filter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(Spacing.sm),
                ) {
                    items(visible, key = { it.seq }) { event ->
                        LogRow(event, clock.format(Date(event.atMillis)))
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(event: MeshNetworkManager.MeshEvent, timestamp: String) {
    val extras = RakshakExtras.current
    val tint = when (event.kind) {
        MeshNetworkManager.EventKind.ERROR -> MaterialTheme.colorScheme.error
        MeshNetworkManager.EventKind.MATCH, MeshNetworkManager.EventKind.ACK -> extras.success
        MeshNetworkManager.EventKind.ALERT -> extras.warning
        MeshNetworkManager.EventKind.PEER -> MaterialTheme.colorScheme.primary
        MeshNetworkManager.EventKind.RESOLVE -> MaterialTheme.colorScheme.secondary
        MeshNetworkManager.EventKind.RELAY, MeshNetworkManager.EventKind.SYSTEM ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
        Text(
            timestamp,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .background(tint, CircleShape),
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            event.text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = Spacing.xs),
    )
}
