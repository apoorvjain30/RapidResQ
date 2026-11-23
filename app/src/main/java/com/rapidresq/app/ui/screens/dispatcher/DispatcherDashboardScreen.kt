package com.rapidresq.app.ui.screens.dispatcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.rapidresq.app.ui.components.CategoryIcon
import com.rapidresq.app.ui.components.DemoModeBanner
import com.rapidresq.app.ui.components.SeverityBadge
import com.rapidresq.app.ui.components.StatusBadge
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatcherDashboardScreen(
    viewModel: RapidResQViewModel,
    onBackToRoles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.incidents.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var selectedFilter by remember { mutableStateOf("All") }

    Column(modifier = modifier.fillMaxSize().background(LightBackground)) {
        DemoModeBanner()
        TopAppBar(
            title = { Text("Dispatcher Command", fontWeight = FontWeight.Bold, color = BrandNavy) },
            navigationIcon = {
                IconButton(onClick = onBackToRoles) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandNavy)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
        )

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

        // Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = LightSurface,
            contentColor = BrandBlue
        ) {
            Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Incidents", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Audit Log", fontWeight = FontWeight.Bold) })
        }

        if (selectedTabIndex == 0) {
            // Incidents Tab
            ScrollableTabRow(
                selectedTabIndex = listOf("All", "Pending", "Verified", "Active", "Closed").indexOf(selectedFilter),
                containerColor = LightBackground,
                contentColor = BrandBlue,
                edgePadding = 16.dp,
                modifier = Modifier.padding(vertical = 8.dp),
                indicator = {}
            ) {
                listOf("All", "Pending", "Verified", "Active", "Closed").forEach { filter ->
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
                    "Pending" -> it.status == IncidentStatus.REPORTED
                    "Verified" -> it.status == IncidentStatus.VERIFIED
                    "Active" -> it.status !in listOf(IncidentStatus.REPORTED, IncidentStatus.RESOLVED, IncidentStatus.REJECTED)
                    "Closed" -> it.status in listOf(IncidentStatus.RESOLVED, IncidentStatus.REJECTED)
                    else -> true
                }
            }.sortedByDescending { it.createdAt }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredIncidents) { incident ->
                    DispatcherIncidentCard(incident, teams, viewModel)
                }
            }
        } else {
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
    viewModel: RapidResQViewModel
) {
    var showVerifyDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(incident.id, style = MaterialTheme.typography.titleMedium, color = BrandBlue, fontWeight = FontWeight.ExtraBold)
                StatusBadge(status = incident.status)
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
                        TextButton(onClick = { showRejectDialog = true }, colors = ButtonDefaults.textButtonColors(contentColor = EmergencyRed)) {
                            Text("Reject")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { showVerifyDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)) {
                            Text("Verify")
                        }
                    }
                    IncidentStatus.VERIFIED -> {
                        Button(onClick = { showAssignDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = InfoCyan)) {
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

    if (showRejectDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject Incident") },
            text = {
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reason.isNotBlank()) {
                            viewModel.rejectIncident(incident.id, reason, "Dispatcher")
                            showRejectDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) { Text("Reject", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showRejectDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAssignDialog) {
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = { Text("Assign Response Unit") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("Recommended: ${incident.category.recommendedUnitType}", style = MaterialTheme.typography.labelMedium, color = InfoCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(teams) { team ->
                        val isRecommended = team.unitType == incident.category.recommendedUnitType
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
                                    Text(team.unitType, style = MaterialTheme.typography.bodySmall, color = if (isRecommended) InfoCyan else SlateTextSecondary)
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
