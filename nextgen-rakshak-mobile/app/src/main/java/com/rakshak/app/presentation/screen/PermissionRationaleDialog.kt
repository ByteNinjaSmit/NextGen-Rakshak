package com.rakshak.app.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rakshak.app.presentation.theme.Spacing

/**
 * Explains each runtime permission *before* the system prompt appears.
 *
 * Volunteers are being asked for camera, location and nearby-device access by an
 * app that scans faces — granting that blind is a lot to ask. Stating the reason
 * for each one first, and saying plainly what the app does not do, is what makes
 * the request reasonable.
 */
@Composable
fun PermissionRationaleDialog(onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Deliberately not dismissible — the app cannot work without these. */ },
        title = { Text("Permissions Rakshak needs") },
        text = {
            Column(
                // A landscape phone has little vertical room to spare for four
                // reasons plus the privacy note — scroll rather than clip.
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                PermissionReason(
                    name = "Camera",
                    reason = "To scan the crowd for a missing child. The camera opens only " +
                        "after an alert arrives and you tap Start Scanning.",
                )
                PermissionReason(
                    name = "Location",
                    reason = "To send police your position when you confirm a match, and to " +
                        "receive only the alerts raised near you.",
                )
                PermissionReason(
                    name = "Nearby devices",
                    reason = "To pass alerts phone-to-phone when the mobile network is " +
                        "congested, so the search keeps working with no signal.",
                )
                PermissionReason(
                    name = "Notifications",
                    reason = "To alert you the moment a child is reported missing.",
                )
                PermissionReason(
                    name = "Phone number",
                    reason = "To fill in your contact number from this SIM automatically, so " +
                        "an officer can reach you about a sighting without you typing it in.",
                )
                Text(
                    "Face matching happens entirely on this phone. No photo or face data " +
                        "of anyone in the crowd is ever uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
    )
}

@Composable
private fun PermissionReason(name: String, reason: String) {
    Column {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(reason, style = MaterialTheme.typography.bodySmall)
    }
}
