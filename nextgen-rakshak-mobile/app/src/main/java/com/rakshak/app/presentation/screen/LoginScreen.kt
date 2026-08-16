package com.rakshak.app.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshak.app.presentation.theme.PrimaryBlue

private val ROLES = listOf("police", "ncc", "ngo", "community")
private const val MIN_PASSWORD = 6

@OptIn(ExperimentalMaterial3Api::class)
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

    // Extra fields for Register UI
    var fullName by remember { mutableStateOf("") }
    var volunteerId by remember { mutableStateOf("") }

    val emailValid = email.contains("@") && email.contains(".")
    val passwordValid = password.length >= MIN_PASSWORD
    val canSubmitEmail = emailValid && passwordValid && !busy

    Scaffold(
        topBar = {
            if (registerMode) {
                TopAppBar(
                    title = { Text("Volunteer Registration", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { registerMode = false }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!registerMode) {
                Spacer(modifier = Modifier.height(48.dp))
                // Login Header
                Text(
                    "Welcome Back!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = PrimaryBlue,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Login to continue",
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
                )

                // Role selection visually minimized for login
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ROLES.take(2).forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { role = option }) {
                            RadioButton(selected = role == option, onClick = { role = option })
                            Text(option.replaceFirstChar { it.uppercase() }, fontSize = 14.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text("Volunteer ID / Email") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    isError = email.isNotEmpty() && !emailValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    singleLine = true,
                    isError = password.isNotEmpty() && !passwordValid,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    "Forgot Password?",
                    color = PrimaryBlue,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable { /* TODO */ },
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onEmailSignIn(email, password, role, false) },
                    enabled = canSubmitEmail,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Don't have an account? ")
                    Text(
                        "Register Now",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { registerMode = true }
                    )
                }

            } else {
                // Register Mode
                Text("Fill in your details", color = Color.Gray, modifier = Modifier.padding(bottom = 24.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = volunteerId,
                    onValueChange = { volunteerId = it },
                    label = { Text("Volunteer ID") },
                    leadingIcon = { Icon(Icons.Filled.Security, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text("Email (for login)") },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    singleLine = true,
                    isError = email.isNotEmpty() && !emailValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // Role selection mapped to Organization
                Text("Organization / Unit", modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), fontSize = 14.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                     ROLES.take(2).forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { role = option }) {
                            RadioButton(selected = role == option, onClick = { role = option })
                            Text(option.replaceFirstChar { it.uppercase() }, fontSize = 14.sp)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                     ROLES.drop(2).forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { role = option }) {
                            RadioButton(selected = role == option, onClick = { role = option })
                            Text(option.replaceFirstChar { it.uppercase() }, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    singleLine = true,
                    isError = password.isNotEmpty() && !passwordValid,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onEmailSignIn(email, password, role, true) },
                    enabled = canSubmitEmail,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Register", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (error != null) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            if (!registerMode) {
                HorizontalDivider(Modifier.padding(vertical = 24.dp))
                
                // Google Sign In
                OutlinedButton(
                    onClick = { onGoogleSignIn(role) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text("Continue with Google")
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // Anonymous (demo)
                if (!showAnonymous) {
                    TextButton(onClick = { showAnonymous = true }) {
                        Text("Continue as guest (demo)")
                    }
                } else {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedButton(
                        onClick = { onAnonymousSignIn(phone.trim(), role) },
                        enabled = phone.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text("Continue as guest")
                    }
                }
            }
        }
    }
}
