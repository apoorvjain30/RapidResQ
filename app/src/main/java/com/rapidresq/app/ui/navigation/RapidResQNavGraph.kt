package com.rapidresq.app.ui.navigation

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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rapidresq.app.data.model.UserRole
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

@Composable
fun RapidResQNavGraph() {
    val navController = rememberNavController()
    val viewModel: RapidResQViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val statusBanner by viewModel.statusBanner.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()

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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("splash") {
                SplashScreen(
                    onFinished = {
                        if (currentSession != null && currentSession?.isGuest == false) {
                            val dest = when (currentSession?.role) {
                                UserRole.CITIZEN -> "citizen_home"
                                UserRole.DISPATCHER -> "dispatcher_dashboard"
                                UserRole.RESPONDER -> "responder_dashboard"
                                UserRole.ADMIN -> "admin_dashboard"
                                else -> "login"
                            }
                            navController.navigate(dest) { popUpTo("splash") { inclusive = true } }
                        } else {
                            navController.navigate("onboarding") { popUpTo("splash") { inclusive = true } }
                        }
                    }
                )
            }
            composable("onboarding") {
                OnboardingScreen(
                    onFinishOnboarding = {
                        navController.navigate("login") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    onGuestSosClick = {
                        viewModel.enterGuestSos()
                        navController.navigate("citizen_home")
                    }
                )
            }
            composable("login") {
                LoginScreen(
                    viewModel = viewModel,
                    onGuestSosClick = {
                        viewModel.enterGuestSos()
                        navController.navigate("citizen_home")
                    },
                    onRequireOtp = { role, email -> 
                        val encodedEmail = email.replace(".", "%2E") // simple encoding for demo
                        navController.navigate("otp_verify/${role.name}/$encodedEmail")
                    },
                    onCitizenSignedIn = { navController.navigate("citizen_home") { popUpTo("login") { inclusive = true } } },
                    onRegisterClick = { navController.navigate("register_citizen") },
                    onForgotPasswordClick = { navController.navigate("forgot_password") },
                    onOpenRoleSwitcher = { navController.navigate("role_entry") }
                )
            }
            composable("register_citizen") {
                RegisterCitizenScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onProceedToOtp = { email -> 
                        val encodedEmail = email.replace(".", "%2E")
                        navController.navigate("otp_verify/${UserRole.CITIZEN.name}/$encodedEmail")
                    },
                    onGuestSosClick = { 
                        viewModel.enterGuestSos()
                        navController.navigate("citizen_home")
                    }
                )
            }
            composable(
                route = "otp_verify/{roleName}/{email}",
                arguments = listOf(
                    navArgument("roleName") { type = NavType.StringType },
                    navArgument("email") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val roleName = backStackEntry.arguments?.getString("roleName") ?: ""
                val email = backStackEntry.arguments?.getString("email")?.replace("%2E", ".") ?: ""
                
                OtpVerificationScreen(
                    viewModel = viewModel,
                    roleName = roleName,
                    email = email,
                    onVerified = {
                        val destination = when (roleName) {
                            UserRole.CITIZEN.name -> "citizen_home"
                            UserRole.DISPATCHER.name -> "dispatcher_dashboard"
                            UserRole.RESPONDER.name -> "responder_dashboard"
                            UserRole.ADMIN.name -> "admin_dashboard"
                            else -> "role_entry"
                        }
                        navController.navigate(destination) {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("forgot_password") {
                ForgotPasswordScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onBackToLogin = { 
                        navController.navigate("login") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("role_entry") {
                RoleEntryScreen(
                    onSelectRole = { role ->
                        when (role) {
                            UserRole.CITIZEN -> navController.navigate("citizen_home")
                            UserRole.DISPATCHER -> navController.navigate("dispatcher_dashboard")
                            UserRole.RESPONDER -> navController.navigate("responder_dashboard")
                            UserRole.ADMIN -> navController.navigate("admin_dashboard")
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
                    onLogout = { navController.navigate("login") { popUpTo(0) } }
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
                LaunchedEffect(currentSession) {
                    if (currentSession?.role != UserRole.DISPATCHER) {
                        navController.navigate("login") { popUpTo(0) }
                    }
                }
                if (currentSession?.role == UserRole.DISPATCHER) {
                    DispatcherDashboardScreen(
                        viewModel = viewModel,
                        onBackToRoles = { navController.popBackStack("role_entry", inclusive = false) },
                        onLogout = { navController.navigate("login") { popUpTo(0) } }
                    )
                }
            }
            composable("responder_dashboard") {
                LaunchedEffect(currentSession) {
                    if (currentSession?.role != UserRole.RESPONDER) {
                        navController.navigate("login") { popUpTo(0) }
                    }
                }
                if (currentSession?.role == UserRole.RESPONDER) {
                    ResponderDashboardScreen(
                        viewModel = viewModel,
                        onBackToRoles = { navController.popBackStack("role_entry", inclusive = false) },
                        onLogout = { navController.navigate("login") { popUpTo(0) } }
                    )
                }
            }
            composable("admin_dashboard") {
                LaunchedEffect(currentSession) {
                    if (currentSession?.role != UserRole.ADMIN) {
                        navController.navigate("login") { popUpTo(0) }
                    }
                }
                if (currentSession?.role == UserRole.ADMIN) {
                    AdminDashboardScreen(
                        viewModel = viewModel,
                        onBackToRoles = { navController.popBackStack("role_entry", inclusive = false) },
                        onLogout = { navController.navigate("login") { popUpTo(0) } }
                    )
                }
            }
        }
    }
}
