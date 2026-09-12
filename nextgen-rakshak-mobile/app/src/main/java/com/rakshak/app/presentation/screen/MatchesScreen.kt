package com.rakshak.app.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rakshak.app.data.model.MatchStatus
import com.rakshak.app.data.model.MatchStatusReport
import com.rakshak.app.presentation.theme.RakshakExtras
import com.rakshak.app.presentation.theme.Spacing
import com.rakshak.app.presentation.theme.WindowWidthClass
import com.rakshak.app.presentation.theme.rememberWindowInfo
import com.rakshak.app.presentation.viewmodel.MatchesSummary
import com.rakshak.app.presentation.viewmodel.MatchesViewModel
import com.rakshak.app.utils.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The volunteer's record of every sighting they have reported.
 *
 * Built around one question a volunteer actually asks after handing over a
 * child: *did that get through, and what happened to it?* So the screen leads
 * with a status summary, flags anything still stuck on the phone before anything
 * else, and lets a row open up to show the evidence the report was made on — the
 * score, the time, the coordinates — rather than only a name and a badge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(viewModel: MatchesViewModel) {
    val myMatches by viewModel.myMatches.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val windowInfo = rememberWindowInfo()
    // Two columns once there is width to spare (a phone in landscape, or a
    // tablet): a single column of fixed-width cards would otherwise waste the
    // whole second half of the screen.
    val columns = if (windowInfo.isLandscape && windowInfo.widthClass != WindowWidthClass.COMPACT) 2 else 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("My Matches", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry queued reports")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (syncing && myMatches.isEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (myMatches.isEmpty() && !syncing) {
                EmptyState()
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(vertical = Spacing.lg),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    SummaryStrip(summary)
                }

                // Anything unsynced is called out above the list, not buried in
                // it. A volunteer who believes police were notified will stop
                // looking; if the report never left the phone, that belief is the
                // most dangerous thing on this screen.
                if (summary.queuedOffline > 0) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        OfflineBanner(summary.queuedOffline, onRetry = viewModel::refresh)
                    }
                }

                items(myMatches, key = { it.id }) { match -> MatchCard(match) }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xxl),
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text("No matches yet", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                "Open the scanner and point your camera at the crowd. " +
                    "Every sighting you confirm is listed here with what police did about it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Counts across every report, so the state of the volunteer's work is one glance. */
@Composable
private fun SummaryStrip(summary: MatchesSummary) {
    val extras = RakshakExtras.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SummaryTile("Reported", summary.total, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        SummaryTile("Awaiting", summary.awaitingReview, extras.warning, Modifier.weight(1f))
        SummaryTile("Dispatched", summary.dispatched, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        SummaryTile("Accepted", summary.accepted, extras.success, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$value",
                style = MaterialTheme.typography.titleLarge,
                color = color,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OfflineBanner(count: Int, onRetry: () -> Unit) {
    val warning = RakshakExtras.current.warning
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = RakshakExtras.current.warningContainer),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = warning)
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$count report(s) not yet delivered",
                    style = MaterialTheme.typography.titleSmall,
                    color = RakshakExtras.current.onWarningContainer,
                )
                Text(
                    "They are saved on this phone and will send when you have signal. " +
                        "Police have not seen them yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RakshakExtras.current.onWarningContainer,
                )
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = "Retry now", tint = warning)
            }
        }
    }
}

/**
 * One reported sighting. Collapsed it answers "who, when, what happened";
 * expanded it shows the evidence the report was made on, which is what a
 * volunteer needs if an officer later asks them about it.
 */
@Composable
private fun MatchCard(match: MatchStatusReport) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()) }
    val formattedTime =
        if (match.timestampMillis > 0) timeFormat.format(Date(match.timestampMillis)) else "—"
    val success = RakshakExtras.current.success

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.xxs),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (match.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = match.imageUrl,
                        contentDescription = "Face captured for this sighting",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                } else {
                    Box(
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        match.childName.ifBlank { "Unnamed child" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formattedTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (match.confidence > 0f) {
                            Text(
                                " · ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${(match.confidence * 100).toInt()}% match",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (match.confidence >= Constants.STRONG_MATCH_THRESHOLD) {
                                    success
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(match.status)
                    if (match.pendingSync) {
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Icon(
                            Icons.Filled.CloudOff,
                            contentDescription = "Not yet delivered",
                            tint = RakshakExtras.current.warning,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = Spacing.md)) {
                    EvidenceRow(
                        icon = if (match.hasLocation) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                        label = "Where you saw them",
                        value = if (match.hasLocation) {
                            formatCoordinates(match.latitude, match.longitude)
                        } else {
                            // Saying "no GPS fix" is not a detail: an officer
                            // reading this report has no pin to walk to, and the
                            // volunteer is the only one who knows where they were.
                            "No GPS fix — tell the officer where you were"
                        },
                    )
                    EvidenceRow(
                        icon = Icons.Filled.Person,
                        label = "Similarity at confirmation",
                        value = if (match.confidence > 0f) {
                            "${(match.confidence * 100).toInt()}% " +
                                "(threshold ${(Constants.SIMILARITY_THRESHOLD * 100).toInt()}%)"
                        } else {
                            "not recorded"
                        },
                    )
                    EvidenceRow(
                        icon = Icons.Filled.Refresh,
                        label = "Kiosk status",
                        value = statusExplanation(match.status, match.pendingSync),
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidenceRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Plain-language meaning of the kiosk state, not just its name. */
private fun statusExplanation(status: MatchStatus, pendingSync: Boolean): String = when {
    pendingSync -> "Saved on this phone; not delivered to police yet"
    status == MatchStatus.PENDING -> "Delivered — waiting for an officer to review it"
    status == MatchStatus.DISPATCHED -> "An officer is on the way"
    status == MatchStatus.ACCEPTED -> "Confirmed by police as the right child"
    else -> "Reviewed and ruled out by police"
}

/** Degrees-with-hemisphere, which is readable aloud to an officer over a radio. */
private fun formatCoordinates(lat: Double, lng: Double): String {
    val ns = if (lat >= 0) "N" else "S"
    val ew = if (lng >= 0) "E" else "W"
    return String.format(Locale.US, "%.5f°%s, %.5f°%s", abs(lat), ns, abs(lng), ew)
}

@Composable
private fun StatusBadge(status: MatchStatus) {
    val extras = RakshakExtras.current
    val (label, color) = when (status) {
        MatchStatus.PENDING -> "Pending" to extras.warning
        MatchStatus.DISPATCHED -> "Dispatched" to MaterialTheme.colorScheme.tertiary
        MatchStatus.ACCEPTED -> "Accepted" to extras.success
        MatchStatus.DISMISSED -> "Dismissed" to MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}
