package com.rapidresq.app.ui.screens.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidresq.app.ui.components.RapidResQLogoMark
import com.rapidresq.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    onGuestSos: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val pages = listOf(
        Triple("Real-Time Emergency Intel", "Report incidents instantly with GPS, photos, and automated AI triage for faster dispatch.", BrandBlue),
        Triple("Precision Responder Routing", "Live tracking, smart PostGIS clustering, and automated dispatch to the nearest specialized unit.", InfoCyan),
        Triple("Governance & Audit Security", "Cryptographically verified audit trails ensure every critical action is logged and secure.", SafeEmerald)
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RapidResQLogoMark(32.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RapidResQ", color = BrandNavy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                TextButton(onClick = onFinishOnboarding) {
                    Text("Skip", color = TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            Surface(
                color = SurfaceCard,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                    Button(
                        onClick = {
                            if (pagerState.currentPage < 2) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                onFinishOnboarding()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (pagerState.currentPage == 2) "Get Started" else "Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onGuestSos,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                        border = BorderStroke(1.dp, EmergencyRedBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Emergency? Report as Guest", 
                            fontWeight = FontWeight.Bold, 
                            maxLines = 1, 
                            softWrap = false, 
                            overflow = TextOverflow.Visible
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = pages[page].third.copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.size(96.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Surface(
                                color = pages[page].third.copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp)
                            ) { }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = pages[page].first,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = pages[page].second,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }
            
            Row(
                modifier = Modifier.padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { index ->
                    val width by animateDpAsState(targetValue = if (pagerState.currentPage == index) 24.dp else 8.dp, label = "dot")
                    val color = if (pagerState.currentPage == index) BrandBlue else CardBorder
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}
