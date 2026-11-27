package com.rapidresq.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidresq.app.data.model.EmergencyCategory
import com.rapidresq.app.data.model.IncidentSeverity
import com.rapidresq.app.data.model.IncidentStatus
import com.rapidresq.app.data.model.TimelineEntry
import com.rapidresq.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RapidResQLogoMark(size: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(BrandNavy)
            .border(width = (size * 0.05f), color = BrandNavyLight, shape = RoundedCornerShape(size * 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.6f)
                .clip(RoundedCornerShape(size * 0.15f))
                .background(EmergencyRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MedicalServices,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.35f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rapidResQTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandBlue,
    unfocusedBorderColor = InputBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = BrandBlue,
    unfocusedLabelColor = TextMuted,
    cursorColor = BrandBlue
)

@Composable
fun SemanticStatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun KpiMetricTile(
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(72.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = CardDefaults.outlinedCardBorder(true).copy(width = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(accentColor))
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = label.uppercase(Locale.getDefault()),
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RapidResQCommandTopBar(
    title: String,
    subtitle: String,
    isCloudConnected: Boolean,
    pendingOfflineCount: Int = 0,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    onAiClick: (() -> Unit)? = null,
    onLogoutClick: (() -> Unit)? = null
) {
    Surface(
        color = SurfaceCard,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandNavy)
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isCloudConnected) EmeraldGreen else AmberWarn)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val statusText = if (pendingOfflineCount > 0) "$subtitle (Offline: $pendingOfflineCount queued)" else subtitle
                        Text(
                            text = statusText,
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    if (onAiClick != null) {
                        Surface(
                            color = AiIndigoSoft,
                            shape = RoundedCornerShape(17.dp),
                            modifier = Modifier.height(34.dp),
                            onClick = onAiClick
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = AiIndigo, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AI", color = AiIndigo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (onLogoutClick != null) {
                        Surface(
                            color = LightSurfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp),
                            onClick = onLogoutClick
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
        }
    }
}

// ---------------------------------------------------------
// Legacy / Shared Components (Kept for compatibility)
// ---------------------------------------------------------

@Composable
fun DemoModeBanner(
    isCloudConnected: Boolean = true,
    text: String? = null
) {
    val bannerText = text ?: if (isCloudConnected) "Live Cloud Sync" else "Offline Mode"
    val bgColor = if (isCloudConnected) EmeraldGreen else AmberWarn

    Surface(
        color = bgColor,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = bannerText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .wrapContentWidth(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun SeverityBadge(severity: IncidentSeverity, modifier: Modifier = Modifier) {
    val (bgColor, contentColor) = when (severity) {
        IncidentSeverity.CRITICAL -> EmergencyRedSoft to EmergencyRed
        IncidentSeverity.HIGH -> AmberSoft to AmberWarn
        IncidentSeverity.MEDIUM -> BrandBlueLight to BrandBlue
        IncidentSeverity.LOW -> EmeraldSoft to EmeraldGreen
    }
    SemanticStatusPill(text = severity.name, containerColor = bgColor, contentColor = contentColor, modifier = modifier)
}

@Composable
fun StatusBadge(status: IncidentStatus, modifier: Modifier = Modifier) {
    val (bgColor, contentColor) = when (status) {
        IncidentStatus.REJECTED -> EmergencyRedSoft to EmergencyRed
        IncidentStatus.RESOLVED -> EmeraldSoft to EmeraldGreen
        IncidentStatus.EN_ROUTE, IncidentStatus.ON_SITE, IncidentStatus.RESPONDING -> AmberSoft to AmberWarn
        IncidentStatus.VERIFIED, IncidentStatus.ASSIGNED, IncidentStatus.ACCEPTED -> BrandBlueLight to BrandBlue
        IncidentStatus.REPORTED -> LightSurfaceVariant to TextSecondary
    }
    val displayStatus = status.name.lowercase().split("_").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    SemanticStatusPill(text = displayStatus, containerColor = bgColor, contentColor = contentColor, modifier = modifier)
}

@Composable
fun CategoryIcon(category: EmergencyCategory, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val icon: ImageVector = when (category) {
        EmergencyCategory.ROAD_ACCIDENT -> Icons.Default.Warning
        EmergencyCategory.FIRE_HAZARD -> Icons.Default.LocalFireDepartment
        EmergencyCategory.MEDICAL -> Icons.Default.MedicalServices
        EmergencyCategory.PUBLIC_SAFETY -> Icons.Default.Security
    }
    Icon(
        imageVector = icon,
        contentDescription = category.label,
        modifier = modifier,
        tint = tint
    )
}

@Composable
fun IncidentTimelineCard(timeline: List<TimelineEntry>) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = CardDefaults.outlinedCardBorder(true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Incident Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))

            timeline.forEachIndexed { index, entry ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Left Timeline Track
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (index == timeline.size - 1) BrandBlue else CardBorder)
                        )
                        if (index < timeline.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(48.dp)
                                    .background(CardBorder)
                            )
                        }
                    }

                    // Content
                    Column(modifier = Modifier.weight(1f).padding(bottom = if (index < timeline.size - 1) 16.dp else 0.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.statusLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(timeFormat.format(Date(entry.timestamp)), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Text("by ${entry.actor}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        if (entry.note != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(color = LightSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                Text(entry.note, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
