package com.rapidresq.app.ui.screens.responder

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rapidresq.app.data.model.*
import com.rapidresq.app.ui.components.*
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
    val isCloudConnected by viewModel.isCloudConnected.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val isSharingGps by viewModel.isResponderSharingGps.collectAsState()

    var showAiSheet by remember { mutableStateOf(false) }
    val isLockedToTeam = session?.assignedTeamId != null

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.toggleResponderLiveGps(true)
        }
    }

    LaunchedEffect(session) {
        session?.assignedTeamId?.let { teamId ->
            viewModel.setActiveResponderTeam(teamId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            RapidResQCommandTopBar(
                title = "Responder Unit",
                subtitle = activeTeam?.name ?: activeTeamId,
                isCloudConnected = isCloudConnected,
                pendingOfflineCount = pendingCount,
                showBack = true,
                canGoBack = false,
                onBack = onBackToRoles,
                onAiClick = { showAiSheet = true },
                onLogoutClick = { viewModel.logout(); onLogout() }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).background(LightBackground)) {
            DemoModeBanner(isCloudConnected = isCloudConnected)

            if (!isOnline || pendingCount > 0) {
                Surface(
                    color = WarningAmber,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Offline Mode • $pendingCount status action(s) queued locally",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
                    )
                }
            }

            // Team Switcher / Unit Info Header
            Surface(color = LightSurface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(activeTeam?.name ?: "First Responder Unit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            Text("${activeTeam?.unitType ?: "EMS"} • ${activeTeam?.baseLocation ?: "Indore Station"}", style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                        }
                        val isAvailable = activeTeam?.isAvailable == true
                        Surface(
                            color = if (isAvailable) SafeEmeraldBg else EmergencyRedBg,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = if (isAvailable) "Available" else "On Assignment",
                                color = if (isAvailable) SafeEmerald else EmergencyRed,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // GPS Sharing Toggle Chip (Privacy First)
                    if (activeIncident != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FilterChip(
                            selected = isSharingGps,
                            onClick = {
                                if (isSharingGps) {
                                    viewModel.toggleResponderLiveGps(false)
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            label = {
                                Text(
                                    if (isSharingGps) "Sharing Live Unit GPS" else "Share Live Unit GPS",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandBlue,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }

                    if (!isLockedToTeam) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PrimaryScrollableTabRow(
                            selectedTabIndex = teams.indexOfFirst { it.id == activeTeamId }.coerceAtLeast(0),
                            containerColor = LightSurfaceVariant,
                            edgePadding = 0.dp,
                            divider = {}
                        ) {
                            teams.forEach { team ->
                                val isSelected = team.id == activeTeamId
                                Tab(
                                    selected = isSelected,
                                    onClick = { viewModel.setActiveResponderTeam(team.id) },
                                    text = { Text(team.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active Assignment
                item {
                    Text("Active Emergency Assignment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (activeIncident == null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = LightSurface),
                            border = CardDefaults.outlinedCardBorder(true)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeEmerald, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No Active Assignment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Unit is clear and on standby for new dispatches.", style = MaterialTheme.typography.bodyMedium, color = SlateTextSecondary)
                            }
                        }
                    } else {
                        ResponderActiveIncidentCard(activeIncident, viewModel)
                    }
                }

                // Completed History
                if (completedIncidents.isNotEmpty()) {
                    item {
                        Text("Recently Resolved by ${activeTeam?.name ?: "Unit"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(completedIncidents) { incident ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = LightSurface),
                            border = CardDefaults.outlinedCardBorder(true)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(incident.id, style = MaterialTheme.typography.labelMedium, color = BrandBlue, fontWeight = FontWeight.Bold)
                                    Text(incident.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(incident.locationText, style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                                }
                                StatusBadge(status = incident.status)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAiSheet) {
        AiAssistantSheet(viewModel = viewModel) {
            showAiSheet = false
        }
    }
}

@Composable
fun ResponderActiveIncidentCard(
    incident: Incident,
    viewModel: RapidResQViewModel
) {
    var fieldNoteText by remember { mutableStateOf("") }
    val nextStatus = incident.status.nextResponderStep()

    val mapPins = remember(incident) {
        val list = mutableListOf<MapPin>()
        if (incident.lat != null && incident.lng != null) {
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
        }
        list
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(incident.id, style = MaterialTheme.typography.headlineSmall, color = BrandBlue, fontWeight = FontWeight.ExtraBold)
                StatusBadge(status = incident.status)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(category = incident.category, tint = SlateTextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(incident.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(incident.locationText, style = MaterialTheme.typography.bodyMedium, color = SlateTextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SeverityBadge(severity = incident.severity)

            if (incident.lat != null && incident.lng != null) {
                Spacer(modifier = Modifier.height(16.dp))
                RapidResQMapCard(
                    pins = mapPins,
                    centerLat = incident.lat,
                    centerLng = incident.lng,
                    zoom = 14.0,
                    height = 180.dp
                )
            }

            // Step Progress Button
            Spacer(modifier = Modifier.height(20.dp))
            if (nextStatus != null) {
                Button(
                    onClick = { viewModel.advanceResponderStatus(incident.id, "Responder") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Text("MARK AS $nextStatus", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            } else {
                Surface(
                    color = SafeEmeraldBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("INCIDENT RESOLVED", color = SafeEmerald, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }

            // Field Note Input
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = fieldNoteText,
                onValueChange = { fieldNoteText = it },
                label = { Text("Add Field Note / SitRep") },
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
                ),
                trailingIcon = {
                    if (fieldNoteText.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.addFieldNote(incident.id, "Responder Lead", fieldNoteText)
                            fieldNoteText = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Note", tint = BrandBlue)
                        }
                    }
                }
            )

            // Timeline
            Spacer(modifier = Modifier.height(16.dp))
            IncidentTimelineCard(timeline = incident.timeline)
        }
    }
}
