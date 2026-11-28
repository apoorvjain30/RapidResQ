import nodemailer from 'nodemailer';

let cachedTransporter = null;

function getTransporter() {
  const host = process.env.SMTP_HOST;
  const port = Number(process.env.SMTP_PORT) || 587;
  const user = process.env.SMTP_USER;
  const pass = process.env.SMTP_PASS;

  if (!host || !user || !pass) return null;

  if (!cachedTransporter) {
    cachedTransporter = nodemailer.createTransport({
      host,
      port,
      secure: port === 465,
      auth: { user, pass },
      pool: true,
      maxConnections: 3,
      connectionTimeout: 10000,
      greetingTimeout: 10000,
      socketTimeout: 15000
    });
  }
  return cachedTransporter;
}

export async function sendOtpEmail(email, code, purpose) {
  const transporter = getTransporter();
  const from = process.env.SMTP_FROM || 'RapidResQ <no-reply@rapidresq.in>';
  const requireRealOtp = process.env.REQUIRE_REAL_OTP === 'true';

  let purposeLabel = 'your account';
  if (purpose === 'CITIZEN_REGISTER') purposeLabel = 'your new RapidResQ account';
  if (purpose === 'LOGIN_MFA') purposeLabel = 'your login attempt';
  if (purpose === 'PASSWORD_RESET') purposeLabel = 'your password reset request';

  if (transporter) {
    try {
      const htmlContent = `
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #23304a; border-radius: 12px; background-color: #111827; color: #f8fafc;">
          <h2 style="color: #3b82f6; margin-top: 0;">RapidResQ Security</h2>
          <p style="color: #cbd5e1; font-size: 16px;">Here is your verification code for ${purposeLabel}:</p>
          <div style="background-color: #0d1424; padding: 16px; border-radius: 8px; text-align: center; margin: 24px 0; border: 1px solid #23304a;">
            <span style="font-family: monospace; font-size: 32px; font-weight: bold; color: #3b82f6; letter-spacing: 6px;">${code}</span>
          </div>
          <p style="color: #94a3b8; font-size: 14px;">This code will expire in 5 minutes. If you didn't request this code, you can safely ignore this email.</p>
        </div>
      `;

      await transporter.sendMail({
        from,
        to: email,
        subject: `RapidResQ Verification Code: ${code}`,
        text: `Your RapidResQ verification code is: ${code}. It expires in 5 minutes.`,
        html: htmlContent
      });

      return { deliveredVia: 'smtp', success: true };
    } catch (err) {
      console.error('[SMTP SEND ERROR]', err.message);
      if (requireRealOtp) {
        throw new Error('Failed to deliver OTP email. Verify SMTP credentials.');
      }
    }
  } else if (requireRealOtp) {
    throw new Error('Failed to deliver OTP email. Verify SMTP credentials.');
  }

  // Fallback to simulated delivery if real OTP is not strictly required
  console.log(`\n==================================================`);
  console.log(`[SIMULATED EMAIL DELIVERY]`);
  console.log(`To: ${email}`);
  console.log(`Purpose: ${purposeLabel}`);
  console.log(`Code: ${code}`);
  console.log(`==================================================\n`);

  return { deliveredVia: 'simulated', success: true, devOtpHint: code };
}
