package com.rapidresq.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.rapidresq.app.data.model.EmergencyCategory
import com.rapidresq.app.data.model.IncidentSeverity
import com.rapidresq.app.data.model.MediaAttachment
import com.rapidresq.app.data.model.UserRole
import com.rapidresq.app.data.repository.AuthRepository
import com.rapidresq.app.data.repository.DemoIncidentRepository
import com.rapidresq.app.data.repository.IncidentRepository
import com.rapidresq.app.data.security.KeystoreSessionManager
import com.rapidresq.app.data.security.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class RapidResQViewModel(application: Application) : AndroidViewModel(application) {

    private val incidentRepository: IncidentRepository = DemoIncidentRepository()
    private val authRepository = AuthRepository(incidentRepository)
    private val sessionManager = KeystoreSessionManager(application)

    val incidents = incidentRepository.incidents
    val teams = incidentRepository.teams
    val auditLogs = incidentRepository.auditLogs
    val staffAccounts = incidentRepository.staffAccounts

    private val _currentSession = MutableStateFlow<UserSession?>(null)
    val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    val activeOtpChallenge = authRepository.activeOtpChallenge
    val simulatedOtpNotice = authRepository.simulatedOtpNotice

    private val _activeResponderTeamId = MutableStateFlow("TEAM-BRAVO")
    val activeResponderTeamId: StateFlow<String> = _activeResponderTeamId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        val restored = sessionManager.getSession()
        if (restored != null) {
            _currentSession.value = restored
            if (restored.assignedTeamId != null) {
                _activeResponderTeamId.value = restored.assignedTeamId
            }
        }
    }

    // --- Auth Actions ---

    fun loginWithPassword(email: String, pass: String): Result<Pair<UserSession?, UserRole>> {
        val result = authRepository.loginWithPassword(email, pass)
        if (result.isSuccess) {
            val session = result.getOrNull()?.first
            if (session != null) {
                sessionManager.saveSession(session)
                _currentSession.value = session
            }
        }
        return result
    }

    fun verifyActiveOtp(code: String): Result<Unit> {
        val result = authRepository.verifyActiveOtp(code)
        if (result.isSuccess) {
            val session = result.getOrNull()!!
            sessionManager.saveSession(session)
            _currentSession.value = session
            if (session.assignedTeamId != null) {
                _activeResponderTeamId.value = session.assignedTeamId
            }
            return Result.success(Unit)
        }
        return Result.failure(result.exceptionOrNull() ?: Exception("Verification failed"))
    }

    fun resendActiveOtp() = authRepository.resendActiveOtp()
    
    fun registerCitizen(name: String, email: String, phone: String, pass: String) = authRepository.registerCitizen(name, email, phone, pass)
    
    fun requestPasswordResetOtp(email: String) = authRepository.requestPasswordResetOtp(email)
    
    fun verifyResetOtp(email: String, code: String) = authRepository.verifyResetOtp(email, code)
    
    fun completePasswordReset(email: String, newPass: String) = authRepository.completePasswordReset(email, newPass)
    
    fun enterGuestSos() {
        val guestSession = UserSession("guest@rapidresq.in", "Guest Citizen", UserRole.CITIZEN, null, UUID.randomUUID().toString(), System.currentTimeMillis() + 8 * 3600 * 1000, true)
        _currentSession.value = guestSession
    }

    fun logout() {
        sessionManager.clearSession()
        _currentSession.value = null
        incidentRepository.logSecurityEvent("SYSTEM", "LOGOUT", "SYSTEM", "User logged out")
    }

    // --- Core Operations ---

    fun clearStatusBanner() { _statusBanner.value = null }
    private fun showStatus(message: String) { _statusBanner.value = message }
    fun setActiveResponderTeam(teamId: String) { _activeResponderTeamId.value = teamId }

    fun submitIncident(
        category: EmergencyCategory, title: String, description: String, locationText: String, gpsLabel: String?, severity: IncidentSeverity, reporterContact: String, media: List<MediaAttachment>?
    ): String? {
        val result = incidentRepository.submitIncident(category, title, description, locationText, gpsLabel, severity, reporterContact, media)
        return if (result.isSuccess) { showStatus("Incident submitted successfully"); result.getOrNull() } else { showStatus("Failed to submit incident"); null }
    }
    fun verifyIncident(incidentId: String, actor: String) {
        if (incidentRepository.verifyIncident(incidentId, actor).isSuccess) showStatus("Incident verified") else showStatus("Verification failed")
    }
    fun rejectIncident(incidentId: String, reason: String, actor: String) {
        if (incidentRepository.rejectIncident(incidentId, reason, actor).isSuccess) showStatus("Incident rejected") else showStatus("Rejection failed")
    }
    fun assignTeam(incidentId: String, teamId: String, actor: String) {
        if (incidentRepository.assignTeam(incidentId, teamId, actor).isSuccess) {
            _activeResponderTeamId.value = teamId
            showStatus("Team assigned successfully")
        } else showStatus("Assignment failed")
    }
    fun advanceResponderStatus(incidentId: String, actor: String) {
        if (incidentRepository.advanceResponderStatus(incidentId, _activeResponderTeamId.value, actor).isSuccess) showStatus("Status updated") else showStatus("Update failed")
    }
    fun addFieldNote(incidentId: String, author: String, text: String) {
        if (incidentRepository.addFieldNote(incidentId, author, text).isSuccess) showStatus("Field note added") else showStatus("Failed to add field note")
    }
    fun addResponseTeam(name: String, unitType: String, baseLocation: String) {
        if (incidentRepository.addResponseTeam(name, unitType, baseLocation).isSuccess) showStatus("Team added successfully") else showStatus("Failed to add team")
    }
    fun toggleTeamAvailability(teamId: String) {
        if (incidentRepository.toggleTeamAvailability(teamId).isSuccess) showStatus("Team availability updated") else showStatus("Failed to update team")
    }
    fun toggleStaffActive(staffId: String) {
        if (incidentRepository.toggleStaffActive(staffId).isSuccess) showStatus("Staff access updated") else showStatus("Failed to update staff")
    }
    fun logSecurityEvent(actorRole: String, action: String, details: String) {
        incidentRepository.logSecurityEvent(actorRole, action, "SYSTEM", details)
    }
}
