package com.rapidresq.app.ui.screens.citizen

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rapidresq.app.data.model.EmergencyCategory
import com.rapidresq.app.data.model.IncidentSeverity
import com.rapidresq.app.data.model.MediaAttachment
import com.rapidresq.app.ui.components.CategoryIcon
import com.rapidresq.app.ui.components.MapPin
import com.rapidresq.app.ui.components.MapPinType
import com.rapidresq.app.ui.components.RapidResQMapCard
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEmergencyScreen(
    viewModel: RapidResQViewModel,
    onBack: () -> Unit,
    onReportSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSubmitting by viewModel.isSubmittingReport.collectAsState()
    
    var selectedCategory by remember { mutableStateOf<EmergencyCategory?>(null) }
    var locationText by remember { mutableStateOf("") }
    var gpsLabel by remember { mutableStateOf<String?>(null) }
    var selectedLat by remember { mutableStateOf<Double?>(null) }
    var selectedLng by remember { mutableStateOf<Double?>(null) }
    var selectedSeverity by remember { mutableStateOf(IncidentSeverity.HIGH) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reporterContact by remember { mutableStateOf("") }
    var attachedMedia by remember { mutableStateOf<MediaAttachment?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val type = context.contentResolver.getType(it)
            val isVideo = type?.startsWith("video") == true
            attachedMedia = MediaAttachment(uri = it.toString(), isVideo = isVideo)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.captureLiveGps { res ->
                if (res != null) {
                    selectedLat = res.lat
                    selectedLng = res.lng
                    locationText = res.displayAddress
                    gpsLabel = String.format(Locale.US, "%.4f° N, %.4f° E (Live GPS)", res.lat, res.lng)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        TopAppBar(
            title = { Text("Report Emergency", fontWeight = FontWeight.Bold, color = BrandNavy) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandNavy)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Category Selection (2x2 Grid approximated)
            item {
                Text("1. What is the emergency?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                val categories = EmergencyCategory.entries
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategorySelectCard(
                            category = categories[0],
                            isSelected = selectedCategory == categories[0],
                            onClick = { selectedCategory = categories[0] },
                            modifier = Modifier.weight(1f)
                        )
                        CategorySelectCard(
                            category = categories[1],
                            isSelected = selectedCategory == categories[1],
                            onClick = { selectedCategory = categories[1] },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategorySelectCard(
                            category = categories[2],
                            isSelected = selectedCategory == categories[2],
                            onClick = { selectedCategory = categories[2] },
                            modifier = Modifier.weight(1f)
                        )
                        CategorySelectCard(
                            category = categories[3],
                            isSelected = selectedCategory == categories[3],
                            onClick = { selectedCategory = categories[3] },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Location
            item {
                Text("2. Where is it?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = locationText,
                    onValueChange = { locationText = it },
                    label = { Text("Location description") },
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
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Live GPS")
                    }

                    Button(
                        onClick = {
                            selectedLat = 22.8235
                            selectedLng = 75.9432
                            locationText = "Near Acropolis Campus, Manglia, Indore"
                            gpsLabel = "22.8235° N, 75.9432° E (Demo GPS)"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LightSurfaceVariant, contentColor = BrandBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Demo GPS")
                    }
                }

                if (gpsLabel != null) {
                    Text(
                        text = "GPS: $gpsLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeEmerald,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Map View Composable
                val mapPins = remember(selectedLat, selectedLng) {
                    if (selectedLat != null && selectedLng != null) {
                        listOf(
                            MapPin(
                                id = "INCIDENT_PIN",
                                title = "Incident Location",
                                snippet = locationText.ifBlank { "Selected Pin" },
                                lat = selectedLat!!,
                                lng = selectedLng!!,
                                pinType = MapPinType.SELECTED_LOCATION
                            )
                        )
                    } else emptyList()
                }

                RapidResQMapCard(
                    pins = mapPins,
                    centerLat = selectedLat ?: 22.7196,
                    centerLng = selectedLng ?: 75.8577,
                    zoom = if (selectedLat != null) 15.0 else 12.5,
                    height = 200.dp,
                    onMapTap = { tapLat, tapLng ->
                        selectedLat = tapLat
                        selectedLng = tapLng
                        gpsLabel = String.format(Locale.US, "%.4f° N, %.4f° E (Map Pin)", tapLat, tapLng)
                        if (locationText.isBlank()) {
                            locationText = String.format(Locale.US, "Pin at %.4f, %.4f", tapLat, tapLng)
                        }
                    }
                )
            }

            // Severity
            item {
                Text("3. Severity level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IncidentSeverity.entries.forEach { severity ->
                        val isSelected = selectedSeverity == severity
                        val bgColor = if (isSelected) BrandBlue else LightSurfaceVariant
                        val textColor = if (isSelected) Color.White else SlateTextPrimary
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = bgColor,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedSeverity = severity }
                        ) {
                            Text(
                                text = severity.name,
                                color = textColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Details
            item {
                Text("4. Details (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Short title") },
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Describe the situation") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    maxLines = 4,
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
                OutlinedTextField(
                    value = reporterContact,
                    onValueChange = { reporterContact = it },
                    label = { Text("Your Contact Number") },
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
            }

            // Media
            item {
                Text("5. Photo / Video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                if (attachedMedia == null) {
                    Card(
                        onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant),
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tap to attach media", color = SlateTextSecondary)
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))) {
                        AsyncImage(
                            model = attachedMedia!!.uri,
                            contentDescription = "Attached Media",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (attachedMedia!!.isVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                            }
                            Surface(
                                color = BrandNavy,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            ) {
                                Text("VIDEO", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        IconButton(
                            onClick = { attachedMedia = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                .size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Submit Button
            item {
                Button(
                    onClick = {
                        if (selectedCategory != null && locationText.isNotBlank() && !isSubmitting) {
                            val id = viewModel.submitIncident(
                                category = selectedCategory!!,
                                title = title.ifBlank { "Emergency Report" },
                                description = description,
                                locationText = locationText,
                                gpsLabel = gpsLabel,
                                severity = selectedSeverity,
                                reporterContact = reporterContact,
                                media = attachedMedia?.let { listOf(it) },
                                lat = selectedLat,
                                lng = selectedLng
                            )
                            if (id != null) {
                                onReportSubmitted(id)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyRed,
                        disabledContainerColor = LightSurfaceVariant
                    ),
                    enabled = selectedCategory != null && locationText.isNotBlank() && !isSubmitting
                ) {
                    if (isSubmitting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BrandBlue, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Submitting Report...", color = BrandBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Text("Submit Emergency Report", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CategorySelectCard(
    category: EmergencyCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) BrandBlueLight else LightSurface
    val borderColor = if (isSelected) BrandBlue else SubtleBorder

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = CardDefaults.outlinedCardBorder(true).copy(brush = SolidColor(borderColor))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CategoryIcon(
                category = category,
                tint = if (isSelected) BrandBlue else SlateTextSecondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = category.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) BrandBlue else SlateTextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}
