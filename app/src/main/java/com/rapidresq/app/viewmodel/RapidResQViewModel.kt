package com.rapidresq.app.viewmodel

import androidx.lifecycle.ViewModel
import com.rapidresq.app.data.model.EmergencyCategory
import com.rapidresq.app.data.model.IncidentSeverity
import com.rapidresq.app.data.model.MediaAttachment
import com.rapidresq.app.data.repository.DemoIncidentRepository
import com.rapidresq.app.data.repository.IncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RapidResQViewModel(
    private val repository: IncidentRepository = DemoIncidentRepository()
) : ViewModel() {

    val incidents = repository.incidents
    val teams = repository.teams
    val auditLogs = repository.auditLogs

    private val _activeResponderTeamId = MutableStateFlow("TEAM-BRAVO")
    val activeResponderTeamId: StateFlow<String> = _activeResponderTeamId.asStateFlow()

    fun setActiveResponderTeam(teamId: String) {
        _activeResponderTeamId.value = teamId
    }

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    private fun showStatus(message: String) {
        _statusBanner.value = message
    }

    fun submitIncident(
        category: EmergencyCategory,
        title: String,
        description: String,
        locationText: String,
        gpsLabel: String?,
        severity: IncidentSeverity,
        reporterContact: String,
        media: List<MediaAttachment>?
    ): String? {
        val result = repository.submitIncident(
            category, title, description, locationText, gpsLabel, severity, reporterContact, media
        )
        return if (result.isSuccess) {
            showStatus("Incident submitted successfully")
            result.getOrNull()
        } else {
            showStatus("Failed to submit incident: ${result.exceptionOrNull()?.message}")
            null
        }
    }

    fun verifyIncident(incidentId: String, actor: String) {
        val result = repository.verifyIncident(incidentId, actor)
        if (result.isSuccess) {
            showStatus("Incident verified")
        } else {
            showStatus("Verification failed: ${result.exceptionOrNull()?.message}")
        }
    }

    fun rejectIncident(incidentId: String, reason: String, actor: String) {
        val result = repository.rejectIncident(incidentId, reason, actor)
        if (result.isSuccess) {
            showStatus("Incident rejected")
        } else {
            showStatus("Rejection failed: ${result.exceptionOrNull()?.message}")
        }
    }

    fun assignTeam(incidentId: String, teamId: String, actor: String) {
        val result = repository.assignTeam(incidentId, teamId, actor)
        if (result.isSuccess) {
            _activeResponderTeamId.value = teamId
            showStatus("Team assigned successfully")
        } else {
            showStatus("Assignment failed: ${result.exceptionOrNull()?.message}")
        }
    }

    fun advanceResponderStatus(incidentId: String, actor: String) {
        val result = repository.advanceResponderStatus(incidentId, _activeResponderTeamId.value, actor)
        if (result.isSuccess) {
            showStatus("Status updated")
        } else {
            showStatus("Update failed: ${result.exceptionOrNull()?.message}")
        }
    }

    fun addFieldNote(incidentId: String, author: String, text: String) {
        val result = repository.addFieldNote(incidentId, author, text)
        if (result.isSuccess) {
            showStatus("Field note added")
        } else {
            showStatus("Failed to add field note: ${result.exceptionOrNull()?.message}")
        }
    }
}
