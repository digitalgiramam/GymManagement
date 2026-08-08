/**
 * SMTP mailer — currently used only for the Super Admin "forgot password" flow.
 *
 * Configure via env vars (see .env.example):
 *   SMTP_HOST, SMTP_PORT, SMTP_SECURE, SMTP_USER, SMTP_PASS, SMTP_FROM
 *
 * If SMTP isn't configured (e.g. local dev), sendPasswordResetEmail() logs the
 * reset link to the console instead of throwing — so the reset flow still
 * works end-to-end without a mail server, and admins deploying this the first
 * time see a clear message instead of a silent failure.
 */

// nodemailer is required lazily (inside getTransporter, wrapped in try/catch)
// rather than at module load time. This is deliberate: this module is
// require()'d from api/index.js's top level, so if nodemailer were ever
// missing or failed to load in a given deployment, a top-level require()
// would crash the *entire* serverless function — breaking every route in the
// app, not just the password-reset feature. Loading it lazily means a
// problem here only disables password-reset emails (falling back to
// console-logging the reset link) instead of taking down the whole API.

const { logError } = require('./errorLog');

let _transporter = null;
let _warnedMissingConfig = false;
let _warnedLoadFailure = false;

function isSmtpConfigured() {
  return Boolean(process.env.SMTP_HOST && process.env.SMTP_USER && process.env.SMTP_PASS);
}

function getTransporter() {
  if (_transporter || !isSmtpConfigured()) return _transporter;

  try {
    const nodemailer = require('nodemailer');
    _transporter = nodemailer.createTransport({
      host: process.env.SMTP_HOST,
      port: parseInt(process.env.SMTP_PORT, 10) || 587,
      secure: process.env.SMTP_SECURE === 'true', // true for port 465, false for 587/25
      auth: {
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS,
      },
    });
  } catch (err) {
    if (!_warnedLoadFailure) {
      logError('mailer', new Error(`Failed to load nodemailer — reset links will be logged instead of emailed: ${err.message}`));
      _warnedLoadFailure = true;
    }
  }
  return _transporter;
}

/**
 * Sends a password-reset email to a Super Admin. Falls back to console
 * logging if SMTP isn't configured, so this never throws and blocks the
 * (deliberately generic) API response to the caller.
 */
async function sendPasswordResetEmail(toEmail, resetLink) {
  const subject = 'Reset your Super Admin password';
  const text = `We received a request to reset your Super Admin password.\n\n` +
    `Reset it here (valid for 1 hour):\n${resetLink}\n\n` +
    `If you didn't request this, you can safely ignore this email.`;
  const html = `
    <p>We received a request to reset your Super Admin password.</p>
    <p><a href="${resetLink}">Click here to reset your password</a> (valid for 1 hour).</p>
    <p style="color:#8b92a3;font-size:13px;">If you didn't request this, you can safely ignore this email.</p>
  `;

  const transporter = getTransporter();
  if (!transporter) {
    if (!_warnedMissingConfig) {
      console.warn('[mailer] SMTP not configured — logging reset link instead of emailing it.');
      _warnedMissingConfig = true;
    }
    console.log(`[mailer] Password reset link for ${toEmail}: ${resetLink}`);
    return { delivered: false, reason: 'SMTP not configured' };
  }

  try {
    await transporter.sendMail({
      from: process.env.SMTP_FROM || process.env.SMTP_USER,
      to: toEmail,
      subject,
      text,
      html,
    });
    return { delivered: true };
  } catch (err) {
    logError('mailer', err);
    return { delivered: false, reason: err.message };
  }
}

module.exports = { sendPasswordResetEmail, isSmtpConfigured };
