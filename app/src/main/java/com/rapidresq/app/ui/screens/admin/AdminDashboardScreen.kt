package com.rapidresq.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rapidresq.app.data.model.IncidentStatus
import com.rapidresq.app.data.model.StaffAccount
import com.rapidresq.app.data.model.UserRole
import com.rapidresq.app.ui.components.AiAssistantSheet
import com.rapidresq.app.ui.components.DemoModeBanner
import com.rapidresq.app.ui.components.RapidResQCommandTopBar
import com.rapidresq.app.ui.components.rapidResQTextFieldColors
import com.rapidresq.app.ui.screens.dispatcher.AuditLogCard
import com.rapidresq.app.ui.screens.dispatcher.DispatcherIncidentCard
import com.rapidresq.app.ui.screens.dispatcher.KpiCard
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: RapidResQViewModel,
    onBackToRoles: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.incidents.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val staffAccounts by viewModel.staffAccounts.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val session by viewModel.currentSession.collectAsState()
    val isCloudConnected by viewModel.isCloudConnected.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val auditChainStatus by viewModel.auditChainStatus.collectAsState()
    val analyticsSummary by viewModel.analyticsSummary.collectAsState()
    val isActionInProgress by viewModel.isActionInProgress.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var showAddUnitDialog by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var showAddStaffDialog by remember { mutableStateOf(false) }
    var editingStaff by remember { mutableStateOf<StaffAccount?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 4) {
            viewModel.loadAnalytics()
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
            RapidResQCommandTopBar(
                title = "Admin Governance",
                subtitle = session?.name ?: "",
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
                        text = "Offline Mode • $pendingCount action(s) queued locally",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
                    )
                }
            }

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
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = LightSurface,
                contentColor = BrandBlue,
                edgePadding = 0.dp,
                divider = {}
            ) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Incidents", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Units", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, text = { Text("Personnel", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTabIndex == 3, onClick = { selectedTabIndex = 3 }, text = { Text("Audit Log", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTabIndex == 4, onClick = { selectedTabIndex = 4 }, text = { Text("Analytics", fontWeight = FontWeight.Bold) })
            }

            when (selectedTabIndex) {
                0 -> {
                    // Incidents Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(incidents.sortedByDescending { it.createdAt }) { incident ->
                            DispatcherIncidentCard(incident, teams, viewModel, isActionInProgress)
                        }
                    }
                }
                1 -> {
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
                                                enabled = !isActionInProgress,
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
                            Text("Add Unit", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                2 -> {
                    // Personnel Tab
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp, start = 16.dp, end = 16.dp, top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(staffAccounts) { staff ->
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
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Switch(
                                                    checked = staff.isActive,
                                                    onCheckedChange = { viewModel.toggleStaffActive(staff.id) },
                                                    enabled = !isActionInProgress,
                                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBlue)
                                                )
                                                IconButton(onClick = { editingStaff = staff }, enabled = !isActionInProgress) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit Role", tint = BrandBlue)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        ExtendedFloatingActionButton(
                            onClick = { showAddStaffDialog = true },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                            containerColor = BrandBlue,
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Staff", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                3 -> {
                    // Audit Log Tab
                    var searchLogQuery by remember { mutableStateOf("") }
                    var selectedLogFilter by remember { mutableStateOf("All") }

                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Button(
                            onClick = { viewModel.verifyAuditIntegrity() },
                            enabled = !isActionInProgress,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy)
                        ) {
                            if (isActionInProgress) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify SHA-256 Chain Integrity", fontWeight = FontWeight.Bold)
                        }

                        if (auditChainStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val isOk = auditChainStatus!!.valid
                            Surface(
                                color = if (isOk) SafeEmeraldBg else EmergencyRedBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (isOk) "SHA-256 Audit Chain VERIFIED (Tamper-Free)" else "TAMPER DETECTED IN AUDIT CHAIN!",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOk) SafeEmerald else EmergencyRed
                                    )
                                    Text(
                                        text = "Checked ${auditChainStatus!!.checkedChainedLogs} records. Latest Hash: ${auditChainStatus!!.latestHash?.take(16)}...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOk) SafeEmerald else EmergencyRed
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
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
                            divider = {}
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
                4 -> {
                    // Analytics Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text("Live Operational Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            if (analyticsSummary == null) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = BrandBlue)
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        KpiCard("Total", analyticsSummary!!.totalIncidents.toString(), Modifier.weight(1f))
                                        KpiCard("Active", analyticsSummary!!.activeIncidents.toString(), Modifier.weight(1f), WarningAmber)
                                        KpiCard("Resolved", analyticsSummary!!.resolvedIncidents.toString(), Modifier.weight(1f), SafeEmerald)
                                        KpiCard("Rejected", analyticsSummary!!.rejectedIncidents.toString(), Modifier.weight(1f), EmergencyRed)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Incident Categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    analyticsSummary!!.byCategory.forEach { (cat, count) ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(cat, style = MaterialTheme.typography.bodyMedium)
                                            Text(count.toString(), fontWeight = FontWeight.Bold, color = BrandBlue)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Top Response Units by Resolution Count", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    analyticsSummary!!.topUnits.forEach { unit ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = LightSurface)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(unit.name, fontWeight = FontWeight.Bold)
                                                    Text(unit.unitType, style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                                                }
                                                Text("Resolved: ${unit.resolvedCount}", fontWeight = FontWeight.Bold, color = InfoCyan)
                                            }
                                        }
                                    }
                                }
                            }
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
            onDismissRequest = { if (!isActionInProgress) showAddUnitDialog = false },
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
                    enabled = !isActionInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) { Text("Create Unit", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showAddUnitDialog = false }, enabled = !isActionInProgress) { Text("Cancel") }
            }
        )
    }

    if (showAddStaffDialog) {
        var sName by remember { mutableStateOf("") }
        var sEmail by remember { mutableStateOf("") }
        var sPhone by remember { mutableStateOf("") }
        var sPass by remember { mutableStateOf("") }
        var sRole by remember { mutableStateOf("DISPATCHER") }
        var sDept by remember { mutableStateOf("") }
        var sTeamId by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isActionInProgress) showAddStaffDialog = false },
            title = { Text("Create Staff Account", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(
                            value = sName,
                            onValueChange = { sName = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = rapidResQTextFieldColors()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = sEmail,
                            onValueChange = { sEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = rapidResQTextFieldColors()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = sPass,
                            onValueChange = { sPass = it },
                            label = { Text("Password (8+ chars, upper, lower, digit)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = rapidResQTextFieldColors()
                        )
                    }
                    item {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AssistChip(onClick = { sRole = "DISPATCHER"; sDept = "Command Center" }, label = { Text("Dispatcher") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (sRole == "DISPATCHER") BrandBlueLight else Color.Transparent))
                            AssistChip(onClick = { sRole = "RESPONDER"; sDept = "" }, label = { Text("Responder") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (sRole == "RESPONDER") BrandBlueLight else Color.Transparent))
                            AssistChip(onClick = { sRole = "ADMIN"; sDept = "Governance" }, label = { Text("Admin") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (sRole == "ADMIN") BrandBlueLight else Color.Transparent))
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = sDept,
                            onValueChange = { sDept = it },
                            label = { Text("Department") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = rapidResQTextFieldColors()
                        )
                    }
                    if (sRole == "RESPONDER") {
                        item {
                            Text("Assign Unit:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            teams.forEach { t ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = sTeamId == t.id, onClick = { sTeamId = t.id })
                                    Text(t.name, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sName.isNotBlank() && sEmail.isNotBlank() && sPass.isNotBlank()) {
                            viewModel.createStaffMember(sName, sEmail, sPhone, sPass, sRole, sDept, sTeamId)
                            showAddStaffDialog = false
                        }
                    },
                    enabled = !isActionInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) { Text("Create", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showAddStaffDialog = false }, enabled = !isActionInProgress) { Text("Cancel") }
            }
        )
    }

    if (editingStaff != null) {
        var sRole by remember { mutableStateOf(editingStaff!!.role.name) }
        var sDept by remember { mutableStateOf(editingStaff!!.department) }
        var sTeamId by remember { mutableStateOf(editingStaff!!.assignedUnitName) }

        AlertDialog(
            onDismissRequest = { if (!isActionInProgress) editingStaff = null },
            title = { Text("Edit Role: ${editingStaff!!.name}", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AssistChip(onClick = { sRole = "CITIZEN" }, label = { Text("Citizen") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (sRole == "CITIZEN") BrandBlueLight else Color.Transparent))
                            AssistChip(onClick = { sRole = "DISPATCHER" }, label = { Text("Dispatcher") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (sRole == "DISPATCHER") BrandBlueLight else Color.Transparent))
                            AssistChip(onClick = { sRole = "RESPONDER" }, label = { Text("Responder") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (sRole == "RESPONDER") BrandBlueLight else Color.Transparent))
                            AssistChip(onClick = { sRole = "ADMIN" }, label = { Text("Admin") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (sRole == "ADMIN") BrandBlueLight else Color.Transparent))
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = sDept,
                            onValueChange = { sDept = it },
                            label = { Text("Department") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = rapidResQTextFieldColors()
                        )
                    }
                    if (sRole == "RESPONDER") {
                        item {
                            Text("Assign Unit:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            teams.forEach { t ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = sTeamId == t.id, onClick = { sTeamId = t.id })
                                    Text(t.name, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateStaffRole(editingStaff!!.id, sRole, sDept, sTeamId)
                        editingStaff = null
                    },
                    enabled = !isActionInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { editingStaff = null }, enabled = !isActionInProgress) { Text("Cancel") }
            }
        )
    }

    if (showAiSheet) {
        AiAssistantSheet(viewModel = viewModel) {
            showAiSheet = false
        }
    }
}
