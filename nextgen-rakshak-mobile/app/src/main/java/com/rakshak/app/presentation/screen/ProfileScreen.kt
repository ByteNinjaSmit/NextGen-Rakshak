package com.rakshak.app.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.presentation.theme.AlertRed
import com.rakshak.app.presentation.theme.PrimaryBlue
import com.rakshak.app.presentation.theme.SafeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(volunteer: Volunteer?, onSignOut: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.SemiBold) }
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
            // Avatar
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
                
                // Verified Badge
                Icon(
                    Icons.Filled.CheckCircle, 
                    contentDescription = "Verified", 
                    tint = SafeGreen,
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.White, CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                volunteer?.name?.ifBlank { "Volunteer" } ?: "Volunteer", 
                fontSize = 20.sp, 
                fontWeight = FontWeight.Bold
            )
            Text(
                volunteer?.role?.replaceFirstChar { it.uppercase() } ?: "Unit",
                color = Color.Gray,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Verified Tag
            Box(
                modifier = Modifier
                    .border(1.dp, SafeGreen, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Verified", color = SafeGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Menu Items
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ProfileMenuItem(Icons.Filled.Person, "Personal Information", onClick = {})
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    ProfileMenuItem(Icons.Filled.Settings, "App Settings", onClick = {})
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    ProfileMenuItem(Icons.Filled.Help, "Help & Support", onClick = {})
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    ProfileMenuItem(Icons.Filled.Info, "About App", onClick = {})
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    ProfileMenuItem(Icons.Filled.ExitToApp, "Logout", textColor = AlertRed, onClick = onSignOut)
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector, 
    title: String, 
    textColor: Color = Color.Black,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if(textColor == Color.Black) Color.Gray else textColor)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = textColor, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}
