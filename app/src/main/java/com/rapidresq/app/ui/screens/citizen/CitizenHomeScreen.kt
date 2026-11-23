package com.rapidresq.app.ui.screens.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import com.rapidresq.app.data.model.Incident
import com.rapidresq.app.ui.components.CategoryIcon
import com.rapidresq.app.ui.components.SeverityBadge
import com.rapidresq.app.ui.components.StatusBadge
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenHomeScreen(
    viewModel: RapidResQViewModel,
    onBackToRoles: () -> Unit,
    onReportClick: () -> Unit,
    onTrackIncident: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.incidents.collectAsState()
    var trackId by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        // Top Bar
        TopAppBar(
            title = { Text("Citizen Emergency Portal", fontWeight = FontWeight.Bold, color = BrandNavy) },
            navigationIcon = {
                IconButton(onClick = onBackToRoles) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandNavy)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SOS Card
            item {
                Card(
                    onClick = onReportClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EmergencyRed),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Report an Emergency",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Track Incident Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    border = CardDefaults.outlinedCardBorder(true)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Track Existing Incident",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = trackId,
                                onValueChange = { trackId = it.toUpperCase(Locale.current) },
                                placeholder = { Text("RRQ-XXXXXX") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandBlue,
                                    unfocusedBorderColor = SubtleBorder,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { if (trackId.isNotBlank()) onTrackIncident(trackId) },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Text("Track")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Active & Recent Demo Incidents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextPrimary
                )
            }

            // Incidents List
            items(incidents) { incident ->
                IncidentSummaryCard(
                    incident = incident,
                    onClick = { onTrackIncident(incident.id) }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun IncidentSummaryCard(
    incident: Incident,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = incident.id,
                    style = MaterialTheme.typography.labelLarge,
                    color = BrandBlue,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(status = incident.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = LightSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CategoryIcon(category = incident.category, tint = SlateTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = incident.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = incident.locationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SeverityBadge(severity = incident.severity)
        }
    }
}
