package com.rakshak.app.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rakshak.app.R
import com.rakshak.app.presentation.theme.Spacing
import com.rakshak.app.presentation.theme.rememberWindowInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onGoogleSignIn: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
) {
    val windowInfo = rememberWindowInfo()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        if (windowInfo.isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Branding(compact = true)
                }
                Spacer(modifier = Modifier.width(Spacing.xl))
                Column(
                    modifier = Modifier.weight(1f).widthIn(max = 420.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    SignInCard(onGoogleSignIn, busy, error)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Branding(compact = false)
                Spacer(modifier = Modifier.height(Spacing.xxl))
                SignInCard(onGoogleSignIn, busy, error)
            }
        }
    }
}

@Composable
private fun Branding(compact: Boolean) {
    Image(
        painter = painterResource(id = R.drawable.rakshak_logo),
        contentDescription = "NextGen Rakshak",
        modifier = Modifier.height(if (compact) 120.dp else 140.dp),
    )
    Spacer(modifier = Modifier.height(Spacing.lg))
    Text(
        "Volunteer Portal",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(Spacing.sm))
    Text(
        "Sign in with your Google account to assist in identifying missing " +
            "children and reporting sightings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = Spacing.lg),
    )
}

@Composable
private fun SignInCard(onGoogleSignIn: () -> Unit, busy: Boolean, error: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onGoogleSignIn,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (error != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
