package com.rapidresq.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rapidresq.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(1) } // 1: Email, 2: OTP, 3: New Password, 4: Success
    
    var email by remember { mutableStateOf("") }
    var otpValue by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        TopAppBar(
            title = { Text(if (step == 4) "Password Reset" else "Reset Password", fontWeight = FontWeight.Bold, color = BrandNavy) },
            navigationIcon = {
                if (step != 4) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandNavy)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Progress Indicator
            if (step < 4) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepDot(1, "Email", step >= 1)
                    HorizontalDivider(modifier = Modifier.width(32.dp), color = if (step >= 2) BrandBlue else SubtleBorder)
                    StepDot(2, "Reset OTP", step >= 2)
                    HorizontalDivider(modifier = Modifier.width(32.dp), color = if (step >= 3) BrandBlue else SubtleBorder)
                    StepDot(3, "New Password", step >= 3)
                }

                Surface(
                    color = InfoCyanBg,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Phase 2 UI: Real server-side reset & audit logging (PASSWORD_RESET) arrive in Phase 3.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InfoCyan,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            when (step) {
                1 -> {
                    Text("Enter registered email", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SlateTextPrimary, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = defaultTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { if (email.contains("@")) step = 2 },
                        enabled = email.contains("@"),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Text("Send Reset Code", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                2 -> {
                    Text("Enter 6-digit Reset OTP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SlateTextPrimary, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sent to $email", style = MaterialTheme.typography.bodyMedium, color = SlateTextSecondary, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    BasicTextField(
                        value = otpValue,
                        onValueChange = { if (it.length <= 6) otpValue = it.filter { char -> char.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        decorationBox = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                repeat(6) { index ->
                                    val char = when {
                                        index >= otpValue.length -> ""
                                        else -> otpValue[index].toString()
                                    }
                                    val isFocused = index == otpValue.length || (index == 5 && otpValue.length == 6)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .background(LightSurface, RoundedCornerShape(8.dp))
                                            .border(2.dp, if (isFocused) BrandBlue else SubtleBorder, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(char, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                                    }
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { step = 3 },
                        enabled = otpValue.length == 6,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Text("Verify Reset Code", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                3 -> {
                    val isPasswordValid = newPassword.length >= 8 && newPassword.any { it.isUpperCase() } && newPassword.any { it.isLowerCase() } && newPassword.any { it.isDigit() }
                    val isFormValid = isPasswordValid && newPassword == confirmPassword

                    Text("Enter New Password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SlateTextPrimary, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                            }
                        },
                        colors = defaultTextFieldColors()
                    )
                    
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        PasswordRuleItem("8+ characters", newPassword.length >= 8)
                        PasswordRuleItem("Uppercase & lowercase", newPassword.any { it.isUpperCase() } && newPassword.any { it.isLowerCase() })
                        PasswordRuleItem("At least one number", newPassword.any { it.isDigit() })
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword,
                        colors = defaultTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { if (isFormValid) step = 4 },
                        enabled = isFormValid,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Text("Reset Password", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                4 -> {
                    Spacer(modifier = Modifier.height(48.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SafeEmeraldBg)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeEmerald, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Password Reset!", style = MaterialTheme.typography.headlineSmall, color = SafeEmerald, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Your password has been successfully updated.", style = MaterialTheme.typography.bodyMedium, color = SlateTextPrimary, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onBackToLogin,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Text("Return to Sign In", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun StepDot(stepNum: Int, label: String, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isActive) BrandBlue else LightSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(stepNum.toString(), color = if (isActive) Color.White else SlateTextSecondary, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (isActive) BrandBlue else SlateTextSecondary)
    }
}
