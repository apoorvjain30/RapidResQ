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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    viewModel: RapidResQViewModel,
    roleName: String,
    email: String,
    onBack: () -> Unit,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier
) {
    val challenge by viewModel.activeOtpChallenge.collectAsState()
    val simulatedNotice by viewModel.simulatedOtpNotice.collectAsState()
    val isActionInProgress by viewModel.isActionInProgress.collectAsState()

    var otpValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var timeLeft by remember { mutableStateOf(300) }

    // Remove any LaunchedEffect that auto-pops the screen if challenge == null

    LaunchedEffect(timeLeft) {
        if (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    val scope = rememberCoroutineScope()

    fun attemptVerify() {
        if (otpValue.length != 6) return
        errorMessage = null
        scope.launch {
            val result = viewModel.verifyActiveOtp(otpValue)
            if (result.isSuccess) {
                onVerified()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Verification failed"
                otpValue = "" // Reset on fail
            }
        }
    }

    fun attemptResend() {
        errorMessage = null
        otpValue = ""
        scope.launch {
            val result = viewModel.resendActiveOtp()
            if (result.isSuccess) {
                timeLeft = 300
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to resend"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBg)
    ) {
        TopAppBar(
            title = { Text("Verify Identity", fontWeight = FontWeight.Bold, color = TextPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCard)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Surface(
                color = BrandBlueLight,
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(40.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Enter Security Code", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            
            val emailMasked = challenge?.email?.let { e ->
                val parts = e.split("@")
                if (parts.size == 2 && parts[0].length > 2) {
                    "${parts[0].take(2)}***@${parts[1]}"
                } else e
            } ?: email

            Text(
                text = "We sent a 6-digit code to $emailMasked",
                fontSize = 15.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            if (errorMessage != null) {
                Surface(color = EmergencyRedSoft, contentColor = EmergencyRed, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(errorMessage!!, modifier = Modifier.padding(12.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                }
            }

            // OTP Input Blocks
            BasicTextField(
                value = otpValue,
                onValueChange = {
                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                        otpValue = it
                        if (it.length == 6) attemptVerify()
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                decorationBox = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until 6) {
                            val char = otpValue.getOrNull(i)?.toString() ?: ""
                            val isFocused = otpValue.length == i
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(InputSurface)
                                    .border(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = if (isFocused) BrandBlue else InputBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(char, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { attemptVerify() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = otpValue.length == 6 && !isActionInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isActionInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Verify Code", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (timeLeft > 0) {
                val min = timeLeft / 60
                val sec = timeLeft % 60
                Text("Code expires in $min:${sec.toString().padStart(2, '0')}", color = TextMuted, fontSize = 13.sp)
            } else {
                TextButton(onClick = { attemptResend() }, enabled = !isActionInProgress) {
                    Text("Resend Code", color = BrandBlue, fontWeight = FontWeight.Bold)
                }
            }

            // Notice Pill
            if (simulatedNotice != null) {
                Spacer(modifier = Modifier.height(32.dp))
                val isRealDelivery = simulatedNotice!!.contains("sent to")
                val bgColor = if (isRealDelivery) EmeraldSoft else AiIndigoSoft
                val fgColor = if (isRealDelivery) EmeraldGreen else AiIndigo

                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = simulatedNotice!!,
                        modifier = Modifier.padding(16.dp),
                        color = fgColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
