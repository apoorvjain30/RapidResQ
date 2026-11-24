package com.rapidresq.app.data.repository

import com.rapidresq.app.data.model.UserRole
import com.rapidresq.app.data.security.PasswordHasher
import com.rapidresq.app.data.security.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID

data class OtpChallenge(
    val email: String,
    val purpose: String,
    val code: String,
    val expiresAtMs: Long,
    var remainingAttempts: Int = 3
)

data class AuthAccount(
    val email: String,
    var passwordHash: String,
    val name: String,
    val role: UserRole,
    val assignedTeamId: String? = null,
    var isLockedOutUntil: Long = 0,
    var failedLoginAttempts: Int = 0
)

class AuthRepository(private val incidentRepo: IncidentRepository) {
    private val accounts = mutableMapOf<String, AuthAccount>()
    
    private val _activeOtpChallenge = MutableStateFlow<OtpChallenge?>(null)
    val activeOtpChallenge: StateFlow<OtpChallenge?> = _activeOtpChallenge.asStateFlow()

    private val _simulatedOtpNotice = MutableStateFlow<String?>(null)
    val simulatedOtpNotice: StateFlow<String?> = _simulatedOtpNotice.asStateFlow()

    init {
        // Seed accounts
        accounts["admin@rapidresq.in"] = AuthAccount("admin@rapidresq.in", PasswordHasher.hashPassword("Admin@123"), "System Admin", UserRole.ADMIN)
        accounts["dispatch@rapidresq.in"] = AuthAccount("dispatch@rapidresq.in", PasswordHasher.hashPassword("Dispatch@123"), "Control Dispatcher", UserRole.DISPATCHER)
        accounts["alpha@rapidresq.in"] = AuthAccount("alpha@rapidresq.in", PasswordHasher.hashPassword("Unit@123"), "Unit Alpha Lead", UserRole.RESPONDER, "TEAM-ALPHA")
        accounts["bravo@rapidresq.in"] = AuthAccount("bravo@rapidresq.in", PasswordHasher.hashPassword("Unit@123"), "Unit Bravo Lead", UserRole.RESPONDER, "TEAM-BRAVO")
        accounts["citizen@rapidresq.in"] = AuthAccount("citizen@rapidresq.in", PasswordHasher.hashPassword("Citizen@123"), "Apoorv Citizen", UserRole.CITIZEN)
    }

    private fun generateOtp(): String {
        val sr = SecureRandom()
        return (1..6).joinToString("") { sr.nextInt(10).toString() }
    }

    fun loginWithPassword(email: String, pass: String): Result<Pair<UserSession?, UserRole>> {
        val account = accounts[email] ?: run {
            incidentRepo.logSecurityEvent("SYSTEM", "LOGIN_FAILED", "SYSTEM", "Unknown email: $email")
            return Result.failure(Exception("Invalid credentials"))
        }

        if (System.currentTimeMillis() < account.isLockedOutUntil) {
            return Result.failure(Exception("Account locked. Try again later."))
        }

        if (!PasswordHasher.verifyPassword(pass, account.passwordHash)) {
            account.failedLoginAttempts++
            if (account.failedLoginAttempts >= 5) {
                account.isLockedOutUntil = System.currentTimeMillis() + 60_000
                account.failedLoginAttempts = 0
                incidentRepo.logSecurityEvent(account.role.label, "LOGIN_LOCKED", "SYSTEM", "Account $email locked due to failed attempts")
                return Result.failure(Exception("Too many failed attempts. Account locked for 60 seconds."))
            }
            incidentRepo.logSecurityEvent(account.role.label, "LOGIN_FAILED", "SYSTEM", "Invalid password for $email")
            return Result.failure(Exception("Invalid credentials"))
        }

        account.failedLoginAttempts = 0

        if (account.role != UserRole.CITIZEN) {
            val code = generateOtp()
            val challenge = OtpChallenge(email, "LOGIN", code, System.currentTimeMillis() + 300_000)
            _activeOtpChallenge.value = challenge
            _simulatedOtpNotice.value = "Simulated OTP Delivery (Pre-Backend): Your code is $code"
            incidentRepo.logSecurityEvent(account.role.label, "OTP_ISSUED", "SYSTEM", "Login OTP issued to $email")
            return Result.success(Pair(null, account.role)) // Indicates OTP required
        } else {
            val session = UserSession(email, account.name, account.role, null, UUID.randomUUID().toString(), System.currentTimeMillis() + 8 * 3600 * 1000)
            incidentRepo.logSecurityEvent(account.role.label, "LOGIN_SUCCESS", "SYSTEM", "Citizen $email signed in")
            return Result.success(Pair(session, account.role))
        }
    }

    fun verifyActiveOtp(code: String): Result<UserSession> {
        val challenge = _activeOtpChallenge.value ?: return Result.failure(Exception("No active OTP request"))
        
        if (System.currentTimeMillis() > challenge.expiresAtMs) {
            _activeOtpChallenge.value = null
            _simulatedOtpNotice.value = null
            return Result.failure(Exception("OTP expired. Please resend."))
        }
        
        val account = accounts[challenge.email]!!
        
        if (challenge.code != code) {
            challenge.remainingAttempts--
            if (challenge.remainingAttempts <= 0) {
                _activeOtpChallenge.value = null
                _simulatedOtpNotice.value = null
                incidentRepo.logSecurityEvent(account.role.label, "OTP_LOCKED", "SYSTEM", "Max OTP attempts reached for ${challenge.email}")
                return Result.failure(Exception("Too many failed attempts. Please restart login."))
            }
            _activeOtpChallenge.value = challenge.copy(remainingAttempts = challenge.remainingAttempts)
            incidentRepo.logSecurityEvent(account.role.label, "OTP_FAILED", "SYSTEM", "Invalid OTP for ${challenge.email}")
            return Result.failure(Exception("Invalid code. ${challenge.remainingAttempts} attempts remaining."))
        }
        
        // Success
        _activeOtpChallenge.value = null
        _simulatedOtpNotice.value = null
        val session = UserSession(account.email, account.name, account.role, account.assignedTeamId, UUID.randomUUID().toString(), System.currentTimeMillis() + 8 * 3600 * 1000)
        
        if (challenge.purpose == "REGISTER") {
            account.isLockedOutUntil = 0
            incidentRepo.logSecurityEvent(account.role.label, "CITIZEN_REGISTERED", "SYSTEM", "Citizen account verified and active")
        } else {
            incidentRepo.logSecurityEvent(account.role.label, "LOGIN_MFA_SUCCESS", "SYSTEM", "MFA successful for ${account.email}")
        }
        return Result.success(session)
    }

    fun resendActiveOtp(): Result<Unit> {
        val challenge = _activeOtpChallenge.value ?: return Result.failure(Exception("No active OTP request"))
        val code = generateOtp()
        _activeOtpChallenge.value = challenge.copy(code = code, expiresAtMs = System.currentTimeMillis() + 300_000, remainingAttempts = 3)
        _simulatedOtpNotice.value = "Simulated OTP Delivery (Pre-Backend): Your code is $code"
        incidentRepo.logSecurityEvent("SYSTEM", "OTP_RESENT", "SYSTEM", "OTP resent to ${challenge.email}")
        return Result.success(Unit)
    }

    fun registerCitizen(name: String, email: String, phone: String, pass: String): Result<Unit> {
        if (accounts.containsKey(email)) return Result.failure(Exception("Email already in use"))
        
        val account = AuthAccount(email, PasswordHasher.hashPassword(pass), name, UserRole.CITIZEN)
        accounts[email] = account
        
        val code = generateOtp()
        _activeOtpChallenge.value = OtpChallenge(email, "REGISTER", code, System.currentTimeMillis() + 300_000)
        _simulatedOtpNotice.value = "Simulated OTP Delivery (Pre-Backend): Your code is $code"
        incidentRepo.logSecurityEvent(UserRole.CITIZEN.label, "OTP_ISSUED", "SYSTEM", "Registration OTP issued to $email")
        return Result.success(Unit)
    }

    fun requestPasswordResetOtp(email: String): Result<Unit> {
        val account = accounts[email]
        if (account != null) {
            val code = generateOtp()
            _activeOtpChallenge.value = OtpChallenge(email, "RESET_PASSWORD", code, System.currentTimeMillis() + 300_000)
            _simulatedOtpNotice.value = "Simulated OTP Delivery (Pre-Backend): Your code is $code"
        } else {
            _activeOtpChallenge.value = OtpChallenge(email, "RESET_PASSWORD", "000000", System.currentTimeMillis() + 300_000)
            _simulatedOtpNotice.value = "Simulated OTP Delivery (Pre-Backend): Your code is 000000"
        }
        return Result.success(Unit)
    }

    fun verifyResetOtp(email: String, code: String): Result<Unit> {
        val challenge = _activeOtpChallenge.value ?: return Result.failure(Exception("No active request"))
        if (challenge.purpose != "RESET_PASSWORD" || challenge.email != email) return Result.failure(Exception("Invalid request"))
        
        if (challenge.code != code || accounts[email] == null) {
            challenge.remainingAttempts--
            if (challenge.remainingAttempts <= 0) {
                _activeOtpChallenge.value = null
                _simulatedOtpNotice.value = null
                return Result.failure(Exception("Too many failed attempts. Restart reset process."))
            }
            _activeOtpChallenge.value = challenge.copy(remainingAttempts = challenge.remainingAttempts)
            return Result.failure(Exception("Invalid code."))
        }
        
        _activeOtpChallenge.value = null
        _simulatedOtpNotice.value = null
        return Result.success(Unit)
    }

    fun completePasswordReset(email: String, newPass: String): Result<Unit> {
        val account = accounts[email] ?: return Result.failure(Exception("Account not found"))
        account.passwordHash = PasswordHasher.hashPassword(newPass)
        account.failedLoginAttempts = 0
        account.isLockedOutUntil = 0
        incidentRepo.logSecurityEvent(account.role.label, "PASSWORD_RESET", "SYSTEM", "Password reset successful for $email")
        return Result.success(Unit)
    }
}
