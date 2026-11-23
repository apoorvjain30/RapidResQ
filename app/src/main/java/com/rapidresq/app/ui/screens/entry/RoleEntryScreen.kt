package com.rapidresq.app.ui.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rapidresq.app.data.model.UserRole
import com.rapidresq.app.ui.components.DemoModeBanner
import com.rapidresq.app.ui.theme.*

@Composable
fun RoleEntryScreen(
    onSelectRole: (UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        DemoModeBanner()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "RapidResQ",
                style = MaterialTheme.typography.displaySmall,
                color = BrandNavy,
                fontWeight = FontWeight.ExtraBold
            )
            
            Text(
                text = "Report. Respond. Resolve. In Real Time.",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBlue,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Authentication (JWT + OTP) is scheduled for Phase 3. Select a role below for instant demo access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateTextSecondary,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            RoleCard(
                role = UserRole.CITIZEN,
                onClick = { onSelectRole(UserRole.CITIZEN) },
                icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp)) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            RoleCard(
                role = UserRole.DISPATCHER,
                onClick = { onSelectRole(UserRole.DISPATCHER) },
                icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(32.dp)) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            RoleCard(
                role = UserRole.RESPONDER,
                onClick = { onSelectRole(UserRole.RESPONDER) },
                icon = { Icon(Icons.Default.LocalHospital, contentDescription = null, modifier = Modifier.size(32.dp)) }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleCard(
    role: UserRole,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = BrandBlueLight,
                contentColor = BrandBlue,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Column {
                Text(
                    text = role.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = SlateTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = role.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateTextSecondary
                )
            }
        }
    }
}
