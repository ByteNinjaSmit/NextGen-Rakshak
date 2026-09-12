package com.rakshak.app.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.presentation.theme.PillShape
import com.rakshak.app.presentation.theme.RakshakExtras
import com.rakshak.app.presentation.theme.Spacing
import com.rakshak.app.presentation.theme.rememberWindowInfo
import com.rakshak.app.presentation.viewmodel.LoginViewModel
import com.rakshak.app.utils.AvatarUrl

private enum class ProfileDialog { NONE, PERSONAL_INFO, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    volunteer: Volunteer?,
    sim: LoginViewModel.SimState,
    noSimWarning: Boolean = false,
    onSelectSim: (Int) -> Unit = {},
    onSavePhone: (String) -> Unit = {},
    onUseSimNumber: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onSignOut: () -> Unit,
    onOpenMesh: () -> Unit = {},
) {
    var openDialog by remember { mutableStateOf(ProfileDialog.NONE) }
    val windowInfo = rememberWindowInfo()
    val snackbarHost = remember { SnackbarHostState() }

    // The SIM sync runs on its own (app open, screen open), so its outcome has
    // to announce itself rather than wait to be noticed.
    LaunchedEffect(sim.message) {
        val message = sim.message ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Profile", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        // Both branches scroll: a landscape phone has noticeably less height,
        // and the identity block (avatar + name + role + badge) plus a
        // four-item menu can exceed it — with no scroll that content was
        // simply clipped, Logout included.
        if (windowInfo.isLandscape) {
            Row(modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg)) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(Spacing.xl))
                    if (noSimWarning) {
                        NoSimBanner()
                        Spacer(modifier = Modifier.height(Spacing.lg))
                    }
                    IdentityBlock(volunteer)
                }
                Spacer(modifier = Modifier.width(Spacing.xl))
                Column(
                    modifier = Modifier.weight(1.4f).fillMaxHeight().verticalScroll(rememberScrollState()),
                ) {
                    Spacer(modifier = Modifier.height(Spacing.xl))
                    ContactCard(sim, onSelectSim, onSavePhone, onUseSimNumber, onSyncNow)
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    MenuCard(onOpenMesh, openDialog = { openDialog = it }, onSignOut = onSignOut)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (noSimWarning) {
                    NoSimBanner()
                    Spacer(modifier = Modifier.height(Spacing.lg))
                }
                IdentityBlock(volunteer)
                Spacer(modifier = Modifier.height(Spacing.xl))
                ContactCard(sim, onSelectSim, onSavePhone, onUseSimNumber, onSyncNow)
                Spacer(modifier = Modifier.height(Spacing.lg))
                MenuCard(onOpenMesh, openDialog = { openDialog = it }, onSignOut = onSignOut)
            }
        }
    }

    when (openDialog) {
        ProfileDialog.PERSONAL_INFO -> PersonalInfoDialog(
            volunteer = volunteer,
            sim = sim,
            onDismiss = { openDialog = ProfileDialog.NONE },
        )

        ProfileDialog.ABOUT -> AlertDialog(
            onDismissRequest = { openDialog = ProfileDialog.NONE },
            title = { Text("About Rakshak") },
            text = {
                Text(
                    "Rakshak helps volunteers spot missing children by scanning faces " +
                        "against active alerts issued by police and reporting sightings " +
                        "in real time, with offline mesh relay as a fallback."
                )
            },
            confirmButton = {
                TextButton(onClick = { openDialog = ProfileDialog.NONE }) { Text("Close") }
            }
        )

        ProfileDialog.NONE -> Unit
    }
}

/**
 * Checked on every app open (see LoginViewModel.syncSim): a volunteer whose SIM
 * is out, or was never readable, needs to know an officer cannot reach them by
 * the number on file — silently leaving the phone field blank would hide that.
 */
@Composable
private fun NoSimBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "No SIM detected on this phone — an officer may not be able to call " +
                "you back about a sighting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun IdentityBlock(volunteer: Volunteer?) {
    val success = RakshakExtras.current.success
    Box(contentAlignment = Alignment.BottomEnd) {
        Avatar(volunteer?.photoUrl.orEmpty(), size = 100.dp)
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Verified",
            tint = success,
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.background, CircleShape),
        )
    }

    Spacer(modifier = Modifier.height(Spacing.lg))

    Text(
        volunteer?.name?.ifBlank { "Volunteer" } ?: "Volunteer",
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        volunteer?.role?.replaceFirstChar { it.uppercase() } ?: "Unit",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(Spacing.sm))

    Box(
        modifier = Modifier
            .border(1.dp, success, RoundedCornerShape(16.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
    ) {
        Text("Verified", color = success, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The volunteer's Google account picture, which is also what the officer sees
 * beside a reported sighting. Falls back to the person glyph on the email/password
 * path and while the image loads, so the circle is never empty.
 */
@Composable
private fun Avatar(photoUrl: String, size: androidx.compose.ui.unit.Dp) {
    val fallback: @Composable () -> Unit = {
        Icon(
            Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.64f),
        )
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isBlank()) {
            fallback()
        } else {
            AsyncImage(
                // Google serves these at 96 px by default, which is soft in a
                // 100 dp circle; ask for the size actually being drawn.
                model = AvatarUrl.sized(photoUrl, px = 256),
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The contact number an officer calls back on, and the SIM it came from.
 *
 * Interactive rather than read-only because the automatic path cannot always
 * win: on a dual-SIM phone the app has to guess which line is the right one, and
 * on the many carriers that never expose an MSISDN it cannot read any number at
 * all. Both cases are recoverable here without leaving the screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactCard(
    sim: LoginViewModel.SimState,
    onSelectSim: (Int) -> Unit,
    onSavePhone: (String) -> Unit,
    onUseSimNumber: () -> Unit,
    onSyncNow: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.xxs),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Text(
                    "Contact number",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                SyncBadge(sim)
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                sim.phone.ifBlank { "Not set" },
                style = MaterialTheme.typography.headlineSmall,
                color = if (sim.phone.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                when {
                    sim.phoneIsManual -> "Entered manually — the SIM will not overwrite it."
                    sim.selectedCard != null -> "From ${sim.selectedCard?.displayName}"
                    !sim.permissionGranted -> "Phone permission denied — the SIM cannot be read."
                    !sim.simPresent -> "No SIM detected."
                    else -> "Re-checked every time the app opens."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Only a dual-SIM phone needs a chooser; a single SIM is named above.
            if (sim.cards.size > 1) {
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    "Which SIM should officers call?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    sim.cards.forEach { card ->
                        FilterChip(
                            selected = card.subscriptionId == sim.selectedSubscriptionId,
                            onClick = { onSelectSim(card.subscriptionId) },
                            shape = PillShape,
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.SimCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(card.displayName + if (card.number == null) " (no number)" else "")
                            },
                        )
                    }
                }
            }

            // A number the carrier withholds is the common case, not an edge one,
            // so the manual field is offered the moment there is nothing to show.
            val needsManualEntry = sim.phone.isBlank() || sim.numberUnreadable
            if (needsManualEntry || sim.phoneIsManual) {
                Spacer(modifier = Modifier.height(Spacing.md))
                ManualPhoneField(current = sim.phone, onSave = onSavePhone)
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSyncNow, enabled = !sim.syncing) { Text("Sync now") }
                if (sim.phoneIsManual && sim.cards.any { it.number != null }) {
                    TextButton(onClick = onUseSimNumber) { Text("Use SIM number") }
                }
                Spacer(modifier = Modifier.weight(1f))
                AnimatedVisibility(visible = sim.syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun SyncBadge(sim: LoginViewModel.SimState) {
    val success = RakshakExtras.current.success
    val warning = RakshakExtras.current.warning
    val synced = sim.syncedToCloud && sim.phone.isNotBlank()
    val icon = if (synced) Icons.Filled.CloudDone else Icons.Filled.CloudOff
    val tint = if (synced) success else warning
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            if (synced) "Synced" else "Not synced",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

@Composable
private fun ManualPhoneField(current: String, onSave: (String) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    Column {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { ch -> ch.isDigit() || ch == '+' } },
            label = { Text("Enter your number") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.isNotBlank() && draft != current,
            ) { Text("Save number") }
        }
    }
}

/**
 * Everything the app knows about this volunteer, in one place: who they are to
 * Google, which line reaches them, and whether the control room has it. Shown on
 * the "Personal Information" row rather than kept in a settings screen, because
 * these are the fields a volunteer is asked to confirm at a deployment briefing.
 */
@Composable
private fun PersonalInfoDialog(
    volunteer: Volunteer?,
    sim: LoginViewModel.SimState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personal Information") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(volunteer?.photoUrl.orEmpty(), size = 56.dp)
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column {
                        Text(
                            volunteer?.name?.ifBlank { "Volunteer" } ?: "Volunteer",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            volunteer?.email?.ifBlank { "No email on record" } ?: "No email on record",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(Spacing.sm))

                InfoRow(Icons.Filled.Person, "Name", volunteer?.name?.ifBlank { "—" } ?: "—")
                InfoRow(
                    Icons.Filled.Badge,
                    "Role",
                    volunteer?.role?.replaceFirstChar { it.uppercase() } ?: "—",
                )
                InfoRow(
                    Icons.Filled.AlternateEmail,
                    "Email",
                    volunteer?.email?.ifBlank { "—" } ?: "—",
                )
                InfoRow(
                    Icons.Filled.PhoneAndroid,
                    "Phone",
                    sim.phone.ifBlank { "Not set" },
                    detail = when {
                        sim.phone.isBlank() -> null
                        sim.phoneIsManual -> "Entered manually"
                        else -> sim.selectedCard?.displayName
                    },
                )
                InfoRow(
                    Icons.Filled.SimCard,
                    "SIM",
                    when {
                        !sim.permissionGranted -> "Permission denied"
                        sim.cards.isEmpty() && !sim.simPresent -> "None detected"
                        sim.cards.isEmpty() -> "Present, not readable"
                        else -> sim.cards.joinToString(", ") { it.displayName }
                    },
                )
                InfoRow(
                    if (sim.syncedToCloud) Icons.Filled.CloudDone else Icons.Filled.Sync,
                    "Control room",
                    if (sim.syncedToCloud && sim.phone.isNotBlank()) {
                        "Number synced"
                    } else {
                        "Number not synced yet"
                    },
                )
                InfoRow(
                    Icons.Filled.Info,
                    "Volunteer ID",
                    volunteer?.id?.take(12)?.plus("…") ?: "—",
                    monospace = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun MenuCard(
    onOpenMesh: () -> Unit,
    openDialog: (ProfileDialog) -> Unit,
    onSignOut: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.xxs),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            ProfileMenuItem(Icons.Filled.Person, "Personal Information", onClick = { openDialog(ProfileDialog.PERSONAL_INFO) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProfileMenuItem(Icons.Filled.Wifi, "Mesh Network", onClick = onOpenMesh)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProfileMenuItem(Icons.Filled.Info, "About App", onClick = { openDialog(ProfileDialog.ABOUT) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProfileMenuItem(
                Icons.AutoMirrored.Filled.ExitToApp,
                "Logout",
                textColor = MaterialTheme.colorScheme.error,
                onClick = onSignOut,
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String? = null,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(88.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else null,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    textColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val isDefault = textColor == Color.Unspecified
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant else textColor,
        )
        Spacer(modifier = Modifier.width(Spacing.lg))
        Text(
            title,
            color = if (isDefault) defaultColor else textColor,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}
