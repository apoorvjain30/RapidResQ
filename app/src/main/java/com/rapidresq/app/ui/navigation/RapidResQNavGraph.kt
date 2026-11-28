package com.rapidresq.app.ui.navigation

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rapidresq.app.data.model.UserRole
import com.rapidresq.app.ui.components.RapidResQLoadingOverlay
import com.rapidresq.app.ui.screens.citizen.CitizenHomeScreen
import com.rapidresq.app.ui.screens.citizen.CitizenTrackScreen
import com.rapidresq.app.ui.screens.citizen.ReportEmergencyScreen
import com.rapidresq.app.ui.screens.dispatcher.DispatcherDashboardScreen
import com.rapidresq.app.ui.screens.entry.RoleEntryScreen
import com.rapidresq.app.ui.screens.responder.ResponderDashboardScreen
import com.rapidresq.app.ui.screens.admin.AdminDashboardScreen
import com.rapidresq.app.ui.screens.onboarding.SplashScreen
import com.rapidresq.app.ui.screens.onboarding.OnboardingScreen
import com.rapidresq.app.ui.screens.auth.LoginScreen
import com.rapidresq.app.ui.screens.auth.RegisterCitizenScreen
import com.rapidresq.app.ui.screens.auth.OtpVerificationScreen
import com.rapidresq.app.ui.screens.auth.ForgotPasswordScreen
import com.rapidresq.app.viewmodel.RapidResQViewModel

fun navigateAndClearAuthStack(navController: NavHostController, targetRoute: String) {
    navController.navigate(targetRoute) {
        popUpTo(navController.graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
fun RapidResQNavGraph() {
    val navController = rememberNavController()
    val viewModel: RapidResQViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val statusBanner by viewModel.statusBanner.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val isSessionInitialized by viewModel.isSessionInitialized.collectAsState()
    val hasPendingOtpChallenge by viewModel.hasPendingOtpChallenge.collectAsState()
    val globalLoadingMessage by viewModel.globalLoadingMessage.collectAsState()

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusBanner()
        }
    }
    
    LaunchedEffect(actionFeedback) {
        actionFeedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionFeedback()
        }
    }

    val currentRoute = navController.currentBackStackEntry?.destination?.route
    val authRoutes = setOf("login", "register_citizen", "otp_verify", "forgot_password")

    LaunchedEffect(currentSession, currentRoute) {
        val session = currentSession
        if (session != null && currentRoute in authRoutes) {
            val dest = when (session.role) {
                UserRole.CITIZEN -> "citizen_home"
                UserRole.DISPATCHER -> "dispatcher_dashboard"
                UserRole.RESPONDER -> "responder_dashboard"
                UserRole.ADMIN -> "admin_dashboard"
            }
            Log.d("RRQ_TRACE", "Session active on $currentRoute -> routing to $dest")
            navigateAndClearAuthStack(navController, dest)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(160)) }
            ) {
                composable("splash") {
                    SplashScreen(
                        isSessionInitialized = isSessionInitialized,
                        onSplashComplete = {
                            val session = viewModel.currentSession.value
                            val hasOtp = viewModel.hasPendingOtpChallenge.value
                            
                            Log.d("RRQ_TRACE", "Splash complete. session=${session != null}, hasOtp=$hasOtp")
                            
                            if (session != null) {
                                val dest = when (session.role) {
                                    UserRole.CITIZEN -> "citizen_home"
                                    UserRole.DISPATCHER -> "dispatcher_dashboard"
                                    UserRole.RESPONDER -> "responder_dashboard"
                                    UserRole.ADMIN -> "admin_dashboard"
                                }
                                Log.d("RRQ_TRACE", "Splash -> navigating to $dest")
                                navigateAndClearAuthStack(navController, dest)
                            } else if (hasOtp) {
                                Log.d("RRQ_TRACE", "Splash -> navigating to otp_verify")
                                navigateAndClearAuthStack(navController, "otp_verify")
                            } else {
                                Log.d("RRQ_TRACE", "Splash -> navigating to onboarding")
                                navigateAndClearAuthStack(navController, "onboarding")
                            }
                        }
                    )
                }
                composable("onboarding") {
                    OnboardingScreen(
                        onFinishOnboarding = {
                            navigateAndClearAuthStack(navController, "login")
                        },
                        onGuestSos = {
                            viewModel.enterGuestSos()
                            navigateAndClearAuthStack(navController, "citizen_home")
                        }
                    )
                }
                composable("login") {
                    LoginScreen(
                        viewModel = viewModel,
                        onGuestSos = {
                            navigateAndClearAuthStack(navController, "citizen_home")
                        },
                        onRequireOtp = {
                            navController.navigate("otp_verify") {
                                launchSingleTop = true
                            }
                        },
                        onLoginSuccess = { 
                            // Handled by Reactive Router
                        },
                        onNavigateToRegister = { navController.navigate("register_citizen") },
                        onNavigateToForgotPass = { navController.navigate("forgot_password") }
                    )
                }
                composable("register_citizen") {
                    RegisterCitizenScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onRequireOtp = { 
                            navController.navigate("otp_verify") {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("otp_verify") {
                    OtpVerificationScreen(
                        viewModel = viewModel,
                        roleName = viewModel.currentSession.value?.role?.name ?: UserRole.CITIZEN.name,
                        email = viewModel.currentSession.value?.email ?: "",
                        onVerified = {
                            // Handled by Reactive Router
                        },
                        onBack = { 
                            Log.d("RRQ_TRACE", "OTP Cancelled -> popping backstack")
                            viewModel.clearPendingOtp()
                            navController.popBackStack() 
                        }
                    )
                }
                composable("forgot_password") {
                    ForgotPasswordScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onResetComplete = { 
                            navigateAndClearAuthStack(navController, "login")
                        }
                    )
                }
                composable("role_entry") {
                    RoleEntryScreen(
                        onSelectRole = { role ->
                            when (role) {
                                UserRole.CITIZEN -> navigateAndClearAuthStack(navController, "citizen_home")
                                UserRole.DISPATCHER -> navigateAndClearAuthStack(navController, "dispatcher_dashboard")
                                UserRole.RESPONDER -> navigateAndClearAuthStack(navController, "responder_dashboard")
                                UserRole.ADMIN -> navigateAndClearAuthStack(navController, "admin_dashboard")
                            }
                        }
                    )
                }
                composable("citizen_home") {
                    CitizenHomeScreen(
                        viewModel = viewModel,
                        onBackToRoles = { navController.popBackStack("role_entry", inclusive = false) },
                        onReportClick = { navController.navigate("citizen_report") },
                        onTrackIncident = { incidentId -> navController.navigate("citizen_track/$incidentId") },
                        onLogout = { navigateAndClearAuthStack(navController, "login") }
                    )
                }
                composable("citizen_report") {
                    ReportEmergencyScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onReportSubmitted = { incidentId ->
                            navController.navigate("citizen_track/$incidentId") {
                                popUpTo("citizen_report") { inclusive = true }
                            }
                        }
                    )
                }
                composable(
                    route = "citizen_track/{incidentId}",
                    arguments = listOf(navArgument("incidentId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val incidentId = backStackEntry.arguments?.getString("incidentId") ?: ""
                    CitizenTrackScreen(
                        incidentId = incidentId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("dispatcher_dashboard") {
                    if (currentSession?.role == UserRole.DISPATCHER) {
                        DispatcherDashboardScreen(
                            viewModel = viewModel,
                            onBackToRoles = { navController.popBackStack("role_entry", inclusive = false) },
                            onLogout = { navigateAndClearAuthStack(navController, "login") }
                        )
                    }
                }
                composable("responder_dashboard") {
                    if (currentSession?.role == UserRole.RESPONDER) {
                        ResponderDashboardScreen(
                            viewModel = viewModel,
                            onBackToRoles = { navController.popBackStack("role_entry", inclusive = false) },
                            onLogout = { navigateAndClearAuthStack(navController, "login") }
                        )
                    }
                }
                composable("admin_dashboard") {
                    if (currentSession?.role == UserRole.ADMIN) {
                        AdminDashboardScreen(
                            viewModel = viewModel,
                            onBackToRoles = { navController.popBackStack("role_entry", inclusive = false) },
                            onLogout = { navigateAndClearAuthStack(navController, "login") }
                        )
                    }
                }
            }

            // Global Animated Loading Overlay
            RapidResQLoadingOverlay(message = globalLoadingMessage)
        }
    }
}
