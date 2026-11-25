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
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.UUID

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

    val socketManager = RealtimeSocketManager().apply {
        onIncidentsChanged = {
            viewModelScope.launch(Dispatchers.IO) {
                remoteIncidentRepository.refreshAllData()
            }
        }
        onTrackedIncidentChanged = { incidentId ->
            viewModelScope.launch(Dispatchers.IO) {
                remoteIncidentRepository.trackIncident(incidentId)
            }
        }
        onEscalationAlert = { incidentId, message ->
            showStatus("ESCALATION ALERT: $message")
            notificationHelper.showAlert("Dispatch Escalated", message)
            viewModelScope.launch(Dispatchers.IO) {
                remoteIncidentRepository.refreshAllData()
            }
        }
        onTeamLocationChanged = { teamId, lat, lng ->
            viewModelScope.launch(Dispatchers.IO) {
                remoteIncidentRepository.refreshAllData()
            }
        }
    }

    val isCloudConnected = socketManager.isConnected

    val incidents = incidentRepository.incidents
    val teams = incidentRepository.teams
    val auditLogs = incidentRepository.auditLogs
    val staffAccounts = incidentRepository.staffAccounts

    private val _recommendedTeams = MutableStateFlow<List<RankedTeam>>(emptyList())
    val recommendedTeams: StateFlow<List<RankedTeam>> = _recommendedTeams.asStateFlow()

    val activeOtpChallenge = remoteAuthRepository.activeOtpChallenge
    val simulatedOtpNotice = remoteAuthRepository.simulatedOtpNotice

    private val _activeResponderTeamId = MutableStateFlow("TEAM-BRAVO")
    val activeResponderTeamId: StateFlow<String> = _activeResponderTeamId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isResponderSharingGps = MutableStateFlow(false)
    val isResponderSharingGps: StateFlow<Boolean> = _isResponderSharingGps.asStateFlow()

    init {
        val restored = sessionManager.getSession()
        if (restored != null) {
            _currentSession.value = restored
            if (restored.assignedTeamId != null) {
                _activeResponderTeamId.value = restored.assignedTeamId
            }
            if (!restored.isGuest) {
                viewModelScope.launch(Dispatchers.IO) {
                    remoteIncidentRepository.refreshAllData()
                    socketManager.connectAndJoinStaff(restored.token)
                }
            }
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

        // Replay queue when online state transitions back to true
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
                        showStatus("Synced $count offline action(s) to cloud")
                        remoteIncidentRepository.refreshAllData()
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

    fun loginWithPassword(email: String, pass: String): Result<Pair<UserSession?, UserRole>> = runBlocking(Dispatchers.IO) {
        val result = remoteAuthRepository.loginWithPassword(email, pass)
        if (result.isSuccess) {
            val session = result.getOrNull()?.first
            if (session != null) {
                sessionManager.saveSession(session)
                _currentSession.value = session
                viewModelScope.launch(Dispatchers.IO) {
                    remoteIncidentRepository.refreshAllData()
                    socketManager.connectAndJoinStaff(session.token)
                }
            }
        }
        result
    }

    fun verifyActiveOtp(code: String): Result<Unit> = runBlocking(Dispatchers.IO) {
        val result = remoteAuthRepository.verifyActiveOtp(code)
        if (result.isSuccess) {
            val session = result.getOrNull()!!
            sessionManager.saveSession(session)
            _currentSession.value = session
            if (session.assignedTeamId != null) {
                _activeResponderTeamId.value = session.assignedTeamId
            }
            viewModelScope.launch(Dispatchers.IO) {
                remoteIncidentRepository.refreshAllData()
                socketManager.connectAndJoinStaff(session.token)
            }
            return@runBlocking Result.success(Unit)
        }
        return@runBlocking Result.failure(result.exceptionOrNull() ?: Exception("Verification failed"))
    }

    fun resendActiveOtp() = runBlocking(Dispatchers.IO) { remoteAuthRepository.resendActiveOtp() }
    
    fun registerCitizen(name: String, email: String, phone: String, pass: String) = runBlocking(Dispatchers.IO) {
        remoteAuthRepository.registerCitizen(name, email, phone, pass)
    }
    
    fun requestPasswordResetOtp(email: String) = runBlocking(Dispatchers.IO) { remoteAuthRepository.requestPasswordResetOtp(email) }
    
    fun verifyResetOtp(email: String, code: String) = runBlocking(Dispatchers.IO) { remoteAuthRepository.verifyResetOtp(email, code) }
    
    fun completePasswordReset(email: String, newPass: String) = runBlocking(Dispatchers.IO) { remoteAuthRepository.completePasswordReset(email, newPass) }
    
    fun enterGuestSos() {
        val guestSession = UserSession("guest@rapidresq.in", "Guest Citizen", UserRole.CITIZEN, null, UUID.randomUUID().toString(), System.currentTimeMillis() + 8 * 3600 * 1000, true)
        _currentSession.value = guestSession
    }

    fun logout() {
        val token = _currentSession.value?.token
        viewModelScope.launch(Dispatchers.IO) {
            remoteAuthRepository.logout(token)
        }
        toggleResponderLiveGps(false)
        socketManager.disconnect()
        sessionManager.clearSession()
        _currentSession.value = null
    }

    // --- Incident & Realtime Operations ---

    fun refreshAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            remoteIncidentRepository.refreshAllData()
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

    fun clearStatusBanner() { _statusBanner.value = null }
    private fun showStatus(message: String) { _statusBanner.value = message }
    fun setActiveResponderTeam(teamId: String) { _activeResponderTeamId.value = teamId }

    fun submitIncident(
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
    ): String? = runBlocking(Dispatchers.IO) {
        val result = remoteIncidentRepository.submitIncident(category, title, description, locationText, gpsLabel, severity, reporterContact, media, lat, lng)
        if (result.isSuccess) {
            showStatus("Incident submitted successfully")
            result.getOrNull()
        } else {
            // Queue offline if network error
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
            showStatus("Offline: Saved locally and will auto-sync when connected.")
            "RRQ-QUEUED-${UUID.randomUUID().toString().take(6).uppercase()}"
        }
    }

    fun verifyIncident(incidentId: String, actor: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = remoteIncidentRepository.verifyIncident(incidentId, actor)
            if (res.isSuccess) showStatus("Incident verified") else showStatus("Verification failed: ${res.exceptionOrNull()?.message}")
        }
    }

    fun rejectIncident(incidentId: String, reason: String, actor: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = remoteIncidentRepository.rejectIncident(incidentId, reason, actor)
            if (res.isSuccess) showStatus("Incident rejected") else showStatus("Rejection failed: ${res.exceptionOrNull()?.message}")
        }
    }

    fun assignTeam(incidentId: String, teamId: String, actor: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = remoteIncidentRepository.assignTeam(incidentId, teamId, actor)
            if (res.isSuccess) {
                _activeResponderTeamId.value = teamId
                showStatus("Team assigned successfully")
            } else showStatus("Assignment failed: ${res.exceptionOrNull()?.message}")
        }
    }

    fun advanceResponderStatus(incidentId: String, actor: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = remoteIncidentRepository.advanceResponderStatus(incidentId, _activeResponderTeamId.value, actor)
            if (res.isSuccess) {
                showStatus("Status updated")
            } else {
                // Queue offline if network error
                val payload = JSONObject().apply {
                    put("incidentId", incidentId)
                    put("teamId", _activeResponderTeamId.value)
                    put("actor", actor)
                }.toString()

                offlineQueueManager.enqueueAction("ADVANCE_STATUS", payload)
                showStatus("Offline: Status change queued locally.")
            }
        }
    }

    fun addFieldNote(incidentId: String, author: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = remoteIncidentRepository.addFieldNote(incidentId, author, text)
            if (res.isSuccess) showStatus("Field note added") else showStatus("Failed to add field note: ${res.exceptionOrNull()?.message}")
        }
    }

    fun addResponseTeam(name: String, unitType: String, baseLocation: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = remoteIncidentRepository.addResponseTeam(name, unitType, baseLocation)
            if (res.isSuccess) showStatus("Team added successfully") else showStatus("Failed to add team: ${res.exceptionOrNull()?.message}")
        }
    }

    fun toggleTeamAvailability(teamId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = remoteIncidentRepository.toggleTeamAvailability(teamId)
            if (res.isSuccess) showStatus("Team availability updated") else showStatus("Failed to update team: ${res.exceptionOrNull()?.message}")
        }
    }

    fun toggleStaffActive(staffId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = remoteIncidentRepository.toggleStaffActive(staffId)
            if (res.isSuccess) showStatus("Staff access updated") else showStatus("Failed to update staff: ${res.exceptionOrNull()?.message}")
        }
    }

    fun logSecurityEvent(actorRole: String, action: String, details: String) {
        remoteIncidentRepository.logSecurityEvent(actorRole, action, "SYSTEM", details)
    }
}
