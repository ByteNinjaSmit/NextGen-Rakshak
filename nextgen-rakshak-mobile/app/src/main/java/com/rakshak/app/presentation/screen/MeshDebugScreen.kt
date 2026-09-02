package com.rakshak.app.presentation.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rakshak.app.networking.mesh.MeshNetworkManager
import com.rakshak.app.utils.LocationSettings

/**
 * Live view of the offline mesh: how many volunteers this device is linked to,
 * and a rolling packet log (received / relayed / dropped, with timestamps). Used
 * for the multi-device mesh trial (VER-08) and as the volunteer's proof the mesh
 * is actually up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshDebugScreen(mesh: MeshNetworkManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val peers by mesh.peerCount.collectAsStateWithLifecycle()
    val log by mesh.log.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Re-checked whenever the screen is recomposed (e.g. returning from settings).
    var locationOn by remember { mutableStateOf(LocationSettings.enabled(context)) }
    LaunchedEffect(Unit) { locationOn = LocationSettings.enabled(context) }

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Network", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (!locationOn) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Location is off — the mesh may not find nearby volunteers " +
                                "even with permission granted.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("Turn on") }
                    }
                }
            }

            Text(
                when (peers) {
                    0 -> "Searching for nearby volunteers…"
                    1 -> "Linked to 1 nearby volunteer"
                    else -> "Linked to $peers nearby volunteers"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                "Packets relay device-to-device with no internet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                items(log) { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
