package com.rakshak.app.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ROLES = listOf("police", "ncc", "ngo", "community")

/**
 * Volunteer sign-in.
 *
 * Google is the primary route: a sighting reported to police should be
 * attributable to an identifiable person, which anonymous sign-in cannot give.
 * The phone-only route is kept as a clearly-labelled demo fallback so the app
 * stays usable before OAuth is configured for the project.
 *
 * The role is chosen before signing in because it is what the officer sees
 * beside a match, and it is not something Google can tell us.
 */
@Composable
fun LoginScreen(
    onGoogleSignIn: (role: String) -> Unit,
    onDemoSignIn: (phone: String, role: String) -> Unit,
    busy: Boolean = false,
    error: String? = null,
) {
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(ROLES.first()) }
    var showDemo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Rakshak Volunteer", fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "Sign in to receive missing-child alerts.",
            modifier = Modifier.padding(bottom = 24.dp),
        )

        Text("I am volunteering as", modifier = Modifier.fillMaxWidth())
        ROLES.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = role == option, onClick = { role = option })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = role == option, onClick = { role = option })
                Text(option.replaceFirstChar { it.uppercase() })
            }
        }

        Button(
            onClick = { onGoogleSignIn(role) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Continue with Google")
            }
        }

        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 20.dp))

        if (!showDemo) {
            TextButton(onClick = { showDemo = true }, enabled = !busy) {
                Text("Continue without Google (demo)")
            }
        } else {
            Text(
                "Demo sign-in — creates an anonymous account with no verified identity.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone number") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onDemoSignIn(phone.trim(), role) },
                enabled = phone.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text("Sign In")
            }
        }
    }
}
