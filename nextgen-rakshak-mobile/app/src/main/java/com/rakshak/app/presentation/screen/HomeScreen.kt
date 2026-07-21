package com.rakshak.app.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rakshak.app.data.model.Alert
import com.rakshak.app.presentation.viewmodel.HomeViewModel

/** Lists active alerts and lets the volunteer start crowd scanning. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onStartScan: () -> Unit) {
    val alerts by viewModel.activeAlerts.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Rakshak — Active Alerts") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (alerts.isEmpty()) {
                Text("No active alerts right now.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(alerts) { AlertRow(it) }
                }
                Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Scanning")
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: Alert) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(alert.childName, fontWeight = FontWeight.Bold)
            Text("${alert.age} yrs · ${alert.gender}")
            Text(alert.clothingDesc)
        }
    }
}
