package com.rapidresq.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rapidresq.app.ui.components.DemoModeBanner
import com.rapidresq.app.ui.screens.dispatcher.AuditLogCard
import com.rapidresq.app.ui.screens.dispatcher.KpiCard
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: RapidResQViewModel,
    onBackToRoles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.incidents.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val staffAccounts by viewModel.staffAccounts.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var showAddUnitDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(LightBackground)) {
        DemoModeBanner()
        TopAppBar(
            title = { Text("Admin Governance", fontWeight = FontWeight.Bold, color = BrandNavy) },
            navigationIcon = {
                IconButton(onClick = onBackToRoles) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandNavy)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
        )

        // KPI Summary
        val securityEventCount = auditLogs.count { it.action == "ACCESS_DENIED_403" || it.action == "REJECT" }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard("Incidents", incidents.size.toString(), Modifier.weight(1f))
            KpiCard("Units", "${teams.count { it.isAvailable }}/${teams.size}", Modifier.weight(1f), InfoCyan)
            KpiCard("Staff", staffAccounts.count { it.isActive }.toString(), Modifier.weight(1f), SafeEmerald)
            KpiCard("Alerts", securityEventCount.toString(), Modifier.weight(1f), if (securityEventCount > 0) EmergencyRed else SlateTextSecondary)
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = LightSurface,
            contentColor = BrandBlue
        ) {
            Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Units", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Personnel", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, text = { Text("Audit Log", fontWeight = FontWeight.Bold) })
        }

        when (selectedTabIndex) {
            0 -> {
                // Units Tab
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp, start = 16.dp, end = 16.dp, top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(teams) { team ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = LightSurface),
                                border = CardDefaults.outlinedCardBorder(true)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                                        Text("${team.unitType} • ${team.baseLocation}", style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                                        Text("Resolved: ${team.resolvedCount}", style = MaterialTheme.typography.labelSmall, color = InfoCyan, modifier = Modifier.padding(top = 4.dp))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Switch(
                                            checked = team.isAvailable,
                                            onCheckedChange = { viewModel.toggleTeamAvailability(team.id) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SafeEmerald)
                                        )
                                        Text(if (team.isAvailable) "Available" else "Busy", style = MaterialTheme.typography.labelSmall, color = if (team.isAvailable) SafeEmerald else SlateTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                    ExtendedFloatingActionButton(
                        onClick = { showAddUnitDialog = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        containerColor = BrandBlue,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Response Unit", fontWeight = FontWeight.Bold)
                    }
                }
            }
            1 -> {
                // Personnel Tab
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(staffAccounts) { staff ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = LightSurface),
                            border = CardDefaults.outlinedCardBorder(true)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(staff.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(color = BrandBlueLight, contentColor = BrandBlue, shape = RoundedCornerShape(4.dp)) {
                                            Text(staff.role.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Text(staff.email, style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                                    Text(staff.department + (staff.assignedUnitName?.let { " • $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary, modifier = Modifier.padding(top = 4.dp))
                                    if (staff.mfaEnabled) {
                                        Text("MFA: OTP", style = MaterialTheme.typography.labelSmall, color = SafeEmerald, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                                Switch(
                                    checked = staff.isActive,
                                    onCheckedChange = { viewModel.toggleStaffActive(staff.id) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBlue)
                                )
                            }
                        }
                    }
                }
            }
            2 -> {
                // Audit Log Tab
                var searchLogQuery by remember { mutableStateOf("") }
                var selectedLogFilter by remember { mutableStateOf("All") }

                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    OutlinedTextField(
                        value = searchLogQuery,
                        onValueChange = { searchLogQuery = it },
                        placeholder = { Text("Search logs (ID, Action, Role)...") },
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
                    ScrollableTabRow(
                        selectedTabIndex = listOf("All", "Security / 403", "Dispatch", "Admin").indexOf(selectedLogFilter),
                        containerColor = LightBackground,
                        contentColor = BrandBlue,
                        edgePadding = 0.dp,
                        modifier = Modifier.padding(vertical = 12.dp),
                        indicator = {}
                    ) {
                        listOf("All", "Security / 403", "Dispatch", "Admin").forEach { filter ->
                            val isSelected = selectedLogFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedLogFilter = filter },
                                label = { Text(filter, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.padding(end = 8.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandBlueLight, selectedLabelColor = BrandBlue)
                            )
                        }
                    }

                    val filteredLogs = auditLogs.filter { log ->
                        val matchesSearch = searchLogQuery.isBlank() || 
                            log.incidentId.contains(searchLogQuery, ignoreCase = true) ||
                            log.action.contains(searchLogQuery, ignoreCase = true) ||
                            log.actorRole.contains(searchLogQuery, ignoreCase = true)
                        
                        val matchesFilter = when (selectedLogFilter) {
                            "Security / 403" -> log.action == "ACCESS_DENIED_403" || log.action == "REJECT"
                            "Dispatch" -> log.actorRole == "Dispatcher"
                            "Admin" -> log.actorRole == "Administrator"
                            else -> true
                        }
                        
                        matchesSearch && matchesFilter
                    }.sortedByDescending { it.timestamp }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filteredLogs) { log ->
                            AuditLogCard(log)
                        }
                    }
                }
            }
        }
    }

    if (showAddUnitDialog) {
        var newUnitName by remember { mutableStateOf("") }
        var newUnitType by remember { mutableStateOf("") }
        var newBaseLocation by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddUnitDialog = false },
            title = { Text("Add Response Unit", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newUnitName,
                        onValueChange = { newUnitName = it },
                        label = { Text("Unit Name") },
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
                    OutlinedTextField(
                        value = newUnitType,
                        onValueChange = { newUnitType = it },
                        label = { Text("Type (e.g., Medical EMS)") },
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
                    OutlinedTextField(
                        value = newBaseLocation,
                        onValueChange = { newBaseLocation = it },
                        label = { Text("Base Location") },
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUnitName.isNotBlank() && newUnitType.isNotBlank() && newBaseLocation.isNotBlank()) {
                            viewModel.addResponseTeam(newUnitName, newUnitType, newBaseLocation)
                            showAddUnitDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) { Text("Create Unit", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showAddUnitDialog = false }) { Text("Cancel") }
            }
        )
    }
}
