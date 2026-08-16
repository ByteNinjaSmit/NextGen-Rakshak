package com.rakshak.app.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.rakshak.app.data.model.MatchStatus
import com.rakshak.app.data.model.MatchStatusReport
import com.rakshak.app.presentation.theme.AlertRed
import com.rakshak.app.presentation.theme.PrimaryBlue
import com.rakshak.app.presentation.theme.SafeGreen
import com.rakshak.app.presentation.viewmodel.MatchesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(viewModel: MatchesViewModel) {
    val myMatches by viewModel.myMatches.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Matches", fontWeight = FontWeight.SemiBold) },
            )
        }
    ) { padding ->
        if (myMatches.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No matches yet", color = Color.Gray)
                    Text("Sightings you report will appear here.", color = Color.LightGray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(myMatches, key = { it.id }) { match ->
                    MatchRow(match)
                }
            }
        }
    }
}

@Composable
private fun MatchRow(match: MatchStatusReport) {
    val dateFormat = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())
    val formattedTime = if (match.timestampMillis > 0) dateFormat.format(Date(match.timestampMillis)) else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (match.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = match.imageUrl,
                    contentDescription = "Child Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray)
                )
            } else {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(match.childName.ifBlank { "Unnamed child" }, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formattedTime, fontSize = 12.sp, color = Color.Gray)
            }

            StatusBadge(match.status)
        }
    }
}

@Composable
private fun StatusBadge(status: MatchStatus) {
    val (label, color) = when (status) {
        MatchStatus.PENDING -> "Pending" to PrimaryBlue
        MatchStatus.DISPATCHED -> "Dispatched" to PrimaryBlue
        MatchStatus.ACCEPTED -> "Accepted" to SafeGreen
        MatchStatus.DISMISSED -> "Dismissed" to AlertRed
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
