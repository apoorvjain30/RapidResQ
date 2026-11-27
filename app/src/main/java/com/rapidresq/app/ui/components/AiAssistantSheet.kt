package com.rapidresq.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
    viewModel: RapidResQViewModel,
    incidentId: String? = null,
    onDismiss: () -> Unit
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isAiThinking by viewModel.isAiThinking.collectAsState()
    val scopeLabel by viewModel.aiScopeLabel.collectAsState()
    val session by viewModel.currentSession.collectAsState()

    var inputMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val userRole = session?.role?.name ?: "CITIZEN"

    val suggestions = when (userRole) {
        "CITIZEN" -> listOf(
            "What is my incident status?",
            "Safety steps while waiting",
            "When will help arrive?"
        )
        "DISPATCHER" -> listOf(
            "Summarize active emergencies",
            "Which incident is highest priority?",
            "Which units are available?"
        )
        "RESPONDER" -> listOf(
            "Summarize my active assignment",
            "On-site safety checklist",
            "What status comes next?"
        )
        "ADMIN" -> listOf(
            "Summarize system security & 403s",
            "How is unit workload distributed?",
            "Check audit chain status"
        )
        else -> listOf("How can I help you?", "Emergency procedures", "System status")
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LightSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BrandBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RapidResQ AI Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = BrandNavy)
                }
                IconButton(onClick = { viewModel.clearAiChat() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = SlateTextSecondary)
                }
            }

            // Scope Badge
            Surface(
                color = BrandBlueLight,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Text(
                    text = "🔒 $scopeLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Suggestion Chips
            if (chatMessages.isEmpty()) {
                Text("Suggested Queries:", style = MaterialTheme.typography.labelMedium, color = SlateTextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.take(2).forEach { suggestion ->
                        OutlinedButton(
                            onClick = { viewModel.sendAiMessage(suggestion, incidentId) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandBlue)
                        ) {
                            Text(suggestion, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (chatMessages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Ask me anything about your dispatches, safety steps, or telemetry.", color = SlateTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                items(chatMessages) { msg ->
                    val isUser = msg.isUser
                    val bg = if (isUser) BrandBlue else LightSurfaceVariant
                    val textColor = if (isUser) Color.White else SlateTextPrimary
                    val align = if (isUser) Alignment.End else Alignment.Start

                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = msg.timestamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) Color.White.copy(alpha = 0.7f) else SlateTextSecondary,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }

                if (isAiThinking) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BrandBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini is analyzing context...", style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
                        }
                    }
                }
            }

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = { Text("Ask RapidResQ AI...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
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

                FloatingActionButton(
                    onClick = {
                        if (inputMessage.isNotBlank()) {
                            viewModel.sendAiMessage(inputMessage, incidentId)
                            inputMessage = ""
                        }
                    },
                    containerColor = BrandBlue,
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
