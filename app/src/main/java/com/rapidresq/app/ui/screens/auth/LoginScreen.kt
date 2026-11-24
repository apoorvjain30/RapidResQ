package com.rapidresq.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rapidresq.app.data.model.UserRole
import com.rapidresq.app.ui.components.DemoModeBanner
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel

@Composable
fun LoginScreen(
    viewModel: RapidResQViewModel,
    onGuestSosClick: () -> Unit,
    onRequireOtp: (UserRole, String) -> Unit,
    onCitizenSignedIn: () -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onOpenRoleSwitcher: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var emailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var loginErrorMsg by remember { mutableStateOf<String?>(null) }

    fun validateAndProceed() {
        emailError = email.isBlank() || !email.contains("@")
        passwordError = password.length < 6
        loginErrorMsg = null

        if (!emailError && !passwordError) {
            val result = viewModel.loginWithPassword(email, password)
            if (result.isSuccess) {
                val (session, role) = result.getOrNull()!!
                if (session != null) {
                    onCitizenSignedIn()
                } else {
                    onRequireOtp(role, email)
                }
            } else {
                loginErrorMsg = result.exceptionOrNull()?.message
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        DemoModeBanner(text = "Phase 2 Auth UI · Real JWT/OTP Verification in Phase 3")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Guest SOS Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = EmergencyRedBg),
                border = CardDefaults.outlinedCardBorder(true).copy(brush = SolidColor(EmergencyRed))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Need Immediate Help?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmergencyRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onGuestSosClick,
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guest Emergency SOS (No Login)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Demo Accounts Helpers
            Surface(
                color = InfoCyanBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Demo Test Accounts", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = InfoCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AssistChip(onClick = { email = "admin@rapidresq.in"; password = "Admin@123" }, label = { Text("Admin") })
                        AssistChip(onClick = { email = "dispatch@rapidresq.in"; password = "Dispatch@123" }, label = { Text("Dispatch") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AssistChip(onClick = { email = "bravo@rapidresq.in"; password = "Unit@123" }, label = { Text("Unit Bravo") })
                        AssistChip(onClick = { email = "citizen@rapidresq.in"; password = "Citizen@123" }, label = { Text("Citizen") })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    
                    Text("Sign In", style = MaterialTheme.typography.headlineSmall, color = BrandNavy, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            emailError = false
                            loginErrorMsg = null
                        },
                        label = { Text("Email Address") },
                        isError = emailError,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
                    if (emailError) {
                        Text("Please enter a valid email", color = EmergencyRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            passwordError = false
                            loginErrorMsg = null
                        },
                        label = { Text("Password") },
                        isError = passwordError,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle password visibility")
                            }
                        },
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
                    if (passwordError) {
                        Text("Password must be at least 6 characters", color = EmergencyRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                    }

                    if (loginErrorMsg != null) {
                        Surface(color = EmergencyRedBg, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(loginErrorMsg!!, color = EmergencyRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onForgotPasswordClick,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Forgot Password?", color = BrandBlue)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { validateAndProceed() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Text("Sign In", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = onRegisterClick) {
                Text("New Citizen? Create Optional Account", color = SlateTextPrimary, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = onOpenRoleSwitcher) {
                Text("Quick Demo Role Switcher (Phase 1 Style)", color = SlateTextSecondary)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
