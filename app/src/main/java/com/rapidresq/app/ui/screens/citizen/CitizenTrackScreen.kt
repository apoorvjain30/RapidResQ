package com.rapidresq.app.ui.screens.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rapidresq.app.data.model.IncidentSeverity
import com.rapidresq.app.data.model.IncidentStatus
import com.rapidresq.app.ui.components.*
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenTrackScreen(
    incidentId: String,
    viewModel: RapidResQViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.incidents.collectAsState()
    val incident = incidents.find { it.id == incidentId }

    LaunchedEffect(incidentId) {
        viewModel.trackIncident(incidentId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        TopAppBar(
            title = { Text("Track Incident", fontWeight = FontWeight.Bold, color = BrandNavy) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandNavy)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
        )

        if (incident == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Incident $incidentId not found.", color = SlateTextSecondary, style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header / ID Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        border = CardDefaults.outlinedCardBorder(true)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = incident.id,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = BrandBlue,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                StatusBadge(status = incident.status)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryIcon(category = incident.category, tint = SlateTextPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = incident.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            SeverityBadge(severity = incident.severity)
                        }
                    }
                }

                // Location Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = BrandBlue)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(incident.locationText, style = MaterialTheme.typography.bodyLarge, color = SlateTextPrimary, fontWeight = FontWeight.Medium)
                                if (incident.gpsLabel != null) {
                                    Text(incident.gpsLabel, style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }

                // Map View item if coordinates available
                if (incident.lat != null && incident.lng != null) {
                    item {
                        val pins = remember(incident) {
                            val list = mutableListOf<MapPin>()
                            list.add(
                                MapPin(
                                    id = incident.id,
                                    title = incident.title,
                                    snippet = incident.locationText,
                                    lat = incident.lat,
                                    lng = incident.lng,
                                    pinType = if (incident.severity == IncidentSeverity.CRITICAL) MapPinType.INCIDENT_CRITICAL else MapPinType.INCIDENT_NORMAL
                                )
                            )
                            if (incident.assignedTeamLat != null && incident.assignedTeamLng != null && incident.assignedTeamName != null) {
                                list.add(
                                    MapPin(
                                        id = "TEAM_${incident.assignedTeamId}",
                                        title = incident.assignedTeamName,
                                        snippet = "Live Unit Location",
                                        lat = incident.assignedTeamLat,
                                        lng = incident.assignedTeamLng,
                                        pinType = MapPinType.RESPONDER_UNIT
                                    )
                                )
                            }
                            list
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Live Map Tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                                if (incident.assignedTeamLat != null) {
                                    Text("Unit En Route", style = MaterialTheme.typography.labelSmall, color = BrandBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                            RapidResQMapCard(
                                pins = pins,
                                centerLat = incident.lat,
                                centerLng = incident.lng,
                                zoom = 14.0,
                                height = 220.dp
                            )
                        }
                    }
                }

                // Assigned Team / Status Note
                item {
                    val (containerColor, noteText, noteColor) = when {
                        incident.status == IncidentStatus.REJECTED -> {
                            Triple(EmergencyRedBg, "Rejected: ${incident.rejectionReason ?: "Invalid report"}", EmergencyRed)
                        }
                        incident.assignedTeamName != null -> {
                            Triple(InfoCyanBg, "Assigned to: ${incident.assignedTeamName}", InfoCyan)
                        }
                        incident.status == IncidentStatus.VERIFIED -> {
                            Triple(WarningAmberBg, "Verified. Awaiting dispatch.", WarningAmber)
                        }
                        else -> {
                            Triple(LightSurfaceVariant, "Awaiting Dispatcher Verification...", SlateTextSecondary)
                        }
                    }
                    
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = noteText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = noteColor,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Safety Guidance
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BrandNavy),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("While You Wait", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            incident.category.safetyGuidance.forEach { tip ->
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text("•", color = WarningAmber, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                                    Text(tip, color = LightSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // Attached Media
                if (!incident.media.isNullOrEmpty()) {
                    item {
                        Text("Attached Media", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        incident.media.firstOrNull()?.let { media ->
                            Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))) {
                                AsyncImage(
                                    model = media.uri,
                                    contentDescription = "Incident Media",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (media.isVideo) {
                                    Surface(
                                        color = BrandNavy,
                                        contentColor = Color.White,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                    ) {
                                        Text("VIDEO", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Timeline
                item {
                    IncidentTimelineCard(timeline = incident.timeline)
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
