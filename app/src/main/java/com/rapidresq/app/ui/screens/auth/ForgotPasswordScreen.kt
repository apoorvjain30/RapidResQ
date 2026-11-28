package com.rapidresq.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.rapidresq.app.ui.components.rapidResQTextFieldColors
import com.rapidresq.app.ui.theme.*
import com.rapidresq.app.viewmodel.RapidResQViewModel

enum class ResetStep { EMAIL, OTP, NEW_PASSWORD, SUCCESS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    viewModel: RapidResQViewModel,
    onBack: () -> Unit,
    onResetComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(ResetStep.EMAIL) }
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val simulatedNotice by viewModel.simulatedOtpNotice.collectAsState()
    val isActionInProgress by viewModel.isActionInProgress.collectAsState()

    val passHasUpper = newPassword.any { it.isUpperCase() }
    val passHasLower = newPassword.any { it.isLowerCase() }
    val passHasDigit = newPassword.any { it.isDigit() }
    val passHasLength = newPassword.length >= 8
    val passMatches = newPassword.isNotEmpty() && newPassword == confirmPassword
    val isPassValid = passHasUpper && passHasLower && passHasDigit && passHasLength && passMatches

    val scope = rememberCoroutineScope()

    fun requestOtp() {
        if (email.isBlank()) return
        errorMessage = null
        scope.launch {
            val result = viewModel.requestPasswordResetOtp(email)
            if (result.isSuccess) step = ResetStep.OTP
            else errorMessage = result.exceptionOrNull()?.message ?: "Failed to send reset code"
        }
    }

    fun verifyOtp() {
        if (otp.length != 6) return
        errorMessage = null
        scope.launch {
            val result = viewModel.verifyResetOtp(email, otp)
            if (result.isSuccess) step = ResetStep.NEW_PASSWORD
            else errorMessage = result.exceptionOrNull()?.message ?: "Invalid code"
        }
    }

    fun submitNewPassword() {
        if (!isPassValid) return
        errorMessage = null
        scope.launch {
            val result = viewModel.completePasswordReset(email, newPassword)
            if (result.isSuccess) step = ResetStep.SUCCESS
            else errorMessage = result.exceptionOrNull()?.message ?: "Failed to reset password"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBg)
    ) {
        TopAppBar(
            title = { Text("Reset Password", fontWeight = FontWeight.Bold, color = BrandNavy) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandNavy)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCard)
        )

        // Progress Bar (1. Email -> 2. Verify -> 3. Reset)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressStep(1, step >= ResetStep.EMAIL, step > ResetStep.EMAIL)
            Box(modifier = Modifier.weight(1f).height(2.dp).background(if (step > ResetStep.EMAIL) EmeraldGreen else CardBorder))
            ProgressStep(2, step >= ResetStep.OTP, step > ResetStep.OTP)
            Box(modifier = Modifier.weight(1f).height(2.dp).background(if (step > ResetStep.OTP) EmeraldGreen else CardBorder))
            ProgressStep(3, step >= ResetStep.NEW_PASSWORD, step == ResetStep.SUCCESS)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (errorMessage != null) {
                Surface(color = EmergencyRedSoft, contentColor = EmergencyRed, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(errorMessage!!, modifier = Modifier.padding(12.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = CardDefaults.outlinedCardBorder(true).copy(width = 1.dp, brush = SolidColor(CardBorder)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    when (step) {
                        ResetStep.EMAIL -> {
                            Text("Find Your Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Enter the email address associated with your RapidResQ account.", fontSize = 14.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(20.dp))
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
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { requestOtp() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = email.isNotBlank() && !isActionInProgress,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isActionInProgress) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text("Send Reset Code", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        ResetStep.OTP -> {
                            Text("Verify Code", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Enter the 6-digit code sent to $email", fontSize = 14.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedTextField(
                                value = otp,
                                onValueChange = { otp = it.take(6); errorMessage = null },
                                label = { Text("6-Digit Code") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = rapidResQTextFieldColors()
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { verifyOtp() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = otp.length == 6 && !isActionInProgress,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isActionInProgress) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text("Verify Code", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }

                            if (simulatedNotice != null) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Surface(color = AiIndigoSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = simulatedNotice!!.replace("Simulated OTP Delivery (Backend): ", "OTP Delivery Preview: "),
                                        modifier = Modifier.padding(16.dp),
                                        color = AiIndigo,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        ResetStep.NEW_PASSWORD -> {
                            Text("Create New Password", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("New Password") },
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
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
                                ReqRow("8+ characters", passHasLength)
                                ReqRow("Uppercase letter", passHasUpper)
                                ReqRow("Lowercase letter", passHasLower)
                                ReqRow("Number", passHasDigit)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = rapidResQTextFieldColors()
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { submitNewPassword() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = isPassValid && !isActionInProgress,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isActionInProgress) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text("Reset Password", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        ResetStep.SUCCESS -> {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(color = EmeraldSoft, shape = CircleShape, modifier = Modifier.size(64.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(32.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Password Reset Complete", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Your password has been changed successfully. You can now sign in with your new password.", fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = onResetComplete,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Back to Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressStep(stepNum: Int, isActive: Boolean, isComplete: Boolean) {
    val bgColor = if (isComplete) EmeraldGreen else if (isActive) BrandBlue else LightSurfaceVariant
    val contentColor = if (isComplete || isActive) Color.White else TextMuted
    Surface(
        color = bgColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier = Modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isComplete) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            else Text(stepNum.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReqRow(label: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (met) EmeraldGreen else TextMuted,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = if (met) TextPrimary else TextMuted, fontSize = 12.sp)
    }
}
