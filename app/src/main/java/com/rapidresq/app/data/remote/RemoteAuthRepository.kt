package com.rapidresq.app.data.remote

import com.rapidresq.app.data.model.UserRole
import com.rapidresq.app.data.repository.IncidentRepository
import com.rapidresq.app.data.security.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

data class RemoteOtpChallenge(
    val email: String,
    val purpose: String,
    val role: UserRole
)

class RemoteAuthRepository(
    private val incidentRepo: IncidentRepository
) {
    private val client = ApiConfig.okHttpClient
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _activeOtpChallenge = MutableStateFlow<RemoteOtpChallenge?>(null)
    val activeOtpChallenge: StateFlow<RemoteOtpChallenge?> = _activeOtpChallenge.asStateFlow()

    private val _simulatedOtpNotice = MutableStateFlow<String?>(null)
    val simulatedOtpNotice: StateFlow<String?> = _simulatedOtpNotice.asStateFlow()
    
    private val _hasPendingOtpChallenge = MutableStateFlow(false)
    val hasPendingOtpChallenge: StateFlow<Boolean> = _hasPendingOtpChallenge.asStateFlow()

    suspend fun loginWithPassword(email: String, pass: String): Result<Pair<UserSession?, UserRole>> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("email", email)
                put("password", pass)
            }
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/auth/login")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val jsonObj = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()

                if (!response.isSuccessful) {
                    val errorMsg = jsonObj.optString("message", "Login failed")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                if (jsonObj.optBoolean("requiresOtp", false)) {
                    val reqEmail = jsonObj.optString("email", email)
                    val roleStr = jsonObj.optString("role", "CITIZEN")
                    val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.CITIZEN }
                    val purpose = jsonObj.optString("purpose", "LOGIN_MFA")
                    
                    val delivery = jsonObj.optJSONObject("delivery")
                    val deliveredVia = delivery?.optString("deliveredVia", "") ?: ""
                    val hint = delivery?.optString("devOtpHint", "") ?: ""
                    
                    if (deliveredVia == "simulated" && hint.isNotBlank()) {
                        _simulatedOtpNotice.value = "OTP Delivery Preview: $hint"
                    } else if (deliveredVia == "smtp") {
                        _simulatedOtpNotice.value = "6-digit verification code sent to $reqEmail. Check Inbox or Spam."
                    } else {
                        _simulatedOtpNotice.value = null
                    }

                    _activeOtpChallenge.value = RemoteOtpChallenge(reqEmail, purpose, role)
                    _hasPendingOtpChallenge.value = true
                    return@withContext Result.success(Pair(null, role))
                } else {
                    val token = jsonObj.optString("token")
                    val userObj = jsonObj.getJSONObject("user")
                    val userRole = UserRole.valueOf(userObj.getString("role"))
                    
                    val session = UserSession(
                        email = userObj.getString("email"),
                        name = userObj.getString("name"),
                        role = userRole,
                        assignedTeamId = if (userObj.has("assigned_team_id") && !userObj.isNull("assigned_team_id")) userObj.getString("assigned_team_id") else null,
                        token = token,
                        expiresAtEpochMs = System.currentTimeMillis() + 8 * 3600 * 1000,
                        isGuest = false
                    )
                    return@withContext Result.success(Pair(session, userRole))
                }
            }
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.localizedMessage}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyActiveOtp(code: String): Result<UserSession> = withContext(Dispatchers.IO) {
        val challenge = _activeOtpChallenge.value ?: return@withContext Result.failure(Exception("No active OTP request"))
        try {
            val json = JSONObject().apply {
                put("email", challenge.email)
                put("code", code)
                put("purpose", challenge.purpose)
            }
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/auth/verify-otp")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val jsonObj = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()

                if (!response.isSuccessful) {
                    val errorMsg = jsonObj.optString("message", "Invalid verification code")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val token = jsonObj.optString("token")
                val userObj = jsonObj.getJSONObject("user")
                val userRole = UserRole.valueOf(userObj.getString("role"))

                _activeOtpChallenge.value = null
                _hasPendingOtpChallenge.value = false
                _simulatedOtpNotice.value = null

                val session = UserSession(
                    email = userObj.getString("email"),
                    name = userObj.getString("name"),
                    role = userRole,
                    assignedTeamId = if (userObj.has("assigned_team_id") && !userObj.isNull("assigned_team_id")) userObj.getString("assigned_team_id") else null,
                    token = token,
                    expiresAtEpochMs = System.currentTimeMillis() + 8 * 3600 * 1000,
                    isGuest = false
                )
                Result.success(session)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resendActiveOtp(): Result<Unit> = withContext(Dispatchers.IO) {
        val challenge = _activeOtpChallenge.value ?: return@withContext Result.failure(Exception("No active OTP request"))
        try {
            val json = JSONObject().apply {
                put("email", challenge.email)
                put("purpose", challenge.purpose)
            }
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/auth/resend-otp")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val jsonObj = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()

                if (!response.isSuccessful) {
                    val errorMsg = jsonObj.optString("message", "Resend OTP failed")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val delivery = jsonObj.optJSONObject("delivery")
                val deliveredVia = delivery?.optString("deliveredVia", "") ?: ""
                val hint = delivery?.optString("devOtpHint", "") ?: ""
                
                if (deliveredVia == "simulated" && hint.isNotBlank()) {
                    _simulatedOtpNotice.value = "OTP Delivery Preview: $hint"
                } else if (deliveredVia == "smtp") {
                    _simulatedOtpNotice.value = "6-digit verification code sent to ${challenge.email}. Check Inbox or Spam."
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerCitizen(name: String, email: String, phone: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("name", name)
                put("email", email)
                put("phone", phone)
                put("password", pass)
            }
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/auth/register-citizen")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val jsonObj = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()

                if (!response.isSuccessful) {
                    val errorMsg = jsonObj.optString("message", "Registration failed")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val reqEmail = jsonObj.optString("email", email)
                val purpose = jsonObj.optString("purpose", "CITIZEN_REGISTER")

                val delivery = jsonObj.optJSONObject("delivery")
                val deliveredVia = delivery?.optString("deliveredVia", "") ?: ""
                val hint = delivery?.optString("devOtpHint", "") ?: ""
                
                if (deliveredVia == "simulated" && hint.isNotBlank()) {
                    _simulatedOtpNotice.value = "OTP Delivery Preview: $hint"
                } else if (deliveredVia == "smtp") {
                    _simulatedOtpNotice.value = "6-digit verification code sent to $reqEmail. Check Inbox or Spam."
                }

                _activeOtpChallenge.value = RemoteOtpChallenge(reqEmail, purpose, UserRole.CITIZEN)
                _hasPendingOtpChallenge.value = true
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestPasswordResetOtp(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("email", email) }
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/auth/forgot-password")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val jsonObj = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()

                if (!response.isSuccessful) {
                    val errorMsg = jsonObj.optString("message", "Request failed")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val delivery = jsonObj.optJSONObject("delivery")
                val deliveredVia = delivery?.optString("deliveredVia", "") ?: ""
                val hint = delivery?.optString("devOtpHint", "") ?: ""
                
                if (deliveredVia == "simulated" && hint.isNotBlank()) {
                    _simulatedOtpNotice.value = "OTP Delivery Preview: $hint"
                } else if (deliveredVia == "smtp") {
                    _simulatedOtpNotice.value = "6-digit verification code sent to $email. Check Inbox or Spam."
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyResetOtp(email: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("email", email)
                put("code", code)
            }
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/auth/verify-reset-otp")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val jsonObj = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()

                if (!response.isSuccessful) {
                    val errorMsg = jsonObj.optString("message", "Invalid reset OTP")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completePasswordReset(email: String, newPass: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("email", email)
                put("newPassword", newPass)
            }
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/auth/reset-password")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val jsonObj = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()

                if (!response.isSuccessful) {
                    val errorMsg = jsonObj.optString("message", "Password reset failed")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                _simulatedOtpNotice.value = null
                _hasPendingOtpChallenge.value = false
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(token: String?): Result<Unit> = withContext(Dispatchers.IO) {
        if (token == null) return@withContext Result.success(Unit)
        try {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/auth/logout")
                .header("Authorization", "Bearer $token")
                .post("".toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }
    fun clearPendingOtp() {
        _activeOtpChallenge.value = null
        _hasPendingOtpChallenge.value = false
        _simulatedOtpNotice.value = null
    }
}
