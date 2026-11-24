package com.rapidresq.app.ui.screens.responder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rapidresq.app.data.model.Incident
import com.rapidresq.app.data.model.IncidentStatus
import com.rapidresq.app.data.model.ResponseTeam
import com.rapidresq.app.ui.components.CategoryIcon
import com.rapidresq.app.ui.components.DemoModeBanner
import com.rapidresq.app.ui.components.IncidentTimelineCard
import com.rapidresq.app.ui.components.SeverityBadge
import com.rapidresq.app.ui.components.StatusBadge
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponderDashboardScreen(
    viewModel: RapidResQViewModel,
    onBackToRoles: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.incidents.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val activeTeamId by viewModel.activeResponderTeamId.collectAsState()
    val session by viewModel.currentSession.collectAsState()
    val isLockedToTeam = session?.assignedTeamId != null

    LaunchedEffect(session) {
        session?.assignedTeamId?.let { teamId ->
            viewModel.setActiveResponderTeam(teamId)
        }
    }

    val activeTeam = teams.find { it.id == activeTeamId }
    
    // Find active assignment
    val activeIncident = incidents.find { 
        it.assignedTeamId == activeTeamId && 
        it.status !in listOf(IncidentStatus.RESOLVED, IncidentStatus.REJECTED) 
    }

    val completedIncidents = incidents.filter { 
        it.assignedTeamId == activeTeamId && it.status == IncidentStatus.RESOLVED 
    }.sortedByDescending { it.createdAt }

    Column(modifier = modifier.fillMaxSize().background(LightBackground)) {
        DemoModeBanner()
        TopAppBar(
            title = { Text("Responder Unit - ${session?.name ?: ""}", fontWeight = FontWeight.Bold, color = BrandNavy) },
            navigationIcon = {
                IconButton(onClick = onBackToRoles) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandNavy)
                }
            },
            actions = {
                IconButton(onClick = { viewModel.logout(); onLogout() }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = BrandNavy)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
        )

        if (!isLockedToTeam) {
            // Team Switcher Strip
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().background(LightSurface)
            ) {
                items(teams) { team ->
                    TeamSelectorChip(
                        team = team,
                        isSelected = team.id == activeTeamId,
                        onClick = { viewModel.setActiveResponderTeam(team.id) }
                    )
                }
            }
        } else {
            Surface(modifier = Modifier.fillMaxWidth().background(LightSurface).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Assigned to: ${activeTeam?.name ?: ""}", fontWeight = FontWeight.Bold, color = BrandBlue)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Active Assignment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextPrimary
                )
            }

            if (activeIncident == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        border = CardDefaults.outlinedCardBorder(true)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeEmerald, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Unit Standing By", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            Text("Ready for Dispatch", style = MaterialTheme.typography.bodyMedium, color = SlateTextSecondary)
                        }
                    }
                }
            } else {
                item {
                    ActiveAssignmentCard(
                        incident = activeIncident,
                        teamName = activeTeam?.name ?: "Responder",
                        viewModel = viewModel
                    )
                }
            }

            if (completedIncidents.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Completed by This Unit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                }
                items(completedIncidents) { incident ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(incident.id, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                                Text(incident.title, style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                            }
                            StatusBadge(status = incident.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeamSelectorChip(team: ResponseTeam, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) BrandNavy else LightSurfaceVariant
    val textColor = if (isSelected) Color.White else SlateTextPrimary

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (team.isAvailable) SafeEmerald else EmergencyRed))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(team.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textColor)
                Text(team.unitType, style = MaterialTheme.typography.labelSmall, color = if (isSelected) Color.White.copy(alpha = 0.7f) else SlateTextSecondary)
            }
        }
    }
}

@Composable
fun ActiveAssignmentCard(
    incident: Incident,
    teamName: String,
    viewModel: RapidResQViewModel
) {
    var noteInput by remember { mutableStateOf("") }
    
    val nextStep = incident.status.nextResponderStep()
    val buttonLabel = when (nextStep) {
        IncidentStatus.ACCEPTED -> "Accept Assignment"
        IncidentStatus.EN_ROUTE -> "Mark En Route"
        IncidentStatus.ON_SITE -> "Mark On Site"
        IncidentStatus.RESPONDING -> "Start Responding"
        IncidentStatus.RESOLVED -> "Mark Resolved"
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(incident.id, style = MaterialTheme.typography.headlineSmall, color = BrandBlue, fontWeight = FontWeight.ExtraBold)
                StatusBadge(status = incident.status)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(category = incident.category, tint = SlateTextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(incident.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            SeverityBadge(severity = incident.severity)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Location
            Surface(color = LightSurfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("LOCATION", style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                    Text(incident.locationText, style = MaterialTheme.typography.bodyLarge, color = SlateTextPrimary, modifier = Modifier.padding(top = 4.dp))
                    if (incident.gpsLabel != null) {
                        Text(incident.gpsLabel, style = MaterialTheme.typography.bodySmall, color = InfoCyan, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // Media
            if (!incident.media.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("ATTACHED MEDIA", style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                incident.media.firstOrNull()?.let { media ->
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp))) {
                        AsyncImage(model = media.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (media.isVideo) {
                            Surface(color = BrandNavy, contentColor = Color.White, shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                                Text("VIDEO", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // Actions & Notes
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("Field note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        if (noteInput.isNotBlank()) {
                            viewModel.addFieldNote(incident.id, teamName, noteInput)
                            noteInput = ""
                        }
                    },
                    enabled = noteInput.isNotBlank()
                ) {
                    Text("Add Note Only")
                }

                if (buttonLabel != null) {
                    Button(
                        onClick = {
                            if (noteInput.isNotBlank()) {
                                viewModel.addFieldNote(incident.id, teamName, noteInput)
                                noteInput = ""
                            }
                            viewModel.advanceResponderStatus(incident.id, teamName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(buttonLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            IncidentTimelineCard(timeline = incident.timeline)
        }
    }
}
