package com.rakshak.app.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions

private val ROLES = listOf("police", "ncc", "ngo", "community")
private const val MIN_PASSWORD = 6

/**
 * Volunteer sign-in, offering three routes.
 *
 * Google and email/password both produce an identifiable account, which is what
 * the synopsis's "pre-registered, credible volunteers" model needs: a sighting
 * sent to police should be attributable to a real person. The anonymous route is
 * kept for demonstrations and is labelled as unverified.
 *
 * The role is chosen before signing in because it is what the officer sees beside
 * a match, and no identity provider can tell us which one applies.
 */
@Composable
fun LoginScreen(
    onGoogleSignIn: (role: String) -> Unit,
    onEmailSignIn: (email: String, password: String, role: String, register: Boolean) -> Unit,
    onAnonymousSignIn: (phone: String, role: String) -> Unit,
    busy: Boolean = false,
    error: String? = null,
) {
    var role by remember { mutableStateOf(ROLES.first()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var registerMode by remember { mutableStateOf(false) }
    var showAnonymous by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }

    val emailValid = email.contains("@") && email.contains(".")
    val passwordValid = password.length >= MIN_PASSWORD
    val canSubmitEmail = emailValid && passwordValid && !busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Rakshak Volunteer", fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "Sign in to receive missing-child alerts.",
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text("I am volunteering as", modifier = Modifier.fillMaxWidth())
        ROLES.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = role == option, onClick = { role = option })
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = role == option, onClick = { role = option })
                Text(option.replaceFirstChar { it.uppercase() })
            }
        }

        // --- Google ---
        Button(
            onClick = { onGoogleSignIn(role) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // --- Email + password ---
        Text(
            if (registerMode) "Create an account" else "Sign in with email",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            singleLine = true,
            isError = email.isNotEmpty() && !emailValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            isError = password.isNotEmpty() && !passwordValid,
            supportingText = {
                if (password.isNotEmpty() && !passwordValid) {
                    Text("At least $MIN_PASSWORD characters")
                }
            },
            visualTransformation =
                if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = { onEmailSignIn(email, password, role, registerMode) },
            enabled = canSubmitEmail,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (registerMode) "Create account" else "Sign in")
        }
        TextButton(onClick = { registerMode = !registerMode }, enabled = !busy) {
            Text(
                if (registerMode) "I already have an account"
                else "New volunteer? Create an account"
            )
        }

        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // --- Anonymous (demo) ---
        if (!showAnonymous) {
            OutlinedButton(
                onClick = { showAnonymous = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue as guest (demo)")
            }
        } else {
            Text(
                "Guest sign-in creates an anonymous account with no verified " +
                    "identity. A match you report cannot be traced back to you.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { onAnonymousSignIn(phone.trim(), role) },
                enabled = phone.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Continue as guest")
            }
        }
    }
}
