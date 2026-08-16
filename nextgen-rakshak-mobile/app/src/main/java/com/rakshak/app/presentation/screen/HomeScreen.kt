package com.rakshak.app.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rakshak.app.data.model.Alert
import com.rakshak.app.presentation.theme.AlertRed
import com.rakshak.app.presentation.theme.PrimaryBlue
import com.rakshak.app.presentation.theme.SafeGreen
import com.rakshak.app.presentation.viewmodel.HomeViewModel
import com.rakshak.app.utils.ElapsedTime
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onStartScan: () -> Unit, onSignOut: () -> Unit = {}) {
    val alerts by viewModel.activeAlerts.collectAsStateWithLifecycle()
    var selectedAlert by remember { mutableStateOf<Alert?>(null) }

    if (selectedAlert != null) {
        AlertDetailsScreen(
            alert = selectedAlert!!,
            onBack = { selectedAlert = null },
            onStartScan = onStartScan
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dashboard", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { /* Open drawer */ }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                        }
                    }
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                // Status Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(SafeGreen.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SafeGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("You are Active", fontWeight = FontWeight.Bold, color = SafeGreen)
                        Text("Ready to help", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Active Alerts", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("View All", color = PrimaryBlue, fontSize = 14.sp, modifier = Modifier.clickable { })
                }

                if (alerts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active alerts right now.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(alerts) { alert ->
                            AlertRow(alert, onClick = { selectedAlert = alert })
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: Alert, onClick: () -> Unit) {
    val elapsed by produceState(ElapsedTime.since(alert.timestamp), alert.timestamp) {
        while (true) {
            value = ElapsedTime.since(alert.timestamp)
            delay(30_000)
        }
    }
    
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val formattedTime = if (alert.timestamp > 0) timeFormat.format(Date(alert.timestamp)) else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (alert.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = alert.imageUrl,
                    contentDescription = "Child Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text("${alert.childName}, ${alert.age} yrs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(alert.clothingDesc, fontSize = 12.sp, color = Color.DarkGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (alert.lastSeen.isNotBlank()) {
                    Text(alert.lastSeen, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formattedTime, fontSize = 12.sp, color = Color.Gray)
            }
            
            // NEW badge
            Box(
                modifier = Modifier
                    .background(AlertRed.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("NEW", color = AlertRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailsScreen(alert: Alert, onBack: () -> Unit, onStartScan: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alert Details", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Share */ }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large Photo
            if (alert.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = alert.imageUrl,
                    contentDescription = "Child Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.LightGray)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("${alert.childName}, ${alert.age} Years", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            val dateFormat = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault())
            val formattedTime = if (alert.timestamp > 0) dateFormat.format(Date(alert.timestamp)) else "Unknown"

            // Details list
            Column(modifier = Modifier.fillMaxWidth()) {
                DetailRow("Clothing", alert.clothingDesc)
                DetailRow("Last Seen", alert.lastSeen)
                DetailRow("Time", formattedTime)
                DetailRow("Gender", alert.gender.replaceFirstChar { it.uppercase() })
                DetailRow("Additional Info", alert.parentContact.ifEmpty { "No additional info." })
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = onStartScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text("Start Scanning", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(120.dp),
            color = Color.Gray,
            fontSize = 14.sp
        )
        Text(
            text = value.ifBlank { "-" },
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
