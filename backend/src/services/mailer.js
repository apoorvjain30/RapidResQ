import nodemailer from 'nodemailer';

export async function sendOtpEmail(email, code, purpose) {
  const host = process.env.SMTP_HOST;
  const user = process.env.SMTP_USER;
  const pass = process.env.SMTP_PASS;
  const port = parseInt(process.env.SMTP_PORT || '587', 10);
  const from = process.env.SMTP_FROM || 'RapidResQ <no-reply@rapidresq.in>';

  if (host && user && pass) {
    try {
      const transporter = nodemailer.createTransport({
        host,
        port,
        secure: port === 465,
        auth: { user, pass }
      });

      const subject = `[RapidResQ] Your Verification Code for ${purpose}`;
      const text = `Your 6-digit verification code for RapidResQ (${purpose}) is: ${code}\nThis code expires in 5 minutes. Do not share it with anyone.`;
      const html = `
        <div style="font-family: Arial, sans-serif; padding: 20px; background-color: #f8fafc; color: #0f172a;">
          <h2 style="color: #1d4ed8;">RapidResQ Emergency System</h2>
          <p>Your verification code for <strong>${purpose}</strong> is:</p>
          <div style="font-size: 28px; font-weight: bold; letter-spacing: 4px; padding: 12px 24px; background: #dbeafe; color: #1d4ed8; display: inline-block; border-radius: 8px;">
            ${code}
          </div>
          <p style="margin-top: 16px; color: #475569; font-size: 14px;">This code will expire in 5 minutes.</p>
        </div>
      `;

      await transporter.sendMail({
        from,
        to: email,
        subject,
        text,
        html
      });

      console.log(`[MAILER] Real OTP email sent to ${email} for ${purpose}`);
      return { deliveredVia: 'email' };
    } catch (err) {
      console.error(`[MAILER ERROR] Failed to send email to ${email}:`, err.message);
    }
  }

  // Fallback / Simulated local dev mode
  console.log(`\n==================================================`);
  console.log(`[DEV MAILER] Simulated OTP Delivery (Pre-Backend)`);
  console.log(`Target: ${email}`);
  console.log(`Purpose: ${purpose}`);
  console.log(`OTP Code: ${code}`);
  console.log(`==================================================\n`);

  if (process.env.NODE_ENV !== 'production') {
    return {
      deliveredVia: 'simulated',
      devOtpHint: code
    };
  }

  return { deliveredVia: 'simulated' };
}
