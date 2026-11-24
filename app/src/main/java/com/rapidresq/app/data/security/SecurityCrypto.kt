package com.rapidresq.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.rapidresq.app.data.model.UserRole
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256

    fun hashPassword(password: String, saltBase64: String? = null): String {
        val salt = if (saltBase64 != null) {
            Base64.decode(saltBase64, Base64.DEFAULT)
        } else {
            ByteArray(16).apply { SecureRandom().nextBytes(this) }
        }
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return "${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(hash, Base64.NO_WRAP)}"
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false
        val newHash = hashPassword(password, parts[0])
        return MessageDigest.isEqual(storedHash.toByteArray(), newHash.toByteArray())
    }
}

data class UserSession(
    val email: String,
    val name: String,
    val role: UserRole,
    val assignedTeamId: String? = null,
    val token: String,
    val expiresAtEpochMs: Long,
    val isGuest: Boolean = false
)

class KeystoreSessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val keyAlias = "RapidResQSessionKey"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    init {
        try {
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // Ignore keystore init failures in demo mode
        }
    }

    private fun getSecretKey(): SecretKey = keyStore.getKey(keyAlias, null) as SecretKey

    fun saveSession(session: UserSession) {
        try {
            val json = JSONObject().apply {
                put("email", session.email)
                put("name", session.name)
                put("role", session.role.name)
                if (session.assignedTeamId != null) put("assignedTeamId", session.assignedTeamId)
                put("token", session.token)
                put("expiresAtEpochMs", session.expiresAtEpochMs)
                put("isGuest", session.isGuest)
            }.toString()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

            prefs.edit()
                .putString("session_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString("session_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            clearSession()
        }
    }

    fun getSession(): UserSession? {
        try {
            val ivStr = prefs.getString("session_iv", null) ?: return null
            val dataStr = prefs.getString("session_data", null) ?: return null

            val iv = Base64.decode(ivStr, Base64.DEFAULT)
            val encrypted = Base64.decode(dataStr, Base64.DEFAULT)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            val decrypted = cipher.doFinal(encrypted)
            val json = JSONObject(String(decrypted, Charsets.UTF_8))

            val expiresAt = json.getLong("expiresAtEpochMs")
            if (System.currentTimeMillis() > expiresAt) {
                clearSession()
                return null
            }

            return UserSession(
                email = json.getString("email"),
                name = json.getString("name"),
                role = UserRole.valueOf(json.getString("role")),
                assignedTeamId = if (json.has("assignedTeamId") && !json.isNull("assignedTeamId")) json.getString("assignedTeamId") else null,
                token = json.getString("token"),
                expiresAtEpochMs = expiresAt,
                isGuest = json.getBoolean("isGuest")
            )
        } catch (e: Exception) {
            clearSession()
            return null
        }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
