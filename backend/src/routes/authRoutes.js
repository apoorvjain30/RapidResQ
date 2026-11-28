import express from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import rateLimit from 'express-rate-limit';
import { supabase } from '../config/supabase.js';
import { inMemoryUsers } from '../config/seed.js';
import { logAudit } from '../services/audit.js';
import { sendOtpEmail } from '../services/mailer.js';
import { requireAuth } from '../middleware/auth.js';

const router = express.Router();

const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 30, // 30 requests per 15 minutes
  message: { error: 'Too Many Requests', message: 'Too many authentication attempts. Please try again later.' }
});

router.use(authLimiter);

// In-memory OTP storage for fast verification & local fallback
// Key: `${email.toLowerCase()}:${purpose}` -> { hashedCode, expiresAt, remainingAttempts, passwordResetToken? }
const otpStore = new Map();

function generateJwt(user) {
  const jwtSecret = process.env.JWT_SECRET || 'your_jwt_secret_key_here';
  return jwt.sign(
    {
      id: user.id,
      email: user.email,
      name: user.name,
      role: user.role,
      assigned_team_id: user.assigned_team_id
    },
    jwtSecret,
    { expiresIn: '8h' }
  );
}

function validateStrongPassword(password) {
  if (!password || password.length < 8) return false;
  const hasUpper = /[A-Z]/.test(password);
  const hasLower = /[a-z]/.test(password);
  const hasDigit = /[0-9]/.test(password);
  return hasUpper && hasLower && hasDigit;
}

async function findUserByEmail(email) {
  const normalizedEmail = email.toLowerCase().trim();

  if (supabase) {
    try {
      const { data, error } = await supabase
        .from('users')
        .select('*')
        .eq('email', normalizedEmail)
        .maybeSingle();

      if (data) return data;
    } catch (err) {
      console.error('[AUTH DB FIND USER ERROR]', err.message);
    }
  }

  return inMemoryUsers.get(normalizedEmail) || null;
}

async function updateUserRecord(email, updates) {
  const normalizedEmail = email.toLowerCase().trim();
  const memUser = inMemoryUsers.get(normalizedEmail);

  if (memUser) {
    Object.assign(memUser, updates);
    inMemoryUsers.set(normalizedEmail, memUser);
  }

  if (supabase) {
    try {
      await supabase
        .from('users')
        .update(updates)
        .eq('email', normalizedEmail);
    } catch (err) {
      console.error('[AUTH DB UPDATE USER ERROR]', err.message);
    }
  }
}

// 1. POST /api/auth/login
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Validation Error', message: 'Email and password are required' });
    }

    const normalizedEmail = email.toLowerCase().trim();
    const user = await findUserByEmail(normalizedEmail);

    if (!user) {
      await logAudit('SYSTEM', 'LOGIN_FAILED', 'SYSTEM', `Unknown email: ${normalizedEmail}`);
      return res.status(401).json({ error: 'Invalid Credentials', message: 'Invalid email or password' });
    }

    if (!user.is_active) {
      await logAudit(user.role, 'LOGIN_BLOCKED', 'SYSTEM', `Inactive account: ${normalizedEmail}`);
      return res.status(403).json({ error: 'Account Suspended', message: 'Your account is suspended. Please contact administrator.' });
    }

    // Check brute-force lockout
    if (user.locked_until && new Date(user.locked_until) > new Date()) {
      const remainingSecs = Math.ceil((new Date(user.locked_until) - new Date()) / 1000);
      await logAudit(user.role, 'LOGIN_BLOCKED', 'SYSTEM', `Lockout active for ${normalizedEmail}`);
      return res.status(429).json({
        error: 'Account Locked',
        message: `Too many failed attempts. Account locked for another ${remainingSecs} seconds.`
      });
    }

    const isMatch = await bcrypt.compare(password, user.password_hash);

    if (!isMatch) {
      const failedAttempts = (user.failed_attempts || 0) + 1;
      let lockedUntil = null;

      if (failedAttempts >= 5) {
        lockedUntil = new Date(Date.now() + 60 * 1000).toISOString(); // 60s lockout
        await updateUserRecord(normalizedEmail, {
          failed_attempts: 0,
          locked_until: lockedUntil
        });
        await logAudit(user.role, 'LOGIN_LOCKED', 'SYSTEM', `Account ${normalizedEmail} locked due to 5 failed attempts`);
        return res.status(429).json({
          error: 'Account Locked',
          message: 'Too many failed attempts. Account locked for 60 seconds.'
        });
      }

      await updateUserRecord(normalizedEmail, { failed_attempts: failedAttempts });
      await logAudit(user.role, 'LOGIN_FAILED', 'SYSTEM', `Invalid password for ${normalizedEmail}`);
      return res.status(401).json({ error: 'Invalid Credentials', message: 'Invalid email or password' });
    }

    // Reset failed login attempts on successful password check
    await updateUserRecord(normalizedEmail, { failed_attempts: 0, locked_until: null });

    // REQUIRE OTP FOR ALL ROLES (Admin, Dispatcher, Responder, AND Citizen)
    const rawCode = String(crypto.randomInt(100000, 999999));
    const hashedCode = await bcrypt.hash(rawCode, 10);
    const expiresAt = Date.now() + 5 * 60 * 1000; // 5 mins

    otpStore.set(`${normalizedEmail}:LOGIN_MFA`, {
      hashedCode,
      expiresAt,
      remainingAttempts: 3
    });

    if (supabase) {
      try {
        await supabase.from('otp_challenges').insert([{
          id: `OTP-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`,
          email: normalizedEmail,
          purpose: 'LOGIN_MFA',
          code_hash: hashedCode,
          expires_at: new Date(expiresAt).toISOString(),
          remaining_attempts: 3,
          is_used: false,
          created_at: new Date().toISOString()
        }]);
      } catch (otpErr) {
        console.error('[OTP DB INSERT ERROR]', otpErr.message);
      }
    }

    const mailResult = await sendOtpEmail(normalizedEmail, rawCode, 'LOGIN_MFA');
    await logAudit(user.role, 'OTP_ISSUED', 'SYSTEM', `Login MFA OTP issued for ${normalizedEmail}`);

    return res.json({
      requiresOtp: true,
      email: normalizedEmail,
      role: user.role,
      purpose: 'LOGIN_MFA',
      delivery: mailResult
    });
  } catch (err) {
    console.error('[AUTH LOGIN ERROR]', err);
    res.status(500).json({ error: 'Server Error', message: err.message });
  }
});

// 2. POST /api/auth/verify-otp
router.post('/verify-otp', async (req, res) => {
  try {
    const { email, code, purpose } = req.body;

    if (!email || !code || !purpose) {
      return res.status(400).json({ error: 'Validation Error', message: 'Email, code, and purpose are required' });
    }

    const normalizedEmail = email.toLowerCase().trim();
    const key = `${normalizedEmail}:${purpose}`;
    const otpChallenge = otpStore.get(key);

    if (!otpChallenge) {
      return res.status(400).json({ error: 'Invalid Request', message: 'No active OTP challenge found or code expired' });
    }

    if (Date.now() > otpChallenge.expiresAt) {
      otpStore.delete(key);
      return res.status(400).json({ error: 'OTP Expired', message: 'OTP has expired. Please request a new code.' });
    }

    const isMatch = await bcrypt.compare(String(code), otpChallenge.hashedCode);

    if (!isMatch) {
      otpChallenge.remainingAttempts -= 1;

      if (otpChallenge.remainingAttempts <= 0) {
        otpStore.delete(key);
        const user = await findUserByEmail(normalizedEmail);
        await logAudit(user?.role || 'SYSTEM', 'OTP_LOCKED', 'SYSTEM', `Max OTP attempts reached for ${normalizedEmail}`);
        return res.status(429).json({ error: 'OTP Locked', message: 'Too many failed OTP attempts. Please restart login.' });
      }

      otpStore.set(key, otpChallenge);
      const user = await findUserByEmail(normalizedEmail);
      await logAudit(user?.role || 'SYSTEM', 'OTP_FAILED', 'SYSTEM', `Invalid OTP for ${normalizedEmail}`);
      return res.status(400).json({
        error: 'Invalid OTP',
        message: `Invalid verification code. ${otpChallenge.remainingAttempts} attempts remaining.`
      });
    }

    // Success -> Clear OTP challenge
    otpStore.delete(key);
    const user = await findUserByEmail(normalizedEmail);

    if (!user) {
      return res.status(404).json({ error: 'User Not Found', message: 'Associated user account not found' });
    }

    const token = generateJwt(user);
    const action = (purpose === 'CITIZEN_REGISTER') ? 'CITIZEN_REGISTERED' : 'LOGIN_MFA_SUCCESS';
    await logAudit(user.role, action, 'SYSTEM', `Successful OTP verification for ${normalizedEmail}`);

    return res.json({
      token,
      user: {
        id: user.id,
        email: user.email,
        name: user.name,
        role: user.role,
        assigned_team_id: user.assigned_team_id,
        isGuest: false
      }
    });
  } catch (err) {
    console.error('[AUTH VERIFY OTP ERROR]', err);
    res.status(500).json({ error: 'Server Error', message: err.message });
  }
});

// 3. POST /api/auth/resend-otp
router.post('/resend-otp', async (req, res) => {
  try {
    const { email, purpose } = req.body;

    if (!email || !purpose) {
      return res.status(400).json({ error: 'Validation Error', message: 'Email and purpose are required' });
    }

    const normalizedEmail = email.toLowerCase().trim();
    const user = await findUserByEmail(normalizedEmail);

    if (!user) {
      return res.status(404).json({ error: 'User Not Found', message: 'Account not found' });
    }

    const rawCode = String(crypto.randomInt(100000, 999999));
    const hashedCode = await bcrypt.hash(rawCode, 10);
    const expiresAt = Date.now() + 5 * 60 * 1000;

    otpStore.set(`${normalizedEmail}:${purpose}`, {
      hashedCode,
      expiresAt,
      remainingAttempts: 3
    });

    const mailResult = await sendOtpEmail(normalizedEmail, rawCode, purpose);
    await logAudit(user.role, 'OTP_RESENT', 'SYSTEM', `OTP resent to ${normalizedEmail} for ${purpose}`);

    return res.json({
      message: 'OTP resent successfully',
      delivery: mailResult
    });
  } catch (err) {
    console.error('[AUTH RESEND OTP ERROR]', err);
    res.status(500).json({ error: 'Server Error', message: err.message });
  }
});

// 4. POST /api/auth/register-citizen
router.post('/register-citizen', async (req, res) => {
  try {
    const { name, email, phone, password } = req.body;

    if (!name || !email || !password) {
      return res.status(400).json({ error: 'Validation Error', message: 'Name, email, and password are required' });
    }

    if (!validateStrongPassword(password)) {
      return res.status(400).json({
        error: 'Weak Password',
        message: 'Password must be at least 8 characters long and contain uppercase, lowercase, and numeric characters.'
      });
    }

    const normalizedEmail = email.toLowerCase().trim();
    const existing = await findUserByEmail(normalizedEmail);

    if (existing) {
      return res.status(409).json({ error: 'Conflict', message: 'An account with this email already exists' });
    }

    const passwordHash = await bcrypt.hash(password, 10);
    const newUser = {
      id: `USR-citizen-${Date.now()}`,
      email: normalizedEmail,
      password_hash: passwordHash,
      name,
      phone: phone || null,
      role: 'CITIZEN',
      department: 'Public',
      assigned_team_id: null,
      mfa_enabled: false,
      is_active: true,
      failed_attempts: 0,
      locked_until: null,
      created_at: new Date().toISOString()
    };

    inMemoryUsers.set(normalizedEmail, newUser);

    if (supabase) {
      try {
        await supabase.from('users').insert([newUser]);
      } catch (err) {
        console.error('[REGISTER DB INSERT ERROR]', err.message);
      }
    }

    const rawCode = String(crypto.randomInt(100000, 999999));
    const hashedCode = await bcrypt.hash(rawCode, 10);
    const expiresAt = Date.now() + 5 * 60 * 1000;

    otpStore.set(`${normalizedEmail}:CITIZEN_REGISTER`, {
      hashedCode,
      expiresAt,
      remainingAttempts: 3
    });

    const mailResult = await sendOtpEmail(normalizedEmail, rawCode, 'CITIZEN_REGISTER');
    await logAudit('CITIZEN', 'OTP_ISSUED', 'SYSTEM', `Registration OTP issued for ${normalizedEmail}`);

    return res.status(201).json({
      message: 'Citizen account created. Verification OTP issued.',
      email: normalizedEmail,
      purpose: 'CITIZEN_REGISTER',
      delivery: mailResult
    });
  } catch (err) {
    console.error('[AUTH REGISTER ERROR]', err);
    res.status(500).json({ error: 'Server Error', message: err.message });
  }
});

// 5. POST /api/auth/forgot-password
router.post('/forgot-password', async (req, res) => {
  try {
    const { email } = req.body;

    if (!email) {
      return res.status(400).json({ error: 'Validation Error', message: 'Email is required' });
    }

    const normalizedEmail = email.toLowerCase().trim();
    const user = await findUserByEmail(normalizedEmail);

    const rawCode = String(crypto.randomInt(100000, 999999));
    const hashedCode = await bcrypt.hash(rawCode, 10);
    const expiresAt = Date.now() + 5 * 60 * 1000;

    otpStore.set(`${normalizedEmail}:PASSWORD_RESET`, {
      hashedCode,
      expiresAt,
      remainingAttempts: 3
    });

    const mailResult = await sendOtpEmail(normalizedEmail, rawCode, 'PASSWORD_RESET');
    await logAudit(user?.role || 'CITIZEN', 'OTP_ISSUED', 'SYSTEM', `Password reset OTP requested for ${normalizedEmail}`);

    return res.json({
      message: 'Password reset code sent',
      email: normalizedEmail,
      purpose: 'PASSWORD_RESET',
      delivery: mailResult
    });
  } catch (err) {
    console.error('[AUTH FORGOT PASSWORD ERROR]', err);
    res.status(500).json({ error: 'Server Error', message: err.message });
  }
});

// 6. POST /api/auth/verify-reset-otp
router.post('/verify-reset-otp', async (req, res) => {
  try {
    const { email, code } = req.body;

    if (!email || !code) {
      return res.status(400).json({ error: 'Validation Error', message: 'Email and code are required' });
    }

    const normalizedEmail = email.toLowerCase().trim();
    const key = `${normalizedEmail}:PASSWORD_RESET`;
    const otpChallenge = otpStore.get(key);

    if (!otpChallenge) {
      return res.status(400).json({ error: 'Invalid Request', message: 'No reset request found or code expired' });
    }

    if (Date.now() > otpChallenge.expiresAt) {
      otpStore.delete(key);
      return res.status(400).json({ error: 'OTP Expired', message: 'OTP has expired' });
    }

    const isMatch = await bcrypt.compare(String(code), otpChallenge.hashedCode);

    if (!isMatch) {
      otpChallenge.remainingAttempts -= 1;
      if (otpChallenge.remainingAttempts <= 0) {
        otpStore.delete(key);
        return res.status(429).json({ error: 'OTP Locked', message: 'Too many failed attempts. Restart password reset.' });
      }
      otpStore.set(key, otpChallenge);
      return res.status(400).json({ error: 'Invalid OTP', message: `Invalid code. ${otpChallenge.remainingAttempts} attempts remaining.` });
    }

    const resetToken = crypto.randomBytes(24).toString('hex');
    otpChallenge.passwordResetToken = resetToken;
    otpStore.set(key, otpChallenge);

    return res.json({
      message: 'Reset OTP verified',
      resetToken
    });
  } catch (err) {
    console.error('[AUTH VERIFY RESET OTP ERROR]', err);
    res.status(500).json({ error: 'Server Error', message: err.message });
  }
});

// 7. POST /api/auth/reset-password
router.post('/reset-password', async (req, res) => {
  try {
    const { email, code, newPassword } = req.body;

    if (!email || !newPassword) {
      return res.status(400).json({ error: 'Validation Error', message: 'Email and newPassword are required' });
    }

    if (!validateStrongPassword(newPassword)) {
      return res.status(400).json({
        error: 'Weak Password',
        message: 'Password must be at least 8 characters long and contain uppercase, lowercase, and numeric characters.'
      });
    }

    const normalizedEmail = email.toLowerCase().trim();
    const key = `${normalizedEmail}:PASSWORD_RESET`;
    const otpChallenge = otpStore.get(key);

    if (code) {
      if (!otpChallenge || Date.now() > otpChallenge.expiresAt) {
        return res.status(400).json({ error: 'Invalid Request', message: 'Reset session expired' });
      }
      const isMatch = await bcrypt.compare(String(code), otpChallenge.hashedCode);
      if (!isMatch) {
        return res.status(400).json({ error: 'Invalid OTP', message: 'Invalid verification code' });
      }
    }

    const user = await findUserByEmail(normalizedEmail);
    if (!user) {
      return res.status(404).json({ error: 'User Not Found', message: 'Account not found' });
    }

    const passwordHash = await bcrypt.hash(newPassword, 10);
    await updateUserRecord(normalizedEmail, {
      password_hash: passwordHash,
      failed_attempts: 0,
      locked_until: null
    });

    otpStore.delete(key);
    await logAudit(user.role, 'PASSWORD_RESET', 'SYSTEM', `Password reset successful for ${normalizedEmail}`);

    return res.json({ message: 'Password reset successfully. You can now log in with your new password.' });
  } catch (err) {
    console.error('[AUTH RESET PASSWORD ERROR]', err);
    res.status(500).json({ error: 'Server Error', message: err.message });
  }
});

// 8. POST /api/auth/logout
router.post('/logout', requireAuth(), async (req, res) => {
  try {
    await logAudit(req.user.role, 'LOGOUT', 'SYSTEM', `User ${req.user.email} logged out`);
    res.json({ message: 'Logged out successfully' });
  } catch (err) {
    res.status(500).json({ error: 'Server Error', message: err.message });
  }
});

// 9. GET /api/auth/me
router.get('/me', requireAuth(), async (req, res) => {
  try {
    const user = await findUserByEmail(req.user.email);
    if (!user) {
      return res.status(404).json({ error: 'User Not Found', message: 'Profile not found' });
    }

    res.json({
      user: {
        id: user.id,
        email: user.email,
        name: user.name,
        role: user.role,
        department: user.department,
        assigned_team_id: user.assigned_team_id,
        mfa_enabled: user.mfa_enabled,
        is_active: user.is_active
      }
    });
  } catch (err) {
    res.status(500).json({ error: 'Server Error', message: err.message });
  }
});

export default router;
