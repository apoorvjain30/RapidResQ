package com.rapidresq.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidresq.app.data.model.UserRole
import com.rapidresq.app.ui.components.RapidResQLogoMark
import com.rapidresq.app.ui.components.rapidResQTextFieldColors
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel

@Composable
fun LoginScreen(
    viewModel: RapidResQViewModel,
    onLoginSuccess: (UserRole) -> Unit,
    onRequireOtp: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPass: () -> Unit,
    onGuestSos: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDemoAccounts by remember { mutableStateOf(false) }

    val isActionInProgress by viewModel.isActionInProgress.collectAsState()
    val globalLoadingMessage by viewModel.globalLoadingMessage.collectAsState()

    val scope = rememberCoroutineScope()

    fun attemptLogin() {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please enter email and password"
            return
        }
        errorMessage = null
        scope.launch {
            val result = viewModel.loginWithPassword(email, password)
            if (result.isSuccess) {
                val sessionPair = result.getOrNull()!!
                if (sessionPair.first == null) {
                    onRequireOtp()
                } else {
                    onLoginSuccess(sessionPair.second)
                }
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Authentication failed"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBg)
            .verticalScroll(rememberScrollState())
    ) {
        // Premium Navy Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(Brush.verticalGradient(listOf(BrandNavy, BrandNavyLight)))
                .statusBarsPadding()
                .padding(bottom = 32.dp, top = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RapidResQLogoMark(44.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("RapidResQ", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text("Secure Emergency Coordination", color = InputBorder, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Guest SOS Card
            Card(
                onClick = {
                    viewModel.enterGuestSos()
                    onGuestSos()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = EmergencyRedSoft),
                border = CardDefaults.outlinedCardBorder(true).copy(brush = SolidColor(EmergencyRedBorder)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = EmergencyRed, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(24.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Need Immediate Help?", fontWeight = FontWeight.Bold, color = EmergencyRed, fontSize = 16.sp)
                            Text("Report an emergency without signing in", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.enterGuestSos()
                            onGuestSos()
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guest Emergency SOS", fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
                    }
                }
            }

            // Sign In Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = CardDefaults.outlinedCardBorder(true).copy(width = 1.dp, brush = SolidColor(CardBorder)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Account Sign In", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Protected by 2-step email OTP verification", fontSize = 13.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(20.dp))

                    if (errorMessage != null) {
                        Surface(color = EmergencyRedSoft, contentColor = EmergencyRed, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Text(errorMessage!!, modifier = Modifier.padding(12.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = rapidResQTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = TextMuted)
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = rapidResQTextFieldColors()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onNavigateToForgotPass) {
                            Text("Forgot Password?", color = BrandBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = { attemptLogin() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !isActionInProgress && globalLoadingMessage == null,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isActionInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onNavigateToRegister) {
                    Text("New citizen? Create Optional Account", color = BrandBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Demo Footer
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (showDemoAccounts) "Quick Fill Test Credentials ▴" else "Quick Fill Test Credentials ▾",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { showDemoAccounts = !showDemoAccounts }.padding(8.dp)
            )
            
            AnimatedVisibility(visible = showDemoAccounts) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DemoChip("Admin") { email = "admin@rapidresq.in"; password = "Admin@123" }
                        DemoChip("Dispatcher") { email = "dispatch@rapidresq.in"; password = "Dispatch@123" }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DemoChip("Unit Alpha") { email = "alpha@rapidresq.in"; password = "Unit@123" }
                        DemoChip("Unit Bravo") { email = "bravo@rapidresq.in"; password = "Unit@123" }
                        DemoChip("Citizen") { email = "citizen@rapidresq.in"; password = "Citizen@123" }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DemoChip(label: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF1A2438),
        contentColor = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
