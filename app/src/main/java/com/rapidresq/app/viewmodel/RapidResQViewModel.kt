package com.rapidresq.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidresq.app.data.location.GpsLocationResult
import com.rapidresq.app.data.location.LocationHelper
import com.rapidresq.app.data.model.*
import com.rapidresq.app.data.notifications.EmergencyNotificationHelper
import com.rapidresq.app.data.offline.OfflineQueueManager
import com.rapidresq.app.data.remote.RealtimeSocketManager
import com.rapidresq.app.data.remote.RemoteAuthRepository
import com.rapidresq.app.data.remote.RemoteIncidentRepository
import com.rapidresq.app.data.repository.IncidentRepository
import com.rapidresq.app.data.security.KeystoreSessionManager
import com.rapidresq.app.data.security.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class RapidResQViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = KeystoreSessionManager(application)
    private val locationHelper = LocationHelper(application)
    val offlineQueueManager = OfflineQueueManager(application)
    val notificationHelper = EmergencyNotificationHelper(application)

    val isOnline = offlineQueueManager.isOnline
    val pendingCount = offlineQueueManager.pendingCount

    private val _currentSession = MutableStateFlow<UserSession?>(null)
    val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    private val remoteIncidentRepository = RemoteIncidentRepository(application) {
        _currentSession.value?.token
    }

    private val incidentRepository: IncidentRepository = remoteIncidentRepository
    private val remoteAuthRepository = RemoteAuthRepository(incidentRepository)

    // Global Loading Overlay State
    private val _globalLoadingMessage = MutableStateFlow<String?>(null)
    val globalLoadingMessage: StateFlow<String?> = _globalLoadingMessage.asStateFlow()

    private suspend fun <T> withLoading(message: String, block: suspend () -> T): T {
        _globalLoadingMessage.value = message
        return try {
            block()
        } finally {
            _globalLoadingMessage.value = null
        }
    }

    // Action State Flags
    private val _isSubmittingReport = MutableStateFlow(false)
    val isSubmittingReport: StateFlow<Boolean> = _isSubmittingReport.asStateFlow()

    private val _isActionInProgress = MutableStateFlow(false)
    val isActionInProgress: StateFlow<Boolean> = _isActionInProgress.asStateFlow()

    private val _autoDispatchEnabled = MutableStateFlow(false)
    val autoDispatchEnabled: StateFlow<Boolean> = _autoDispatchEnabled.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    private var isRefreshing = AtomicBoolean(false)

    val socketManager = RealtimeSocketManager().apply {
        onIncidentsChanged = {
            refreshAllData()
        }
        onTrackedIncidentChanged = { incidentId ->
            viewModelScope.launch(Dispatchers.IO) {
                remoteIncidentRepository.trackIncident(incidentId)
            }
        }
        onEscalationAlert = { _, message ->
            notificationHelper.showAlert("Dispatch Escalated", message)
            refreshAllData()
        }
        onTeamLocationChanged = { _, _, _ ->
            refreshAllData()
        }
        onSettingsUpdated = { enabled ->
            _autoDispatchEnabled.value = enabled
        }
    }

    val isCloudConnected = socketManager.isConnected

    val incidents = incidentRepository.incidents
    val teams = incidentRepository.teams
    val auditLogs = incidentRepository.auditLogs
    val staffAccounts = incidentRepository.staffAccounts

    private val _recommendedTeams = MutableStateFlow<List<RankedTeam>>(emptyList())
    val recommendedTeams: StateFlow<List<RankedTeam>> = _recommendedTeams.asStateFlow()

    // AI Chat & Triage State
    private val _chatMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<AiChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _aiScopeLabel = MutableStateFlow("General Public Scope")
    val aiScopeLabel: StateFlow<String> = _aiScopeLabel.asStateFlow()

    private val _auditChainStatus = MutableStateFlow<AuditChainStatus?>(null)
    val auditChainStatus: StateFlow<AuditChainStatus?> = _auditChainStatus.asStateFlow()

    private val _analyticsSummary = MutableStateFlow<AnalyticsSummary?>(null)
    val analyticsSummary: StateFlow<AnalyticsSummary?> = _analyticsSummary.asStateFlow()

    val activeOtpChallenge = remoteAuthRepository.activeOtpChallenge
    val simulatedOtpNotice = remoteAuthRepository.simulatedOtpNotice
    
    private val _isSessionInitialized = MutableStateFlow(false)
    val isSessionInitialized: StateFlow<Boolean> = _isSessionInitialized.asStateFlow()
    
    val hasPendingOtpChallenge = remoteAuthRepository.hasPendingOtpChallenge

    fun clearPendingOtp() {
        remoteAuthRepository.clearPendingOtp()
        sessionManager.clearPendingOtp()
    }

    private val _activeResponderTeamId = MutableStateFlow("TEAM-BRAVO")
    val activeResponderTeamId: StateFlow<String> = _activeResponderTeamId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()
    fun clearStatusBanner() { _statusBanner.value = null }

    private val _isResponderSharingGps = MutableStateFlow(false)
    val isResponderSharingGps: StateFlow<Boolean> = _isResponderSharingGps.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = sessionManager.getSession()
            if (restored != null) {
                _currentSession.value = restored
                if (restored.assignedTeamId != null) {
                    _activeResponderTeamId.value = restored.assignedTeamId
                }
                if (!restored.isGuest) {
                    remoteIncidentRepository.refreshAllData()
                    fetchAutoDispatchSetting()
                    socketManager.connectAndJoinStaff(restored.token)
                }
            }
            _isSessionInitialized.value = true
        }

        // Setup offline queue executor
        offlineQueueManager.setQueueExecutor { actionType, payloadJson ->
            try {
                when (actionType) {
                    "SUBMIT_INCIDENT" -> {
                        val json = JSONObject(payloadJson)
                        val cat = EmergencyCategory.valueOf(json.getString("category"))
                        val title = json.getString("title")
                        val desc = json.getString("description")
                        val locText = json.getString("locationText")
                        val gpsLbl = if (json.has("gpsLabel")) json.getString("gpsLabel") else null
                        val lat = if (json.has("lat")) json.getDouble("lat") else null
                        val lng = if (json.has("lng")) json.getDouble("lng") else null
                        val sev = IncidentSeverity.valueOf(json.getString("severity"))
                        val contact = json.getString("reporterContact")

                        val res = remoteIncidentRepository.submitIncident(cat, title, desc, locText, gpsLbl, sev, contact, null, lat, lng)
                        res.isSuccess
                    }
                    "ADVANCE_STATUS" -> {
                        val json = JSONObject(payloadJson)
                        val incId = json.getString("incidentId")
                        val tId = json.getString("teamId")
                        val act = json.getString("actor")

                        val res = remoteIncidentRepository.advanceResponderStatus(incId, tId, act)
                        res.isSuccess
                    }
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
        }

        // Replay queue when online
        viewModelScope.launch {
            isOnline.collect { online ->
                if (online) {
                    val count = offlineQueueManager.flushPendingQueue { actionType, payloadJson ->
                        try {
                            when (actionType) {
                                "SUBMIT_INCIDENT" -> {
                                    val json = JSONObject(payloadJson)
                                    val cat = EmergencyCategory.valueOf(json.getString("category"))
                                    val title = json.getString("title")
                                    val desc = json.getString("description")
                                    val locText = json.getString("locationText")
                                    val gpsLbl = if (json.has("gpsLabel")) json.getString("gpsLabel") else null
                                    val lat = if (json.has("lat")) json.getDouble("lat") else null
                                    val lng = if (json.has("lng")) json.getDouble("lng") else null
                                    val sev = IncidentSeverity.valueOf(json.getString("severity"))
                                    val contact = json.getString("reporterContact")

                                    val res = remoteIncidentRepository.submitIncident(cat, title, desc, locText, gpsLbl, sev, contact, null, lat, lng)
                                    res.isSuccess
                                }
                                "ADVANCE_STATUS" -> {
                                    val json = JSONObject(payloadJson)
                                    val incId = json.getString("incidentId")
                                    val tId = json.getString("teamId")
                                    val act = json.getString("actor")

                                    val res = remoteIncidentRepository.advanceResponderStatus(incId, tId, act)
                                    res.isSuccess
                                }
                                else -> false
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    if (count > 0) {
                        showFeedback("Synced $count offline action(s) to cloud")
                        refreshAllData()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.disconnect()
        locationHelper.stopDutyLocationUpdates()
    }

    fun clearActionFeedback() {
        _actionFeedback.value = null
    }

    private fun showFeedback(message: String) {
        _actionFeedback.value = message
    }

    // --- Admin Operations ---
    private suspend fun fetchAutoDispatchSetting() {
        val res = remoteIncidentRepository.fetchAutoDispatchSetting()
        if (res.isSuccess) {
            _autoDispatchEnabled.value = res.getOrDefault(false)
        }
    }

    fun setAutoDispatchEnabled(enabled: Boolean) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val res = remoteIncidentRepository.setAutoDispatchEnabled(enabled)
            if (res.isSuccess) {
                _autoDispatchEnabled.value = enabled
                showFeedback("Auto-dispatch ${if (enabled) "Enabled" else "Disabled"}")
            } else {
                showFeedback("Failed to update auto-dispatch: ${res.exceptionOrNull()?.message}")
            }
            _isActionInProgress.value = false
        }
    }

    fun createStaffMember(name: String, email: String, phone: String, pass: String, role: String, department: String, assignedTeamId: String?) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Creating staff account...") {
                val res = remoteIncidentRepository.createStaffMember(name, email, phone, pass, role, department, assignedTeamId)
                if (res.isSuccess) {
                    showFeedback("Staff member created successfully")
                    refreshAllData()
                } else {
                    showFeedback("Failed to create staff: ${res.exceptionOrNull()?.message}")
                }
                _isActionInProgress.value = false
            }
        }
    }

    fun updateStaffRole(userId: String, newRole: String, department: String, assignedTeamId: String?) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Updating staff role...") {
                val res = remoteIncidentRepository.updateStaffRole(userId, newRole, department, assignedTeamId)
                if (res.isSuccess) {
                    showFeedback("Staff role updated successfully")
                    refreshAllData()
                } else {
                    showFeedback("Failed to update role: ${res.exceptionOrNull()?.message}")
                }
                _isActionInProgress.value = false
            }
        }
    }

    fun deleteIncident(incidentId: String) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Deleting incident...") {
                val res = remoteIncidentRepository.deleteIncident(incidentId)
                if (res.isSuccess) {
                    showFeedback("Incident deleted successfully")
                    refreshAllData()
                } else {
                    showFeedback("Failed to delete incident: ${res.exceptionOrNull()?.message}")
                }
                _isActionInProgress.value = false
            }
        }
    }

    // --- AI Chat & Triage Methods ---

    fun sendAiMessage(message: String, incidentId: String? = null) {
        if (message.isBlank()) return
        val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val userMsg = AiChatMessage(id = UUID.randomUUID().toString(), isUser = true, text = message, timestamp = timeLabel)
        _chatMessages.value = _chatMessages.value + userMsg
        _isAiThinking.value = true

        viewModelScope.launch {
            val res = remoteIncidentRepository.sendAiChat(message, incidentId)
            _isAiThinking.value = false
            if (res.isSuccess) {
                val pair = res.getOrNull()!!
                _aiScopeLabel.value = pair.second
                val aiMsg = AiChatMessage(id = UUID.randomUUID().toString(), isUser = false, text = pair.first, timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
                _chatMessages.value = _chatMessages.value + aiMsg
            } else {
                val errMsg = AiChatMessage(id = UUID.randomUUID().toString(), isUser = false, text = "AI service error: ${res.exceptionOrNull()?.message ?: "Unable to connect"}", timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
                _chatMessages.value = _chatMessages.value + errMsg
            }
        }
    }

    fun clearAiChat() {
        _chatMessages.value = emptyList()
    }

    fun triggerAiTriage(incidentId: String) {
        _isActionInProgress.value = true
        viewModelScope.launch {
            withLoading("Refreshing AI Triage...") {
                val res = remoteIncidentRepository.runAiTriage(incidentId)
                if (res.isSuccess) {
                    showFeedback("AI Triage refreshed successfully")
                } else {
                    showFeedback("AI Triage failed")
                }
                _isActionInProgress.value = false
            }
        }
    }

    fun verifyAuditIntegrity() {
        _isActionInProgress.value = true
        viewModelScope.launch {
            withLoading("Verifying SHA-256 audit chain...") {
                val res = remoteIncidentRepository.verifyAuditChain()
                if (res.isSuccess) {
                    _auditChainStatus.value = res.getOrNull()
                    showFeedback("Audit chain verified successfully")
                } else {
                    showFeedback("Audit verification failed")
                }
                _isActionInProgress.value = false
            }
        }
    }

    fun loadAnalytics() {
        viewModelScope.launch {
            val res = remoteIncidentRepository.fetchAnalytics()
            if (res.isSuccess) {
                _analyticsSummary.value = res.getOrNull()
            }
        }
    }

    // --- Hardware GPS Helpers ---

    fun captureLiveGps(onResult: (GpsLocationResult?) -> Unit) {
        viewModelScope.launch {
            val result = locationHelper.getCurrentLocation()
            onResult(result)
        }
    }

    fun toggleResponderLiveGps(enabled: Boolean) {
        _isResponderSharingGps.value = enabled
        val teamId = activeResponderTeamId.value

        if (enabled) {
            locationHelper.startDutyLocationUpdates { lat, lng ->
                viewModelScope.launch(Dispatchers.IO) {
                    remoteIncidentRepository.updateTeamLocation(teamId, lat, lng)
                }
            }
        } else {
            locationHelper.stopDutyLocationUpdates()
        }
    }

    // --- Auth Actions ---

    suspend fun loginWithPassword(email: String, pass: String): Result<Pair<UserSession?, UserRole>> = withContext(Dispatchers.IO) {
        withLoading("Signing in & sending OTP...") {
            _isActionInProgress.value = true
            try {
                val result = remoteAuthRepository.loginWithPassword(email, pass)
                if (result.isSuccess) {
                    val session = result.getOrNull()?.first
                    if (session != null) {
                        sessionManager.saveSession(session)
                        _currentSession.value = session
                        remoteIncidentRepository.refreshAllData()
                        fetchAutoDispatchSetting()
                        socketManager.connectAndJoinStaff(session.token)
                    } else {
                        val reqEmail = result.getOrNull()?.first?.email ?: email
                        sessionManager.savePendingOtp(reqEmail, "LOGIN_MFA")
                    }
                }
                result
            } finally {
                _isActionInProgress.value = false
            }
        }
    }

    suspend fun verifyActiveOtp(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        withLoading("Verifying security code...") {
            _isActionInProgress.value = true
            try {
                val result = remoteAuthRepository.verifyActiveOtp(code)
                if (result.isSuccess) {
                    val session = result.getOrNull()!!
                    sessionManager.saveSession(session)
                    sessionManager.clearPendingOtp()
                    _currentSession.value = session
                    if (session.assignedTeamId != null) {
                        _activeResponderTeamId.value = session.assignedTeamId
                    }
                    remoteIncidentRepository.refreshAllData()
                    fetchAutoDispatchSetting()
                    socketManager.connectAndJoinStaff(session.token)
                    Result.success(Unit)
                } else {
                    Result.failure(result.exceptionOrNull() ?: Exception("Verification failed"))
                }
            } finally {
                _isActionInProgress.value = false
            }
        }
    }

    suspend fun resendActiveOtp() = withContext(Dispatchers.IO) {
        withLoading("Sending new verification code...") {
            remoteAuthRepository.resendActiveOtp()
        }
    }
    
    suspend fun registerCitizen(name: String, email: String, phone: String, pass: String) = withContext(Dispatchers.IO) {
        withLoading("Creating account & sending OTP...") {
            val res = remoteAuthRepository.registerCitizen(name, email, phone, pass)
            if (res.isSuccess) {
                sessionManager.savePendingOtp(email, "CITIZEN_REGISTER")
            }
            res
        }
    }
    
    suspend fun requestPasswordResetOtp(email: String) = withContext(Dispatchers.IO) {
        withLoading("Sending reset code to email...") {
            remoteAuthRepository.requestPasswordResetOtp(email)
        }
    }
    
    suspend fun verifyResetOtp(email: String, code: String) = withContext(Dispatchers.IO) {
        withLoading("Verifying reset code...") {
            remoteAuthRepository.verifyResetOtp(email, code)
        }
    }
    
    suspend fun completePasswordReset(email: String, newPass: String) = withContext(Dispatchers.IO) {
        withLoading("Updating password...") {
            remoteAuthRepository.completePasswordReset(email, newPass)
        }
    }
    
    fun enterGuestSos() {
        val guestSession = UserSession("guest@rapidresq.in", "Guest Citizen", UserRole.CITIZEN, null, UUID.randomUUID().toString(), System.currentTimeMillis() + 8 * 3600 * 1000, true)
        _currentSession.value = guestSession
        clearAiChat()
    }

    fun logout() {
        val token = _currentSession.value?.token
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Signing out securely...") {
                remoteAuthRepository.logout(token)
                toggleResponderLiveGps(false)
                socketManager.disconnect()
                sessionManager.clearSession()
                sessionManager.clearPendingOtp()
                _currentSession.value = null
                clearAiChat()
            }
        }
    }

    // --- Incident & Realtime Operations ---

    fun refreshAllData() {
        if (isRefreshing.compareAndSet(false, true)) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    remoteIncidentRepository.refreshAllData()
                } finally {
                    isRefreshing.set(false)
                }
            }
        }
    }

    fun trackIncident(incidentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            remoteIncidentRepository.trackIncident(incidentId)
            socketManager.connectAndJoinTracking(incidentId)
        }
    }

    fun fetchRecommendedTeams(incidentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = remoteIncidentRepository.fetchRecommendedTeams(incidentId)
            if (res.isSuccess) {
                _recommendedTeams.value = res.getOrDefault(emptyList())
            }
        }
    }

    fun setActiveResponderTeam(teamId: String) { _activeResponderTeamId.value = teamId }

    suspend fun submitIncident(
        category: EmergencyCategory,
        title: String,
        description: String,
        locationText: String,
        gpsLabel: String?,
        severity: IncidentSeverity,
        reporterContact: String,
        media: List<MediaAttachment>?,
        lat: Double? = null,
        lng: Double? = null
    ): String? = withContext(Dispatchers.IO) {
        withLoading("Submitting emergency report...") {
            _isSubmittingReport.value = true
            val result = remoteIncidentRepository.submitIncident(category, title, description, locationText, gpsLabel, severity, reporterContact, media, lat, lng)
            val finalId = if (result.isSuccess) {
                showFeedback("Incident submitted successfully")
                result.getOrNull()
            } else {
                val payload = JSONObject().apply {
                    put("category", category.name)
                    put("title", title)
                    put("description", description)
                    put("locationText", locationText)
                    if (gpsLabel != null) put("gpsLabel", gpsLabel)
                    if (lat != null) put("lat", lat)
                    if (lng != null) put("lng", lng)
                    put("severity", severity.name)
                    put("reporterContact", reporterContact)
                }.toString()

                offlineQueueManager.enqueueAction("SUBMIT_INCIDENT", payload)
                showFeedback("Offline: Saved locally and will auto-sync when connected.")
                "RRQ-QUEUED-${UUID.randomUUID().toString().take(6).uppercase()}"
            }
            _isSubmittingReport.value = false
            finalId
        }
    }

    fun verifyIncident(incidentId: String, actor: String) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Verifying incident...") {
                val res = remoteIncidentRepository.verifyIncident(incidentId, actor)
                if (res.isSuccess) showFeedback("Incident verified") else showFeedback("Verification failed: ${res.exceptionOrNull()?.message}")
                _isActionInProgress.value = false
            }
        }
    }

    fun rejectIncident(incidentId: String, reason: String, actor: String) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Rejecting incident...") {
                val res = remoteIncidentRepository.rejectIncident(incidentId, reason, actor)
                if (res.isSuccess) showFeedback("Incident rejected") else showFeedback("Rejection failed: ${res.exceptionOrNull()?.message}")
                _isActionInProgress.value = false
            }
        }
    }

    fun assignTeam(incidentId: String, teamId: String, actor: String) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Dispatching response unit...") {
                val res = remoteIncidentRepository.assignTeam(incidentId, teamId, actor)
                if (res.isSuccess) {
                    _activeResponderTeamId.value = teamId
                    showFeedback("Team assigned successfully")
                } else showFeedback("Assignment failed: ${res.exceptionOrNull()?.message}")
                _isActionInProgress.value = false
            }
        }
    }

    fun advanceResponderStatus(incidentId: String, actor: String) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Updating unit status...") {
                val res = remoteIncidentRepository.advanceResponderStatus(incidentId, _activeResponderTeamId.value, actor)
                if (res.isSuccess) {
                    showFeedback("Status updated")
                } else {
                    val payload = JSONObject().apply {
                        put("incidentId", incidentId)
                        put("teamId", _activeResponderTeamId.value)
                        put("actor", actor)
                    }.toString()

                    offlineQueueManager.enqueueAction("ADVANCE_STATUS", payload)
                    showFeedback("Offline: Status change queued locally.")
                }
                _isActionInProgress.value = false
            }
        }
    }

    fun addFieldNote(incidentId: String, author: String, text: String) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Adding field note...") {
                val res = remoteIncidentRepository.addFieldNote(incidentId, author, text)
                if (res.isSuccess) showFeedback("Field note added") else showFeedback("Failed to add field note: ${res.exceptionOrNull()?.message}")
                _isActionInProgress.value = false
            }
        }
    }

    fun addResponseTeam(name: String, unitType: String, baseLocation: String) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Adding response unit...") {
                val res = remoteIncidentRepository.addResponseTeam(name, unitType, baseLocation)
                if (res.isSuccess) showFeedback("Team added successfully") else showFeedback("Failed to add team: ${res.exceptionOrNull()?.message}")
                _isActionInProgress.value = false
            }
        }
    }

    fun toggleTeamAvailability(teamId: String) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Updating unit availability...") {
                val res = remoteIncidentRepository.toggleTeamAvailability(teamId)
                if (res.isSuccess) showFeedback("Team availability updated") else showFeedback("Failed to update team: ${res.exceptionOrNull()?.message}")
                _isActionInProgress.value = false
            }
        }
    }

    fun toggleStaffActive(staffId: String) {
        _isActionInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            withLoading("Updating staff access...") {
                val res = remoteIncidentRepository.toggleStaffActive(staffId)
                if (res.isSuccess) showFeedback("Staff access updated") else showFeedback("Failed to update staff: ${res.exceptionOrNull()?.message}")
                _isActionInProgress.value = false
            }
        }
    }

    fun logSecurityEvent(actorRole: String, action: String, details: String) {
        remoteIncidentRepository.logSecurityEvent(actorRole, action, "SYSTEM", details)
    }
}
