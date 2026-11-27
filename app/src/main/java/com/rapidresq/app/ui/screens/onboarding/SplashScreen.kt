package com.rapidresq.app.ui.screens.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidresq.app.ui.components.RapidResQLogoMark
import com.rapidresq.app.ui.theme.BrandNavy
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = tween(durationMillis = 800),
        label = "logo_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "logo_alpha"
    )

    LaunchedEffect(Unit) {
        isVisible = true
        delay(1300)
        onSplashComplete()
    }

    val backgroundBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFF1E3A8A), // Subtle inner glow
            BrandNavy
        ),
        radius = 800f
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
        ) {
            RapidResQLogoMark(88.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "RapidResQ",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Report. Respond. Resolve.",
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Text(
            text = "EMERGENCY COORDINATION PLATFORM",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(alpha)
        )
    }
}
