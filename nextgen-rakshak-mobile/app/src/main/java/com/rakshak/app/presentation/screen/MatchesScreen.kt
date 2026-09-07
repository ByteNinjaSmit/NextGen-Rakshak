package com.rakshak.app.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rakshak.app.data.model.MatchStatus
import com.rakshak.app.data.model.MatchStatusReport
import com.rakshak.app.presentation.theme.AlertRed
import com.rakshak.app.presentation.theme.PrimaryBlue
import com.rakshak.app.presentation.theme.SafeGreen
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Matches", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry queued reports")
                    }
                },
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

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                item { SummaryStrip(summary) }

                // Anything unsynced is called out above the list, not buried in
                // it. A volunteer who believes police were notified will stop
                // looking; if the report never left the phone, that belief is the
                // most dangerous thing on this screen.
                if (summary.queuedOffline > 0) {
                    item { OfflineBanner(summary.queuedOffline, onRetry = viewModel::refresh) }
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
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("No matches yet", fontWeight = FontWeight.SemiBold, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Open the scanner and point your camera at the crowd. " +
                    "Every sighting you confirm is listed here with what police did about it.",
                color = Color.Gray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Counts across every report, so the state of the volunteer's work is one glance. */
@Composable
private fun SummaryStrip(summary: MatchesSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryTile("Reported", summary.total, PrimaryBlue, Modifier.weight(1f))
        SummaryTile("Awaiting", summary.awaitingReview, Color(0xFFFFA000), Modifier.weight(1f))
        SummaryTile("Dispatched", summary.dispatched, PrimaryBlue, Modifier.weight(1f))
        SummaryTile("Accepted", summary.accepted, SafeGreen, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$value", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
            Text(label, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
        }
    }
}

@Composable
private fun OfflineBanner(count: Int, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA000).copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Color(0xFFFFA000))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$count report(s) not yet delivered",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    "They are saved on this phone and will send when you have signal. " +
                        "Police have not seen them yet.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = "Retry now", tint = Color(0xFFFFA000))
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

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (match.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = match.imageUrl,
                        contentDescription = "Face captured for this sighting",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color.LightGray),
                    )
                } else {
                    Box(
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        match.childName.ifBlank { "Unnamed child" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formattedTime, fontSize = 12.sp, color = Color.Gray)
                        if (match.confidence > 0f) {
                            Text(" · ", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                "${(match.confidence * 100).toInt()}% match",
                                fontSize = 12.sp,
                                color = if (match.confidence >= Constants.STRONG_MATCH_THRESHOLD) {
                                    SafeGreen
                                } else {
                                    PrimaryBlue
                                },
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(match.status)
                    if (match.pendingSync) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(
                            Icons.Filled.CloudOff,
                            contentDescription = "Not yet delivered",
                            tint = Color(0xFFFFA000),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
    val (label, color) = when (status) {
        MatchStatus.PENDING -> "Pending" to Color(0xFFFFA000)
        MatchStatus.DISPATCHED -> "Dispatched" to PrimaryBlue
        MatchStatus.ACCEPTED -> "Accepted" to SafeGreen
        MatchStatus.DISMISSED -> "Dismissed" to AlertRed
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
