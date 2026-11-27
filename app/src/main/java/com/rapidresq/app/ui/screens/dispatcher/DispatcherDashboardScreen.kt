package com.rapidresq.app.ui.screens.dispatcher

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rapidresq.app.data.model.*
import com.rapidresq.app.ui.components.*
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatcherDashboardScreen(
    viewModel: RapidResQViewModel,
    onBackToRoles: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.incidents.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val session by viewModel.currentSession.collectAsState()
    val isCloudConnected by viewModel.isCloudConnected.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val autoDispatchEnabled by viewModel.autoDispatchEnabled.collectAsState()
    val isActionInProgress by viewModel.isActionInProgress.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var selectedFilter by remember { mutableStateOf("Active") }
    var showAiSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(actionFeedback) {
        if (actionFeedback != null) {
            snackbarHostState.showSnackbar(actionFeedback!!)
            viewModel.clearActionFeedback()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Dispatcher Command - ${session?.name ?: ""}", fontWeight = FontWeight.Bold, color = BrandNavy) },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAiSheet = true },
                containerColor = BrandBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assist")
            }
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
                        text = "Offline Mode • $pendingCount action(s) queued locally",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
                    )
                }
            }

            // KPI Summary
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard("Total", incidents.size.toString(), Modifier.weight(1f))
                KpiCard("Pending", incidents.count { it.status == IncidentStatus.REPORTED }.toString(), Modifier.weight(1f), EmergencyRed)
                KpiCard("Active", incidents.count { it.status !in listOf(IncidentStatus.REPORTED, IncidentStatus.RESOLVED, IncidentStatus.REJECTED) }.toString(), Modifier.weight(1f), WarningAmber)
                KpiCard("Units", teams.count { it.isAvailable }.toString(), Modifier.weight(1f), SafeEmerald)
            }

            // Auto-Dispatch Control
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = if (autoDispatchEnabled) BrandBlueLight else LightSurface),
                border = CardDefaults.outlinedCardBorder(true)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Dispatch Units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandBlue)
                        Text(
                            text = if (autoDispatchEnabled) "Auto-assigning nearest unit via PostGIS" else "Manual dispatcher approval required",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (autoDispatchEnabled) BrandBlue else SlateTextSecondary
                        )
                    }
                    Switch(
                        checked = autoDispatchEnabled,
                        onCheckedChange = { viewModel.setAutoDispatchEnabled(it) },
                        enabled = !isActionInProgress,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBlue)
                    )
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = LightSurface,
                contentColor = BrandBlue
            ) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Incidents", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Live Map", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, text = { Text("Audit Log", fontWeight = FontWeight.Bold) })
            }

            when (selectedTabIndex) {
                0 -> {
                    // Incidents Tab
                    ScrollableTabRow(
                        selectedTabIndex = listOf("Active", "All", "Pending", "Verified", "Rejected", "Resolved").indexOf(selectedFilter).coerceAtLeast(0),
                        containerColor = LightBackground,
                        contentColor = BrandBlue,
                        edgePadding = 16.dp,
                        modifier = Modifier.padding(vertical = 8.dp),
                        divider = {}
                    ) {
                        listOf("Active", "All", "Pending", "Verified", "Rejected", "Resolved").forEach { filter ->
                            val isSelected = selectedFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.padding(end = 8.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandBlueLight, selectedLabelColor = BrandBlue)
                            )
                        }
                    }

                    val filteredIncidents = incidents.filter {
                        when (selectedFilter) {
                            "Active" -> it.status !in listOf(IncidentStatus.RESOLVED, IncidentStatus.REJECTED)
                            "Pending" -> it.status == IncidentStatus.REPORTED
                            "Verified" -> it.status == IncidentStatus.VERIFIED
                            "Rejected" -> it.status == IncidentStatus.REJECTED
                            "Resolved" -> it.status == IncidentStatus.RESOLVED
                            else -> true
                        }
                    }.sortedByDescending { it.createdAt }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredIncidents) { incident ->
                            DispatcherIncidentCard(incident, teams, viewModel, isActionInProgress)
                        }
                    }
                }
                1 -> {
                    // Live Map Tab
                    val mapPins = remember(incidents, teams) {
                        val list = mutableListOf<MapPin>()
                        incidents.filter { it.status !in listOf(IncidentStatus.RESOLVED, IncidentStatus.REJECTED) }.forEach { inc ->
                            if (inc.lat != null && inc.lng != null) {
                                list.add(
                                    MapPin(
                                        id = inc.id,
                                        title = "${inc.id} - ${inc.title}",
                                        snippet = "${inc.category.label} (${inc.status})",
                                        lat = inc.lat,
                                        lng = inc.lng,
                                        pinType = if (inc.severity == IncidentSeverity.CRITICAL) MapPinType.INCIDENT_CRITICAL else MapPinType.INCIDENT_NORMAL
                                    )
                                )
                            }
                        }
                        teams.forEach { team ->
                            list.add(
                                MapPin(
                                    id = team.id,
                                    title = team.name,
                                    snippet = "${team.unitType} (${if (team.isAvailable) "Available" else "On Duty"})",
                                    lat = team.lat,
                                    lng = team.lng,
                                    pinType = MapPinType.RESPONDER_UNIT
                                )
                            )
                        }
                        list
                    }

                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = LightSurface),
                            border = CardDefaults.outlinedCardBorder(true)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(EmergencyRed))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Critical Incident", style = MaterialTheme.typography.labelSmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(WarningAmber))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Incident", style = MaterialTheme.typography.labelSmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(BrandBlue))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Response Unit", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        RapidResQMapCard(
                            pins = mapPins,
                            centerLat = 22.7196,
                            centerLng = 75.8577,
                            zoom = 12.5,
                            height = 420.dp
                        )
                    }
                }
                2 -> {
                    // Audit Log Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(auditLogs.sortedByDescending { it.timestamp }) { log ->
                            AuditLogCard(log)
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
fun KpiCard(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = BrandBlue) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatcherIncidentCard(
    incident: Incident,
    teams: List<ResponseTeam>,
    viewModel: RapidResQViewModel,
    isActionInProgress: Boolean
) {
    var showVerifyDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(incident.id, style = MaterialTheme.typography.titleMedium, color = BrandBlue, fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = incident.status)
                    if (incident.status == IncidentStatus.REJECTED) {
                        IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp).padding(start = 8.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = EmergencyRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (incident.possibleDuplicateOf != null) {
                Surface(
                    color = WarningAmberBg,
                    contentColor = WarningAmber,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Possible duplicate of ${incident.possibleDuplicateOf}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (incident.aiSummary != null) {
                Surface(
                    color = BrandBlueLight,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Triage Advisory (Human Verify)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = BrandBlue)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(incident.aiSummary, style = MaterialTheme.typography.bodySmall, color = SlateTextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            incident.aiSuggestedSeverity?.let { Text("Suggested: $it", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = EmergencyRed) }
                            incident.aiRecommendedUnit?.let { Text("Unit: $it", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = InfoCyan) }
                            incident.aiSpamScore?.let { Text("Spam Risk: $it%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (it > 50) EmergencyRed else SafeEmerald) }
                        }
                    }
                }
            } else {
                TextButton(onClick = { viewModel.triggerAiTriage(incident.id) }, enabled = !isActionInProgress) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Analyze with AI", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(category = incident.category, tint = SlateTextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(incident.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${incident.locationText} ${incident.gpsLabel?.let { "($it)" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityBadge(severity = incident.severity)
                Spacer(modifier = Modifier.width(12.dp))
                if (incident.assignedTeamName != null) {
                    Text("Assigned: ${incident.assignedTeamName}", style = MaterialTheme.typography.labelMedium, color = InfoCyan)
                }
            }

            if (!incident.media.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                incident.media.firstOrNull()?.let { media ->
                    Box(modifier = Modifier.height(80.dp).width(120.dp).clip(RoundedCornerShape(8.dp))) {
                        AsyncImage(model = media.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (media.isVideo) {
                            Surface(color = BrandNavy, contentColor = Color.White, shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                                Text("VIDEO", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // Actions
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                when (incident.status) {
                    IncidentStatus.REPORTED -> {
                        TextButton(onClick = { showRejectDialog = true }, enabled = !isActionInProgress, colors = ButtonDefaults.textButtonColors(contentColor = EmergencyRed)) {
                            Text("Reject")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { showVerifyDialog = true }, enabled = !isActionInProgress, colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)) {
                            Text("Verify")
                        }
                    }
                    IncidentStatus.VERIFIED -> {
                        Button(onClick = { showAssignDialog = true }, enabled = !isActionInProgress, colors = ButtonDefaults.buttonColors(containerColor = InfoCyan)) {
                            Text("Assign Unit")
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    // Dialogs
    if (showVerifyDialog) {
        AlertDialog(
            onDismissRequest = { showVerifyDialog = false },
            title = { Text("Verify Incident") },
            text = { Text("Confirming this incident will flag it for dispatch.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.verifyIncident(incident.id, "Dispatcher")
                    showVerifyDialog = false
                }) { Text("Verify & Escalate") }
            },
            dismissButton = { TextButton(onClick = { showVerifyDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Incident") },
            text = { Text("Are you sure you want to permanently delete this rejected incident? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteIncident(incident.id)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) { Text("Delete Permanently", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRejectDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!isActionInProgress) showRejectDialog = false },
            title = { Text("Reject Incident") },
            text = {
                Column {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason for rejection") },
                        modifier = Modifier.fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AssistChip(onClick = { reason = "False Alarm" }, label = { Text("False Alarm") })
                        AssistChip(onClick = { reason = "Duplicate Report" }, label = { Text("Duplicate") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AssistChip(onClick = { reason = "Test / Prank" }, label = { Text("Test / Prank") })
                        AssistChip(onClick = { reason = "Insufficient Details" }, label = { Text("No Details") })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reason.isNotBlank()) {
                            viewModel.rejectIncident(incident.id, reason, "Dispatcher")
                            showRejectDialog = false
                        }
                    },
                    enabled = reason.isNotBlank() && !isActionInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) { 
                    if (isActionInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Reject", color = Color.White)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showRejectDialog = false }, enabled = !isActionInProgress) { Text("Cancel") } }
        )
    }

    if (showAssignDialog) {
        LaunchedEffect(incident.id) {
            viewModel.fetchRecommendedTeams(incident.id)
        }
        val recTeams by viewModel.recommendedTeams.collectAsState()
        val displayTeams = if (recTeams.isNotEmpty()) recTeams else teams.map { 
            RankedTeam(it.id, it.name, it.unitType, it.baseLocation, it.isAvailable, it.resolvedCount, null, it.unitType == incident.category.recommendedUnitType, it.lat, it.lng)
        }

        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = { Text("Assign Response Unit") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("Recommended: ${incident.category.recommendedUnitType}", style = MaterialTheme.typography.labelMedium, color = InfoCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(displayTeams) { team ->
                        val isRecommended = team.isRecommended
                        val color = if (team.isAvailable) LightSurface else LightSurfaceVariant
                        Card(
                            onClick = {
                                if (team.isAvailable) {
                                    viewModel.assignTeam(incident.id, team.id, "Dispatcher")
                                    showAssignDialog = false
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = color),
                            border = CardDefaults.outlinedCardBorder(true)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(team.name, fontWeight = FontWeight.Bold, color = if (team.isAvailable) SlateTextPrimary else SlateTextSecondary)
                                    val distLabel = if (team.distanceKm != null) "${team.distanceKm} km • " else ""
                                    Text("$distLabel${team.unitType}", style = MaterialTheme.typography.bodySmall, color = if (isRecommended) InfoCyan else SlateTextSecondary)
                                }
                                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (team.isAvailable) SafeEmerald else EmergencyRed))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAssignDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AuditLogCard(log: AuditEntry) {
    val dateFormat = SimpleDateFormat("HH:mm:ss · MMM dd", Locale.getDefault())
    val isRed = log.action == "ACCESS_DENIED_403" || log.action == "REJECT"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(true)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(log.incidentId, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = BrandBlue)
                Text(dateFormat.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (isRed) EmergencyRedBg else LightSurfaceVariant,
                    contentColor = if (isRed) EmergencyRed else SlateTextPrimary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(log.action, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("by ${log.actorRole}", style = MaterialTheme.typography.labelSmall, color = SlateTextSecondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(log.details, style = MaterialTheme.typography.bodyMedium, color = SlateTextPrimary)
        }
    }
}
