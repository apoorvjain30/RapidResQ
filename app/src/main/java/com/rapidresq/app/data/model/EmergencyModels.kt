package com.rapidresq.app.data.model

enum class UserRole(val label: String, val subtitle: String) {
    CITIZEN("Citizen", "Report incidents quickly"),
    DISPATCHER("Dispatcher", "Verify and assign resources"),
    RESPONDER("First Responder", "Accept and resolve incidents")
}

enum class EmergencyCategory(
    val label: String,
    val recommendedUnitType: String,
    val safetyGuidance: List<String>
) {
    ROAD_ACCIDENT(
        "Road Accident",
        "Traffic & Rescue",
        listOf("Move to a safe area if possible", "Do not move severely injured persons", "Turn on hazard lights")
    ),
    FIRE_HAZARD(
        "Fire / Hazard",
        "Fire & Hazmat",
        listOf("Evacuate immediately", "Do not use elevators", "Stay low to avoid smoke")
    ),
    MEDICAL(
        "Medical Emergency",
        "Medical EMS",
        listOf("Check for breathing and pulse", "Apply pressure to severe bleeding", "Do not give food or water")
    ),
    PUBLIC_SAFETY(
        "Public Safety",
        "Security Response",
        listOf("Move to a secure location", "Avoid confrontation", "Observe and report details")
    )
}

enum class IncidentSeverity {
    CRITICAL, HIGH, MEDIUM, LOW
}

enum class IncidentStatus(val stepIndex: Int) {
    REPORTED(0),
    VERIFIED(1),
    ASSIGNED(2),
    ACCEPTED(3),
    EN_ROUTE(4),
    ON_SITE(5),
    RESPONDING(6),
    RESOLVED(7),
    REJECTED(-1);

    fun nextResponderStep(): IncidentStatus? {
        return when (this) {
            ASSIGNED -> ACCEPTED
            ACCEPTED -> EN_ROUTE
            EN_ROUTE -> ON_SITE
            ON_SITE -> RESPONDING
            RESPONDING -> RESOLVED
            else -> null
        }
    }
}

data class MediaAttachment(
    val uri: String,
    val isVideo: Boolean
)

data class TimelineEntry(
    val timestamp: Long,
    val statusLabel: String,
    val actor: String,
    val note: String? = null
)

data class FieldNote(
    val timestamp: Long,
    val author: String,
    val text: String
)

data class AuditEntry(
    val id: String,
    val timestamp: Long,
    val actorRole: String,
    val action: String,
    val incidentId: String,
    val details: String
)

data class ResponseTeam(
    val id: String,
    val name: String,
    val unitType: String,
    val baseLocation: String,
    val isAvailable: Boolean,
    val resolvedCount: Int
)

data class Incident(
    val id: String,
    val category: EmergencyCategory,
    val title: String,
    val description: String,
    val locationText: String,
    val gpsLabel: String? = null,
    val severity: IncidentSeverity,
    val reporterContact: String,
    val media: List<MediaAttachment>? = null,
    val status: IncidentStatus,
    val assignedTeamId: String? = null,
    val assignedTeamName: String? = null,
    val rejectionReason: String? = null,
    val createdAt: Long,
    val timeline: List<TimelineEntry> = emptyList(),
    val fieldNotes: List<FieldNote> = emptyList()
)
