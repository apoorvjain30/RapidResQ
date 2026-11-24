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

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusBanner()
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
                        navController.navigate("onboarding") {
                            popUpTo("splash") { inclusive = true }
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
                    onGuestSosClick = { navController.navigate("citizen_home") }
                )
            }
            composable("login") {
                LoginScreen(
                    onGuestSosClick = { navController.navigate("citizen_home") },
                    onRequireOtp = { role, email -> 
                        val encodedEmail = email.replace(".", "%2E") // simple encoding for demo
                        navController.navigate("otp_verify/${role.name}/$encodedEmail")
                    },
                    onCitizenSignedIn = { navController.navigate("citizen_home") },
                    onRegisterClick = { navController.navigate("register_citizen") },
                    onForgotPasswordClick = { navController.navigate("forgot_password") },
                    onOpenRoleSwitcher = { navController.navigate("role_entry") }
                )
            }
            composable("register_citizen") {
                RegisterCitizenScreen(
                    onBack = { navController.popBackStack() },
                    onProceedToOtp = { email -> 
                        val encodedEmail = email.replace(".", "%2E")
                        navController.navigate("otp_verify/${UserRole.CITIZEN.name}/$encodedEmail")
                    },
                    onGuestSosClick = { navController.navigate("citizen_home") }
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
                    onTrackIncident = { incidentId -> navController.navigate("citizen_track/$incidentId") }
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
                DispatcherDashboardScreen(
                    viewModel = viewModel,
                    onBackToRoles = { navController.popBackStack("role_entry", inclusive = false) }
                )
            }
            composable("responder_dashboard") {
                ResponderDashboardScreen(
                    viewModel = viewModel,
                    onBackToRoles = { navController.popBackStack("role_entry", inclusive = false) }
                )
            }
            composable("admin_dashboard") {
                AdminDashboardScreen(
                    viewModel = viewModel,
                    onBackToRoles = { navController.popBackStack("role_entry", inclusive = false) }
                )
            }
        }
    }
}
