package com.rapidresq.app.data.remote

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class RealtimeSocketManager {
    private var socket: Socket? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    var onIncidentsChanged: (() -> Unit)? = null
    var onTrackedIncidentChanged: ((String) -> Unit)? = null
    var onEscalationAlert: ((String, String) -> Unit)? = null
    var onTeamLocationChanged: ((String, Double, Double) -> Unit)? = null
    var onSettingsUpdated: ((Boolean) -> Unit)? = null

    private fun getOrCreateSocket(): Socket {
        if (socket == null) {
            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
            }
            socket = IO.socket(ApiConfig.BASE_URL, options).apply {
                on(Socket.EVENT_CONNECT) {
                    _isConnected.value = true
                }
                on(Socket.EVENT_DISCONNECT) {
                    _isConnected.value = false
                }
                on(Socket.EVENT_CONNECT_ERROR) {
                    _isConnected.value = false
                }
                on("incidents:updated") {
                    onIncidentsChanged?.invoke()
                }
                on("incident:updated") { args ->
                    if (args.isNotEmpty()) {
                        val data = args[0]
                        if (data is JSONObject) {
                            val incidentId = data.optString("id")
                            if (incidentId.isNotEmpty()) {
                                onTrackedIncidentChanged?.invoke(incidentId)
                            }
                        }
                    }
                    onIncidentsChanged?.invoke()
                }
                on("incident:escalated") { args ->
                    if (args.isNotEmpty()) {
                        val data = args[0]
                        if (data is JSONObject) {
                            val incidentId = data.optString("incidentId")
                            val message = data.optString("message")
                            onEscalationAlert?.invoke(incidentId, message)
                        }
                    }
                    onIncidentsChanged?.invoke()
                }
                on("team:location") { args ->
                    if (args.isNotEmpty()) {
                        val data = args[0]
                        if (data is JSONObject) {
                            val teamId = data.optString("teamId")
                            val lat = data.optDouble("lat", 0.0)
                            val lng = data.optDouble("lng", 0.0)
                            if (teamId.isNotEmpty() && lat != 0.0 && lng != 0.0) {
                                onTeamLocationChanged?.invoke(teamId, lat, lng)
                            }
                        }
                    }
                }
                on("settings:updated") { args ->
                    if (args.isNotEmpty()) {
                        val data = args[0]
                        if (data is JSONObject) {
                            val enabled = data.optBoolean("autoDispatchEnabled", false)
                            onSettingsUpdated?.invoke(enabled)
                        }
                    }
                }
            }
        }
        return socket!!
    }

    fun connectAndJoinStaff(jwtToken: String) {
        val s = getOrCreateSocket()
        if (!s.connected()) {
            s.connect()
        }
        val joinPayload = JSONObject().apply {
            put("token", jwtToken)
        }
        s.emit("join:staff", joinPayload)
    }

    fun connectAndJoinTracking(incidentId: String) {
        val s = getOrCreateSocket()
        if (!s.connected()) {
            s.connect()
        }
        val joinPayload = JSONObject().apply {
            put("incidentId", incidentId)
        }
        s.emit("join:track", joinPayload)
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _isConnected.value = false
    }
}
