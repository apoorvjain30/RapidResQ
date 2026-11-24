package com.rapidresq.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rapidresq.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    roleName: String,
    email: String,
    onVerified: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var otpValue by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(60) }
    val otpLength = 6

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        TopAppBar(
            title = { Text("", color = BrandNavy) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandNavy)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = BrandBlueLight,
                contentColor = BrandBlue,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("2-Step OTP Verification", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = BrandNavy)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Enter the 6-digit code sent to\n$email", style = MaterialTheme.typography.bodyMedium, color = SlateTextSecondary, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                color = WarningAmberBg,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Phase 2 UI Preview: Enter any 6 digits (e.g., 123456). Real OTP delivery, expiry & lockout launch in Phase 3.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            BasicTextField(
                value = otpValue,
                onValueChange = { if (it.length <= otpLength) otpValue = it.filter { char -> char.isDigit() } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                decorationBox = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        repeat(otpLength) { index ->
                            val char = when {
                                index >= otpValue.length -> ""
                                else -> otpValue[index].toString()
                            }
                            val isFocused = index == otpValue.length || (index == otpLength - 1 && otpValue.length == otpLength)
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
                onClick = onVerified,
                enabled = otpValue.length == otpLength,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) {
                Text("Verify & Continue", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = { countdown = 60 },
                enabled = countdown == 0
            ) {
                if (countdown > 0) {
                    Text("Resend code in 00:${countdown.toString().padStart(2, '0')}", color = SlateTextSecondary)
                } else {
                    Text("Resend OTP", color = BrandBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
