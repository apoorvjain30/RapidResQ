package com.rapidresq.app.data.remote

import android.content.Context
import android.net.Uri
import com.rapidresq.app.data.model.*
import com.rapidresq.app.data.repository.IncidentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

class RemoteIncidentRepository(
    private val context: Context,
    private val tokenProvider: () -> String?
) : IncidentRepository {

    private val client = ApiConfig.okHttpClient

    private val _incidents = MutableStateFlow<List<Incident>>(emptyList())
    override val incidents: StateFlow<List<Incident>> = _incidents.asStateFlow()

    private val _teams = MutableStateFlow<List<ResponseTeam>>(emptyList())
    override val teams: StateFlow<List<ResponseTeam>> = _teams.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditEntry>>(emptyList())
    override val auditLogs: StateFlow<List<AuditEntry>> = _auditLogs.asStateFlow()

    private val _staffAccounts = MutableStateFlow<List<StaffAccount>>(emptyList())
    override val staffAccounts: StateFlow<List<StaffAccount>> = _staffAccounts.asStateFlow()

    override fun logSecurityEvent(actorRole: String, action: String, incidentId: String, details: String) {
        // Logged on server automatically via API responses
    }

    private fun buildRequest(url: String, method: String = "GET", body: RequestBody? = null): Request {
        val builder = Request.Builder().url(url)
        tokenProvider()?.let {
            if (it.isNotBlank()) builder.header("Authorization", "Bearer $it")
        }
        when (method) {
            "POST" -> builder.post(body ?: "".toRequestBody("application/json".toMediaTypeOrNull()))
            "PATCH" -> builder.patch(body ?: "".toRequestBody("application/json".toMediaTypeOrNull()))
            "GET" -> builder.get()
        }
        return builder.build()
    }

    override suspend fun refreshAllData() = withContext(Dispatchers.IO) {
        try {
            fetchIncidents()
            fetchTeams()
            fetchAuditLogs()
            fetchStaff()
        } catch (e: Exception) {
            // Ignore overall refresh errors silently
        }
    }

    private suspend fun fetchIncidents() = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("${ApiConfig.BASE_URL}/api/incidents")
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val array = JSONArray(res.body?.string() ?: "[]")
                    val list = mutableListOf<Incident>()
                    for (i in 0 until array.length()) {
                        list.add(parseIncident(array.getJSONObject(i)))
                    }
                    _incidents.value = list
                }
            }
        } catch (e: Exception) {
            // Silently ignore
        }
    }

    private suspend fun fetchTeams() = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("${ApiConfig.BASE_URL}/api/admin/teams")
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val array = JSONArray(res.body?.string() ?: "[]")
                    val list = mutableListOf<ResponseTeam>()
                    for (i in 0 until array.length()) {
                        list.add(parseTeam(array.getJSONObject(i)))
                    }
                    _teams.value = list
                }
            }
        } catch (e: Exception) {
            // Silently ignore
        }
    }

    private suspend fun fetchAuditLogs() = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("${ApiConfig.BASE_URL}/api/admin/audit")
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val array = JSONArray(res.body?.string() ?: "[]")
                    val list = mutableListOf<AuditEntry>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            AuditEntry(
                                id = obj.optString("id", ""),
                                timestamp = System.currentTimeMillis(),
                                actorRole = obj.optString("actor_role", "SYSTEM"),
                                action = obj.optString("action", ""),
                                incidentId = obj.optString("incident_id", "SYSTEM"),
                                details = obj.optString("details", "")
                            )
                        )
                    }
                    _auditLogs.value = list
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private suspend fun fetchStaff() = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("${ApiConfig.BASE_URL}/api/admin/staff")
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val array = JSONArray(res.body?.string() ?: "[]")
                    val list = mutableListOf<StaffAccount>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val roleStr = obj.optString("role", "CITIZEN")
                        val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.CITIZEN }
                        list.add(
                            StaffAccount(
                                id = obj.optString("id", ""),
                                name = obj.optString("name", ""),
                                email = obj.optString("email", ""),
                                role = role,
                                department = obj.optString("department", ""),
                                assignedUnitName = if (obj.has("assigned_team_id") && !obj.isNull("assigned_team_id")) obj.getString("assigned_team_id") else null,
                                mfaEnabled = obj.optBoolean("mfa_enabled", true),
                                isActive = obj.optBoolean("is_active", true)
                            )
                        )
                    }
                    _staffAccounts.value = list
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override suspend fun trackIncident(incidentId: String): Result<Incident> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("${ApiConfig.BASE_URL}/api/incidents/track/$incidentId")
            client.newCall(req).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) {
                    return@withContext Result.failure(Exception(obj.optString("message", "Incident not found")))
                }
                val incident = parseIncident(obj)
                val current = _incidents.value.toMutableList()
                val index = current.indexOfFirst { it.id == incident.id }
                if (index >= 0) current[index] = incident else current.add(incident)
                _incidents.value = current
                Result.success(incident)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun submitIncident(
        category: EmergencyCategory,
        title: String,
        description: String,
        locationText: String,
        gpsLabel: String?,
        severity: IncidentSeverity,
        reporterContact: String,
        media: List<MediaAttachment>?,
        lat: Double?,
        lng: Double?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            builder.addFormDataPart("category", category.name)
            builder.addFormDataPart("title", title)
            builder.addFormDataPart("description", description)
            builder.addFormDataPart("locationText", locationText)
            if (gpsLabel != null) builder.addFormDataPart("gpsLabel", gpsLabel)
            if (lat != null) builder.addFormDataPart("lat", lat.toString())
            if (lng != null) builder.addFormDataPart("lng", lng.toString())
            builder.addFormDataPart("severity", severity.name)
            builder.addFormDataPart("reporterContact", reporterContact)

            media?.firstOrNull()?.let { attachment ->
                if (attachment.uri.startsWith("content://") || attachment.uri.startsWith("file://")) {
                    try {
                        val uri = Uri.parse(attachment.uri)
                        val mimeType = context.contentResolver.getType(uri) ?: if (attachment.isVideo) "video/mp4" else "image/jpeg"
                        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()

                        if (bytes != null) {
                            val fileName = if (attachment.isVideo) "media.mp4" else "media.jpg"
                            builder.addFormDataPart("media", fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                        }
                    } catch (e: Exception) {
                        // Skip media if reading failed
                    }
                }
            }

            val request = buildRequest("${ApiConfig.BASE_URL}/api/incidents", "POST", builder.build())

            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) {
                    return@withContext Result.failure(Exception(obj.optString("message", "Failed to submit report")))
                }
                val incidentObj = obj.getJSONObject("incident")
                val created = parseIncident(incidentObj)
                
                _incidents.value = listOf(created) + _incidents.value
                Result.success(created.id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyIncident(incidentId: String, actor: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("${ApiConfig.BASE_URL}/api/incidents/$incidentId/verify", "POST")
            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) return@withContext Result.failure(Exception(obj.optString("message", "Verification failed")))
                refreshAllData()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rejectIncident(incidentId: String, reason: String, actor: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("reason", reason) }
            val request = buildRequest("${ApiConfig.BASE_URL}/api/incidents/$incidentId/reject", "POST", json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) return@withContext Result.failure(Exception(obj.optString("message", "Rejection failed")))
                refreshAllData()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun assignTeam(incidentId: String, teamId: String, actor: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("teamId", teamId) }
            val request = buildRequest("${ApiConfig.BASE_URL}/api/incidents/$incidentId/assign", "POST", json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) return@withContext Result.failure(Exception(obj.optString("message", "Assignment failed")))
                refreshAllData()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun advanceResponderStatus(incidentId: String, teamId: String, actor: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("${ApiConfig.BASE_URL}/api/incidents/$incidentId/status", "POST")
            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) return@withContext Result.failure(Exception(obj.optString("message", "Status update failed")))
                refreshAllData()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addFieldNote(incidentId: String, author: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("text", text) }
            val request = buildRequest("${ApiConfig.BASE_URL}/api/incidents/$incidentId/notes", "POST", json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) return@withContext Result.failure(Exception(obj.optString("message", "Note failed")))
                refreshAllData()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addResponseTeam(name: String, unitType: String, baseLocation: String): Result<ResponseTeam> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("name", name)
                put("unitType", unitType)
                put("baseLocation", baseLocation)
            }
            val request = buildRequest("${ApiConfig.BASE_URL}/api/admin/teams", "POST", json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) return@withContext Result.failure(Exception(obj.optString("message", "Team creation failed")))
                val team = parseTeam(obj)
                refreshAllData()
                Result.success(team)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleTeamAvailability(teamId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("${ApiConfig.BASE_URL}/api/admin/teams/$teamId/toggle", "PATCH")
            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) return@withContext Result.failure(Exception(obj.optString("message", "Toggle failed")))
                refreshAllData()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTeamLocation(teamId: String, lat: Double, lng: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("lat", lat)
                put("lng", lng)
            }
            val request = buildRequest("${ApiConfig.BASE_URL}/api/admin/teams/$teamId/location", "PATCH", json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) return@withContext Result.failure(Exception(obj.optString("message", "Location update failed")))
                refreshAllData()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleStaffActive(staffId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("${ApiConfig.BASE_URL}/api/admin/staff/$staffId/toggle", "PATCH")
            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) return@withContext Result.failure(Exception(obj.optString("message", "Staff toggle failed")))
                refreshAllData()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchRecommendedTeams(incidentId: String): Result<List<RankedTeam>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("${ApiConfig.BASE_URL}/api/incidents/$incidentId/recommended-teams")
            client.newCall(request).execute().use { res ->
                val bodyStr = res.body?.string() ?: ""
                val obj = if (bodyStr.isNotBlank()) JSONObject(bodyStr) else JSONObject()
                if (!res.isSuccessful) return@withContext Result.failure(Exception(obj.optString("message", "Failed to fetch teams")))
                
                val recType = obj.optString("recommendedUnitType", "")
                val teamsArray = obj.optJSONArray("teams") ?: JSONArray()
                val list = mutableListOf<RankedTeam>()
                for (i in 0 until teamsArray.length()) {
                    val tObj = teamsArray.getJSONObject(i)
                    val uType = tObj.optString("unitType", tObj.optString("unit_type", ""))
                    val dist = if (tObj.has("distance_km") && !tObj.isNull("distance_km")) tObj.getDouble("distance_km") else null
                    list.add(
                        RankedTeam(
                            id = tObj.getString("id"),
                            name = tObj.getString("name"),
                            unitType = uType,
                            baseLocation = tObj.optString("baseLocation", tObj.optString("base_location", "")),
                            isAvailable = if (tObj.has("isAvailable")) tObj.getBoolean("isAvailable") else tObj.optBoolean("is_available", true),
                            resolvedCount = if (tObj.has("resolvedCount")) tObj.getInt("resolvedCount") else tObj.optInt("resolved_count", 0),
                            distanceKm = dist,
                            isRecommended = uType == recType,
                            lat = if (tObj.has("lat") && !tObj.isNull("lat")) tObj.getDouble("lat") else 22.7196,
                            lng = if (tObj.has("lng") && !tObj.isNull("lng")) tObj.getDouble("lng") else 75.8577
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseIncident(obj: JSONObject): Incident {
        val catStr = obj.optString("category", "PUBLIC_SAFETY")
        val category = try { EmergencyCategory.valueOf(catStr) } catch (e: Exception) { EmergencyCategory.PUBLIC_SAFETY }

        val sevStr = obj.optString("severity", "HIGH")
        val severity = try { IncidentSeverity.valueOf(sevStr) } catch (e: Exception) { IncidentSeverity.HIGH }

        val statStr = obj.optString("status", "REPORTED")
        val status = try { IncidentStatus.valueOf(statStr) } catch (e: Exception) { IncidentStatus.REPORTED }

        val mediaArray = obj.optJSONArray("media")
        val mediaList = mutableListOf<MediaAttachment>()
        if (mediaArray != null) {
            for (i in 0 until mediaArray.length()) {
                val mObj = mediaArray.getJSONObject(i)
                mediaList.add(MediaAttachment(mObj.optString("uri", ""), mObj.optBoolean("isVideo", false)))
            }
        } else if (obj.has("media_url") && !obj.isNull("media_url")) {
            mediaList.add(MediaAttachment(obj.getString("media_url"), obj.optBoolean("media_is_video", false)))
        }

        val timelineArray = obj.optJSONArray("timeline")
        val timelineList = mutableListOf<TimelineEntry>()
        if (timelineArray != null) {
            for (i in 0 until timelineArray.length()) {
                val tObj = timelineArray.getJSONObject(i)
                timelineList.add(
                    TimelineEntry(
                        timestamp = tObj.optLong("timestamp", System.currentTimeMillis()),
                        statusLabel = tObj.optString("statusLabel", tObj.optString("status_label", "")),
                        actor = tObj.optString("actor", "System"),
                        note = if (tObj.has("note") && !tObj.isNull("note")) tObj.getString("note") else null
                    )
                )
            }
        }

        val notesArray = obj.optJSONArray("fieldNotes")
        val notesList = mutableListOf<FieldNote>()
        if (notesArray != null) {
            for (i in 0 until notesArray.length()) {
                val nObj = notesArray.getJSONObject(i)
                notesList.add(
                    FieldNote(
                        timestamp = nObj.optLong("timestamp", System.currentTimeMillis()),
                        author = nObj.optString("author", ""),
                        text = nObj.optString("text", "")
                    )
                )
            }
        }

        return Incident(
            id = obj.getString("id"),
            category = category,
            title = obj.optString("title", "Emergency Report"),
            description = obj.optString("description", ""),
            locationText = obj.optString("locationText", obj.optString("location_text", "")),
            gpsLabel = if (obj.has("gpsLabel") && !obj.isNull("gpsLabel")) obj.getString("gpsLabel") else if (obj.has("gps_label") && !obj.isNull("gps_label")) obj.getString("gps_label") else null,
            lat = if (obj.has("lat") && !obj.isNull("lat")) obj.getDouble("lat") else null,
            lng = if (obj.has("lng") && !obj.isNull("lng")) obj.getDouble("lng") else null,
            severity = severity,
            reporterContact = obj.optString("reporterContact", obj.optString("reporter_contact", "")),
            media = if (mediaList.isNotEmpty()) mediaList else null,
            status = status,
            assignedTeamId = if (obj.has("assignedTeamId") && !obj.isNull("assignedTeamId")) obj.getString("assignedTeamId") else if (obj.has("assigned_team_id") && !obj.isNull("assigned_team_id")) obj.getString("assigned_team_id") else null,
            assignedTeamName = if (obj.has("assignedTeamName") && !obj.isNull("assignedTeamName")) obj.getString("assignedTeamName") else if (obj.has("assigned_team_name") && !obj.isNull("assigned_team_name")) obj.getString("assigned_team_name") else null,
            assignedTeamLat = if (obj.has("assignedTeamLat") && !obj.isNull("assignedTeamLat")) obj.getDouble("assignedTeamLat") else if (obj.has("assigned_team_lat") && !obj.isNull("assigned_team_lat")) obj.getDouble("assigned_team_lat") else null,
            assignedTeamLng = if (obj.has("assignedTeamLng") && !obj.isNull("assignedTeamLng")) obj.getDouble("assignedTeamLng") else if (obj.has("assigned_team_lng") && !obj.isNull("assigned_team_lng")) obj.getDouble("assigned_team_lng") else null,
            rejectionReason = if (obj.has("rejectionReason") && !obj.isNull("rejectionReason")) obj.getString("rejectionReason") else if (obj.has("rejection_reason") && !obj.isNull("rejection_reason")) obj.getString("rejection_reason") else null,
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            possibleDuplicateOf = if (obj.has("possibleDuplicateOf") && !obj.isNull("possibleDuplicateOf")) obj.getString("possibleDuplicateOf") else if (obj.has("possible_duplicate_of") && !obj.isNull("possible_duplicate_of")) obj.getString("possible_duplicate_of") else null,
            timeline = timelineList,
            fieldNotes = notesList
        )
    }

    private fun parseTeam(obj: JSONObject): ResponseTeam {
        return ResponseTeam(
            id = obj.getString("id"),
            name = obj.getString("name"),
            unitType = obj.optString("unitType", obj.optString("unit_type", "")),
            baseLocation = obj.optString("baseLocation", obj.optString("base_location", "")),
            isAvailable = if (obj.has("isAvailable")) obj.getBoolean("isAvailable") else obj.optBoolean("is_available", true),
            resolvedCount = if (obj.has("resolvedCount")) obj.getInt("resolvedCount") else obj.optInt("resolved_count", 0),
            lat = if (obj.has("lat") && !obj.isNull("lat")) obj.getDouble("lat") else 22.7196,
            lng = if (obj.has("lng") && !obj.isNull("lng")) obj.getDouble("lng") else 75.8577
        )
    }
}
