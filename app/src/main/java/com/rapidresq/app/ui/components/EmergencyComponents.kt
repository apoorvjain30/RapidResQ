package com.rapidresq.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rapidresq.app.data.model.EmergencyCategory
import com.rapidresq.app.data.model.IncidentSeverity
import com.rapidresq.app.data.model.IncidentStatus
import com.rapidresq.app.data.model.TimelineEntry
import com.rapidresq.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DemoModeBanner(text: String = "Phase 1 Prototype · Local Demo State") {
    Surface(
        color = BrandNavy,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .wrapContentWidth(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun SeverityBadge(severity: IncidentSeverity, modifier: Modifier = Modifier) {
    val (bgColor, contentColor) = when (severity) {
        IncidentSeverity.CRITICAL -> EmergencyRedBg to EmergencyRed
        IncidentSeverity.HIGH -> WarningAmberBg to WarningAmber
        IncidentSeverity.MEDIUM -> InfoCyanBg to InfoCyan
        IncidentSeverity.LOW -> SafeEmeraldBg to SafeEmerald
    }

    Surface(
        color = bgColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Text(
            text = severity.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusBadge(status: IncidentStatus, modifier: Modifier = Modifier) {
    val (bgColor, contentColor) = when (status) {
        IncidentStatus.REJECTED -> EmergencyRedBg to EmergencyRed
        IncidentStatus.RESOLVED -> SafeEmeraldBg to SafeEmerald
        IncidentStatus.EN_ROUTE, IncidentStatus.ON_SITE, IncidentStatus.RESPONDING -> WarningAmberBg to WarningAmber
        IncidentStatus.VERIFIED, IncidentStatus.ASSIGNED, IncidentStatus.ACCEPTED -> InfoCyanBg to InfoCyan
        IncidentStatus.REPORTED -> LightSurfaceVariant to SlateTextSecondary
    }

    Surface(
        color = bgColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        val displayStatus = status.name.lowercase().split("_").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        Text(
            text = displayStatus,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CategoryIcon(category: EmergencyCategory, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val icon: ImageVector = when (category) {
        EmergencyCategory.ROAD_ACCIDENT -> Icons.Default.Warning // DirectionsCar or Warning
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
fun IncidentTimelineCard(timeline: List<TimelineEntry>, modifier: Modifier = Modifier) {
    val dateFormat = SimpleDateFormat("HH:mm · MMM dd", Locale.getDefault())

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Incident Timeline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (timeline.isEmpty()) {
                Text(
                    text = "No timeline events yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateTextSecondary
                )
            } else {
                timeline.sortedByDescending { it.timestamp }.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        // Timeline Line/Dot
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(24.dp).padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (index == 0) BrandBlue else SlateTextSecondary)
                            )
                            if (index < timeline.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(32.dp)
                                        .background(SubtleBorder)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Timeline Content
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.statusLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (index == 0) SlateTextPrimary else SlateTextSecondary
                                )
                                Text(
                                    text = dateFormat.format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateTextSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Text(
                                text = "By ${entry.actor}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextSecondary
                            )
                            if (!entry.note.isNullOrBlank()) {
                                Text(
                                    text = entry.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextPrimary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
