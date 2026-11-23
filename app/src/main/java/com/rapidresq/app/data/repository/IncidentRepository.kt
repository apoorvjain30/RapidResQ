package com.rapidresq.app.data.repository

import com.rapidresq.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.SecureRandom
import java.util.UUID

interface IncidentRepository {
    val incidents: StateFlow<List<Incident>>
    val teams: StateFlow<List<ResponseTeam>>
    val auditLogs: StateFlow<List<AuditEntry>>

    fun submitIncident(
        category: EmergencyCategory,
        title: String,
        description: String,
        locationText: String,
        gpsLabel: String?,
        severity: IncidentSeverity,
        reporterContact: String,
        media: List<MediaAttachment>?
    ): Result<String>

    fun verifyIncident(incidentId: String, actor: String): Result<Unit>
    fun rejectIncident(incidentId: String, reason: String, actor: String): Result<Unit>
    fun assignTeam(incidentId: String, teamId: String, actor: String): Result<Unit>
    fun advanceResponderStatus(incidentId: String, teamId: String, actor: String): Result<Unit>
    fun addFieldNote(incidentId: String, author: String, text: String): Result<Unit>
}

class DemoIncidentRepository : IncidentRepository {
    // Phase 1 in-memory demo store

    private val _incidents = MutableStateFlow<List<Incident>>(emptyList())
    override val incidents: StateFlow<List<Incident>> = _incidents.asStateFlow()

    private val _teams = MutableStateFlow<List<ResponseTeam>>(emptyList())
    override val teams: StateFlow<List<ResponseTeam>> = _teams.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditEntry>>(emptyList())
    override val auditLogs: StateFlow<List<AuditEntry>> = _auditLogs.asStateFlow()

    private val secureRandom = SecureRandom()
    private val allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // avoid 0/O/1/I

    init {
        seedDemoData()
    }

    private fun generateTrackingId(): String {
        val chars = (1..6).map { allowedChars[secureRandom.nextInt(allowedChars.length)] }.joinToString("")
        return "RRQ-$chars"
    }

    private fun logAudit(actorRole: String, action: String, incidentId: String, details: String) {
        val entry = AuditEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            actorRole = actorRole,
            action = action,
            incidentId = incidentId,
            details = details
        )
        _auditLogs.update { it + entry }
    }

    private fun updateIncident(id: String, updateOp: (Incident) -> Incident): Result<Incident> {
        var updatedIncident: Incident? = null
        _incidents.update { list ->
            list.map { inc ->
                if (inc.id == id) {
                    val newInc = updateOp(inc)
                    updatedIncident = newInc
                    newInc
                } else {
                    inc
                }
            }
        }
        return updatedIncident?.let { Result.success(it) } ?: Result.failure(Exception("Incident not found"))
    }

    private fun updateTeam(id: String, updateOp: (ResponseTeam) -> ResponseTeam): Result<ResponseTeam> {
        var updatedTeam: ResponseTeam? = null
        _teams.update { list ->
            list.map { team ->
                if (team.id == id) {
                    val newTeam = updateOp(team)
                    updatedTeam = newTeam
                    newTeam
                } else {
                    team
                }
            }
        }
        return updatedTeam?.let { Result.success(it) } ?: Result.failure(Exception("Team not found"))
    }

    override fun submitIncident(
        category: EmergencyCategory,
        title: String,
        description: String,
        locationText: String,
        gpsLabel: String?,
        severity: IncidentSeverity,
        reporterContact: String,
        media: List<MediaAttachment>?
    ): Result<String> {
        val id = generateTrackingId()
        val now = System.currentTimeMillis()
        val incident = Incident(
            id = id,
            category = category,
            title = title,
            description = description,
            locationText = locationText,
            gpsLabel = gpsLabel,
            severity = severity,
            reporterContact = reporterContact,
            media = media,
            status = IncidentStatus.REPORTED,
            createdAt = now,
            timeline = listOf(TimelineEntry(now, "Reported", "Citizen"))
        )
        _incidents.update { it + incident }
        logAudit(UserRole.CITIZEN.label, "SUBMIT", id, "Incident reported")
        return Result.success(id)
    }

    override fun verifyIncident(incidentId: String, actor: String): Result<Unit> {
        val incident = _incidents.value.find { it.id == incidentId } ?: return Result.failure(Exception("Incident not found"))
        if (incident.status != IncidentStatus.REPORTED) {
            return Result.failure(Exception("Only REPORTED incidents can be verified"))
        }
        
        val now = System.currentTimeMillis()
        val res = updateIncident(incidentId) {
            it.copy(
                status = IncidentStatus.VERIFIED,
                timeline = it.timeline + TimelineEntry(now, "Verified", actor)
            )
        }
        if (res.isSuccess) {
            logAudit(UserRole.DISPATCHER.label, "VERIFY", incidentId, "Incident verified")
            return Result.success(Unit)
        }
        return Result.failure(Exception("Update failed"))
    }

    override fun rejectIncident(incidentId: String, reason: String, actor: String): Result<Unit> {
        val incident = _incidents.value.find { it.id == incidentId } ?: return Result.failure(Exception("Incident not found"))
        if (incident.status != IncidentStatus.REPORTED) {
            return Result.failure(Exception("Only REPORTED incidents can be rejected"))
        }

        val now = System.currentTimeMillis()
        val res = updateIncident(incidentId) {
            it.copy(
                status = IncidentStatus.REJECTED,
                rejectionReason = reason,
                timeline = it.timeline + TimelineEntry(now, "Rejected", actor, reason)
            )
        }
        if (res.isSuccess) {
            logAudit(UserRole.DISPATCHER.label, "REJECT", incidentId, "Incident rejected: $reason")
            return Result.success(Unit)
        }
        return Result.failure(Exception("Update failed"))
    }

    override fun assignTeam(incidentId: String, teamId: String, actor: String): Result<Unit> {
        val incident = _incidents.value.find { it.id == incidentId } ?: return Result.failure(Exception("Incident not found"))
        if (incident.status != IncidentStatus.VERIFIED) {
            return Result.failure(Exception("Only VERIFIED incidents can be assigned"))
        }
        val team = _teams.value.find { it.id == teamId } ?: return Result.failure(Exception("Team not found"))
        if (!team.isAvailable) {
            return Result.failure(Exception("Team is not available"))
        }

        val now = System.currentTimeMillis()
        val teamRes = updateTeam(teamId) { it.copy(isAvailable = false) }
        if (teamRes.isFailure) return Result.failure(Exception("Failed to update team"))
        
        val incRes = updateIncident(incidentId) {
            it.copy(
                status = IncidentStatus.ASSIGNED,
                assignedTeamId = team.id,
                assignedTeamName = team.name,
                timeline = it.timeline + TimelineEntry(now, "Assigned to ${team.name}", actor)
            )
        }
        if (incRes.isSuccess) {
            logAudit(UserRole.DISPATCHER.label, "ASSIGN", incidentId, "Assigned to ${team.name}")
            return Result.success(Unit)
        }
        return Result.failure(Exception("Update failed"))
    }

    override fun advanceResponderStatus(incidentId: String, teamId: String, actor: String): Result<Unit> {
        val incident = _incidents.value.find { it.id == incidentId } ?: return Result.failure(Exception("Incident not found"))
        
        if (incident.assignedTeamId != teamId) {
            logAudit(UserRole.RESPONDER.label, "ACCESS_DENIED_403", incidentId, "Team $teamId attempted to update status")
            return Result.failure(Exception("ACCESS_DENIED_403: Not assigned to this incident"))
        }

        val nextStatus = incident.status.nextResponderStep()
            ?: return Result.failure(Exception("Cannot advance from ${incident.status}"))

        val now = System.currentTimeMillis()
        val incRes = updateIncident(incidentId) {
            it.copy(
                status = nextStatus,
                timeline = it.timeline + TimelineEntry(now, "Status updated to $nextStatus", actor)
            )
        }

        if (incRes.isSuccess) {
            logAudit(UserRole.RESPONDER.label, "STATUS_UPDATE", incidentId, "Advanced to $nextStatus")
            if (nextStatus == IncidentStatus.RESOLVED) {
                updateTeam(teamId) { it.copy(isAvailable = true, resolvedCount = it.resolvedCount + 1) }
                logAudit(UserRole.RESPONDER.label, "RESOLVE", incidentId, "Incident resolved")
            }
            return Result.success(Unit)
        }
        return Result.failure(Exception("Update failed"))
    }

    override fun addFieldNote(incidentId: String, author: String, text: String): Result<Unit> {
        val now = System.currentTimeMillis()
        val note = FieldNote(now, author, text)
        val res = updateIncident(incidentId) {
            it.copy(fieldNotes = it.fieldNotes + note)
        }
        if (res.isSuccess) {
            logAudit(UserRole.RESPONDER.label, "FIELD_NOTE", incidentId, "Note added by $author")
            return Result.success(Unit)
        }
        return Result.failure(Exception("Incident not found"))
    }

    private fun seedDemoData() {
        _teams.value = listOf(
            ResponseTeam("TEAM-ALPHA", "Unit Alpha", "Medical EMS", "Indore Hospital", true, 12),
            ResponseTeam("TEAM-BRAVO", "Unit Bravo", "Fire & Hazmat", "Vijay Nagar Fire Station", false, 45),
            ResponseTeam("TEAM-CHARLIE", "Unit Charlie", "Traffic & Rescue", "Bhawarkua Square", true, 30),
            ResponseTeam("TEAM-DELTA", "Unit Delta", "Security Response", "Palasia Police Station", true, 18)
        )

        val now = System.currentTimeMillis()
        val hour = 3600_000L
        
        val inc1Id = generateTrackingId()
        val inc1 = Incident(
            id = inc1Id,
            category = EmergencyCategory.FIRE_HAZARD,
            title = "Vijay Nagar Commercial Fire",
            description = "Heavy smoke spotted on 3rd floor of commercial complex",
            locationText = "Vijay Nagar Square, near Mall",
            gpsLabel = "22.7533, 75.8937",
            severity = IncidentSeverity.CRITICAL,
            reporterContact = "9876543210",
            status = IncidentStatus.EN_ROUTE,
            assignedTeamId = "TEAM-BRAVO",
            assignedTeamName = "Unit Bravo",
            createdAt = now - 2 * hour,
            timeline = listOf(
                TimelineEntry(now - 2 * hour, "Reported", "Citizen"),
                TimelineEntry(now - (1.9 * hour).toLong(), "Verified", "Dispatcher"),
                TimelineEntry(now - (1.8 * hour).toLong(), "Assigned to Unit Bravo", "Dispatcher"),
                TimelineEntry(now - (1.7 * hour).toLong(), "Status updated to ACCEPTED", "Unit Bravo"),
                TimelineEntry(now - (1.6 * hour).toLong(), "Status updated to EN_ROUTE", "Unit Bravo")
            )
        )
        
        val inc2Id = generateTrackingId()
        val inc2 = Incident(
            id = inc2Id,
            category = EmergencyCategory.ROAD_ACCIDENT,
            title = "Multiple Vehicle Collision",
            description = "Two cars and a bike involved, traffic blocked",
            locationText = "Bhawarkua Road",
            gpsLabel = "22.6953, 75.8679",
            severity = IncidentSeverity.HIGH,
            reporterContact = "9876543211",
            status = IncidentStatus.VERIFIED,
            createdAt = now - hour,
            timeline = listOf(
                TimelineEntry(now - hour, "Reported", "Citizen"),
                TimelineEntry(now - (0.5 * hour).toLong(), "Verified", "Dispatcher")
            )
        )

        val inc3Id = generateTrackingId()
        val inc3 = Incident(
            id = inc3Id,
            category = EmergencyCategory.MEDICAL,
            title = "Heart Attack Suspected",
            description = "Elderly male unconscious, breathing shallow",
            locationText = "Palasia",
            gpsLabel = "22.7231, 75.8920",
            severity = IncidentSeverity.CRITICAL,
            reporterContact = "9876543212",
            status = IncidentStatus.RESOLVED,
            assignedTeamId = "TEAM-ALPHA",
            assignedTeamName = "Unit Alpha",
            createdAt = now - 5 * hour,
            timeline = listOf(
                TimelineEntry(now - 5 * hour, "Reported", "Citizen"),
                TimelineEntry(now - (4.9 * hour).toLong(), "Verified", "Dispatcher"),
                TimelineEntry(now - (4.8 * hour).toLong(), "Assigned to Unit Alpha", "Dispatcher"),
                TimelineEntry(now - (4.7 * hour).toLong(), "Status updated to ACCEPTED", "Unit Alpha"),
                TimelineEntry(now - (4.5 * hour).toLong(), "Status updated to EN_ROUTE", "Unit Alpha"),
                TimelineEntry(now - (4.2 * hour).toLong(), "Status updated to ON_SITE", "Unit Alpha"),
                TimelineEntry(now - 3 * hour, "Status updated to RESPONDING", "Unit Alpha"),
                TimelineEntry(now - 2 * hour, "Status updated to RESOLVED", "Unit Alpha")
            )
        )

        _incidents.value = listOf(inc1, inc2, inc3)
    }
}
