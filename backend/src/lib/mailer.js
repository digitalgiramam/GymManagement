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
 * Shared send path for every email this app sends. Falls back to console
 * logging if SMTP isn't configured (or fails to load), and never throws —
 * callers get back a { delivered, reason? } result instead.
 */
async function deliverEmail({ to, subject, text, html, fallbackLogLine }) {
  const transporter = getTransporter();
  if (!transporter) {
    if (!_warnedMissingConfig) {
      console.warn('[mailer] SMTP not configured — logging instead of emailing.');
      _warnedMissingConfig = true;
    }
    console.log(`[mailer] ${fallbackLogLine}`);
    return { delivered: false, reason: 'SMTP not configured' };
  }

  try {
    await transporter.sendMail({
      from: process.env.SMTP_FROM || process.env.SMTP_USER,
      to,
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

/** Super Admin "forgot password" — emails a reset link. */
async function sendPasswordResetEmail(toEmail, resetLink) {
  return deliverEmail({
    to: toEmail,
    subject: 'Reset your Super Admin password',
    text: `We received a request to reset your Super Admin password.\n\n` +
      `Reset it here (valid for 1 hour):\n${resetLink}\n\n` +
      `If you didn't request this, you can safely ignore this email.`,
    html: `
      <p>We received a request to reset your Super Admin password.</p>
      <p><a href="${resetLink}">Click here to reset your password</a> (valid for 1 hour).</p>
      <p style="color:#8b92a3;font-size:13px;">If you didn't request this, you can safely ignore this email.</p>
    `,
    fallbackLogLine: `Password reset link for ${toEmail}: ${resetLink}`,
  });
}

/** Gym Owner "forgot password" (mobile app) — emails a 6-digit code, entered directly in the app. */
async function sendPasswordResetCodeEmail(toEmail, code) {
  return deliverEmail({
    to: toEmail,
    subject: 'Your Gym Manager password reset code',
    text: `Your password reset code is: ${code}\n\n` +
      `Enter this in the app to set a new password. It's valid for 15 minutes.\n\n` +
      `If you didn't request this, you can safely ignore this email.`,
    html: `
      <p>Your password reset code is:</p>
      <p style="font-size:28px; font-weight:700; letter-spacing:4px;">${code}</p>
      <p>Enter this in the app to set a new password. It's valid for 15 minutes.</p>
      <p style="color:#8b92a3;font-size:13px;">If you didn't request this, you can safely ignore this email.</p>
    `,
    fallbackLogLine: `Password reset code for ${toEmail}: ${code}`,
  });
}

module.exports = { sendPasswordResetEmail, sendPasswordResetCodeEmail, isSmtpConfigured };
