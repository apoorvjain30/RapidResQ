package com.rapidresq.app.ui.screens.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidresq.app.data.model.Incident
import com.rapidresq.app.ui.components.AiAssistantSheet
import com.rapidresq.app.ui.components.CategoryIcon
import com.rapidresq.app.ui.components.RapidResQCommandTopBar
import com.rapidresq.app.ui.components.SemanticStatusPill
import com.rapidresq.app.ui.components.SeverityBadge
import com.rapidresq.app.ui.components.StatusBadge
import com.rapidresq.app.ui.components.rapidResQTextFieldColors
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenHomeScreen(
    viewModel: RapidResQViewModel,
    onBackToRoles: () -> Unit,
    onReportClick: () -> Unit,
    onTrackIncident: (String) -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.incidents.collectAsState()
    val session by viewModel.currentSession.collectAsState()
    val isCloudConnected by viewModel.isCloudConnected.collectAsState()
    
    var trackId by remember { mutableStateOf("") }
    var showAiSheet by remember { mutableStateOf(false) }
    
    val titleText = if (session?.isGuest == true) "Guest SOS Mode" else "Citizen Portal"
    val subtitleText = if (session?.isGuest == true) "No account tracking" else session?.name ?: ""

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasBg,
        topBar = {
            RapidResQCommandTopBar(
                title = titleText,
                subtitle = subtitleText,
                isCloudConnected = isCloudConnected,
                showBack = true,
                canGoBack = false,
                onBack = onBackToRoles,
                onAiClick = { showAiSheet = true },
                onLogoutClick = { viewModel.logout(); onLogout() }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Crimson Emergency Hero Card
            item {
                Card(
                    onClick = onReportClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(EmergencyRed, Color(0xFFB91C1C))))
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Report an Emergency",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Share GPS, category & photo/video in seconds",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Surface(
                                color = Color.White,
                                contentColor = EmergencyRed,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("Report Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Track Incident Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = CardDefaults.outlinedCardBorder(true).copy(width = 1.dp, brush = SolidColor(CardBorder)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Track Existing Incident",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = trackId,
                                onValueChange = { trackId = it.toUpperCase(Locale.current) },
                                placeholder = { Text("RRQ-XXXXXX") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = rapidResQTextFieldColors(),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { if (trackId.isNotBlank()) onTrackIncident(trackId) },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                modifier = Modifier.height(56.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
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
                    text = "Active & Recent Incidents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
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
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }

    if (showAiSheet) {
        AiAssistantSheet(viewModel = viewModel, incidentId = null) {
            showAiSheet = false
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
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = CardDefaults.outlinedCardBorder(true).copy(width = 1.dp, brush = SolidColor(CardBorder)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
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
                    shape = CircleShape,
                    color = LightSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CategoryIcon(category = incident.category, tint = TextPrimary)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = incident.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = incident.locationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SeverityBadge(severity = incident.severity)
        }
    }
}
