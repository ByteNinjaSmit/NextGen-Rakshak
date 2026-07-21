package com.rakshak.app.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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

/** Mock sign-in: phone + role. Real OTP is out of scope for the MVP. */
@Composable
fun LoginScreen(onSignIn: (phone: String, role: String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(ROLES.first()) }

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

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone number") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Role", modifier = Modifier.padding(top = 16.dp).fillMaxWidth())
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
            onClick = { onSignIn(phone.trim(), role) },
            enabled = phone.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Text("Sign In")
        }
    }
}
