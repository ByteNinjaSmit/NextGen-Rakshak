package com.rakshak.app.presentation.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.rakshak.app.data.model.Alert
import com.rakshak.app.presentation.theme.RakshakExtras
import com.rakshak.app.presentation.theme.Spacing
import com.rakshak.app.presentation.theme.WindowWidthClass
import com.rakshak.app.presentation.theme.rememberWindowInfo
import com.rakshak.app.presentation.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onStartScan: () -> Unit) {
    val alerts by viewModel.activeAlerts.collectAsStateWithLifecycle()
    var selectedAlert by remember { mutableStateOf<Alert?>(null) }
    val windowInfo = rememberWindowInfo()
    // A wide landscape window (phone rotated, or a tablet) earns a true
    // master-detail layout: the list never disappears behind the detail, so
    // picking a different alert is one tap instead of a trip back.
    val useTwoPane = windowInfo.isLandscape && windowInfo.widthClass != WindowWidthClass.COMPACT

    if (useTwoPane) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AlertListScreen(
                    alerts = alerts,
                    selectedId = selectedAlert?.id,
                    onSelect = { selectedAlert = it },
                )
            }
            androidx.compose.material3.VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.weight(1.3f).fillMaxHeight()) {
                if (selectedAlert != null) {
                    AlertDetailsScreen(
                        alert = selectedAlert!!,
                        onBack = { selectedAlert = null },
                        onStartScan = onStartScan,
                        showBack = false,
                    )
                } else {
                    EmptyDetailPane()
                }
            }
        }
        return
    }

    if (selectedAlert != null) {
        AlertDetailsScreen(
            alert = selectedAlert!!,
            onBack = { selectedAlert = null },
            onStartScan = onStartScan,
            showBack = true,
        )
    } else {
        AlertListScreen(alerts = alerts, selectedId = null, onSelect = { selectedAlert = it })
    }
}

@Composable
private fun EmptyDetailPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                "Select an alert to see details",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertListScreen(
    alerts: List<Alert>,
    selectedId: String?,
    onSelect: (Alert) -> Unit,
) {
    val success = RakshakExtras.current.success
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md)
                    .background(success.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = success)
                Spacer(modifier = Modifier.width(Spacing.md))
                Column {
                    Text("You are Active", style = MaterialTheme.typography.titleMedium, color = success)
                    Text(
                        "Ready to help",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Active Alerts",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = Spacing.sm)
            )

            if (alerts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No active alerts right now.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(alerts, key = { it.id }) { alert ->
                        AlertRow(alert, selected = alert.id == selectedId, onClick = { onSelect(alert) })
                    }
                    item { Spacer(modifier = Modifier.height(Spacing.lg)) }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: Alert, selected: Boolean, onClick: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = if (alert.timestamp > 0) timeFormat.format(Date(alert.timestamp)) else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            // primaryContainer is deliberately a vivid, full-strength brand red
            // in dark mode (it doubles as an alert accent elsewhere) — using it
            // solid here for "this row is selected" reads as an error state, not
            // a selection. A soft tint says the same thing without the alarm.
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) Spacing.xxs else 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlertThumbnail(alert.imageUrl, size = 64.dp)
            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text("${alert.childName}, ${alert.age} yrs", style = MaterialTheme.typography.titleMedium)
                Text(
                    alert.clothingDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alert.lastSeen.isNotBlank()) {
                    Text(
                        alert.lastSeen,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    "NEW",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun AlertThumbnail(imageUrl: String, size: androidx.compose.ui.unit.Dp) {
    if (imageUrl.isNotBlank()) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = "Child Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            error = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailsScreen(
    alert: Alert,
    onBack: () -> Unit,
    onStartScan: () -> Unit,
    showBack: Boolean = true,
) {
    val windowInfo = rememberWindowInfo()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Alert Details", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        val text = "MISSING: ${alert.childName}, ${alert.age} yrs. " +
                            "Last seen: ${alert.lastSeen}. Wearing: ${alert.clothingDesc}. " +
                            "If seen, contact ${alert.parentContact.ifBlank { "the police" }}."
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share alert"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { padding ->
        // Start Scanning must never scroll out of reach — a landscape phone
        // (roughly a third less height) previously overflowed straight past it
        // with no way to scroll down to it at all. The fix in both
        // orientations is the same shape: everything above the button lives in
        // a scrollable region with weight(1f); the button is a fixed sibling
        // after it, never inside the scroll.
        if (windowInfo.isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            ) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AlertPhoto(alert.imageUrl, size = 160.dp)
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text("${alert.childName}, ${alert.age} Years", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(modifier = Modifier.width(Spacing.xl))
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    ) {
                        AlertDetailRows(alert)
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                    StartScanningButton(onStartScan)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg)) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AlertPhoto(alert.imageUrl, size = 120.dp)
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    Text("${alert.childName}, ${alert.age} Years", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(Spacing.xxl))
                    AlertDetailRows(alert)
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                StartScanningButton(onStartScan)
            }
        }
    }
}

@Composable
private fun AlertPhoto(imageUrl: String, size: androidx.compose.ui.unit.Dp) {
    if (imageUrl.isNotBlank()) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = "Child Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            error = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(size / 2),
                    )
                }
            },
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

@Composable
private fun AlertDetailRows(alert: Alert) {
    val dateFormat = remember { SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault()) }
    val formattedTime = if (alert.timestamp > 0) dateFormat.format(Date(alert.timestamp)) else "Unknown"

    Column(modifier = Modifier.fillMaxWidth()) {
        DetailRow("Clothing", alert.clothingDesc)
        DetailRow("Last Seen", alert.lastSeen)
        DetailRow("Time", formattedTime)
        DetailRow("Gender", alert.gender.replaceFirstChar { it.uppercase() })
        DetailRow("Additional Info", alert.parentContact.ifEmpty { "No additional info." })
    }
}

/**
 * Always a fixed footer, never inside the scrollable detail region above it —
 * a landscape phone has enough less height that the button used to be pushed
 * clean off the bottom of the screen with no way to scroll down to it.
 */
@Composable
private fun StartScanningButton(onStartScan: () -> Unit) {
    Button(
        onClick = onStartScan,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text("Start Scanning", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(120.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value.ifBlank { "-" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
