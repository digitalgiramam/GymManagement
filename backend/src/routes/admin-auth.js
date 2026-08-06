/**
 * Super Admin auth — platform-level login, separate from tenant users.
 * POST /api/admin/auth/login
 * POST /api/admin/auth/forgot-password
 * POST /api/admin/auth/reset-password
 */

const express  = require('express');
const jwt      = require('jsonwebtoken');
const bcrypt   = require('bcryptjs');
const crypto   = require('crypto');
const { z }    = require('zod');
const { query } = require('../lib/db');
const { sendPasswordResetEmail } = require('../lib/mailer');

const router = express.Router();

const RESET_TOKEN_TTL_MS = 60 * 60 * 1000; // 1 hour

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

const loginSchema = z.object({
  email:    z.string().email('Valid email required'),
  password: z.string().min(1, 'Password is required'),
});

router.post('/login', async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { email, password } = parsed.data;

  try {
    const { rows } = await query(`SELECT * FROM super_admins WHERE email = $1 LIMIT 1`, [email]);
    const admin = rows[0];
    if (!admin) return res.status(401).json({ error: 'Invalid email or password.' });

    const valid = await bcrypt.compare(password, admin.passwordHash);
    if (!valid) return res.status(401).json({ error: 'Invalid email or password.' });

    const token = jwt.sign(
      { adminId: admin.id, email: admin.email, role: 'SUPER_ADMIN' },
      process.env.JWT_SECRET,
      { expiresIn: '12h' },   // shorter-lived than tenant tokens — platform-level access
    );

    return res.json({
      token,
      admin: { id: admin.id, email: admin.email, fullName: admin.fullName },
    });
  } catch (err) {
    console.error('[admin-auth/login]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

// ── POST /api/admin/auth/forgot-password ────────────────────────────────────
const forgotPasswordSchema = z.object({
  email: z.string().email('Valid email required'),
});

router.post('/forgot-password', async (req, res) => {
  const parsed = forgotPasswordSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { email } = parsed.data;
  // Always return the same generic response, whether or not the email
  // matches an account — this avoids leaking which emails have accounts.
  const genericResponse = { message: 'If an account exists for that email, a password reset link has been sent.' };

  try {
    const { rows } = await query(`SELECT id, email FROM super_admins WHERE email = $1 LIMIT 1`, [email]);
    const admin = rows[0];
    if (!admin) return res.json(genericResponse);

    const rawToken  = crypto.randomBytes(32).toString('hex');
    const tokenHash = hashToken(rawToken);
    const expiry    = new Date(Date.now() + RESET_TOKEN_TTL_MS);

    await query(
      `UPDATE super_admins SET "resetTokenHash" = $1, "resetTokenExpiry" = $2 WHERE id = $3`,
      [tokenHash, expiry, admin.id],
    );

    const portalUrl = (process.env.ADMIN_PORTAL_URL || `${req.protocol}://${req.get('host')}`).replace(/\/$/, '');
    const resetLink = `${portalUrl}/admin/reset-password.html?token=${rawToken}`;

    await sendPasswordResetEmail(admin.email, resetLink);
    return res.json(genericResponse);
  } catch (err) {
    console.error('[admin-auth/forgot-password]', err);
    // Still return the generic response so we don't leak internal errors either.
    return res.json(genericResponse);
  }
});

// ── POST /api/admin/auth/reset-password ─────────────────────────────────────
const resetPasswordSchema = z.object({
  token:    z.string().min(1, 'Reset token is required'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

router.post('/reset-password', async (req, res) => {
  const parsed = resetPasswordSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { token, password } = parsed.data;
  const tokenHash = hashToken(token);

  try {
    const { rows } = await query(
      `SELECT id FROM super_admins WHERE "resetTokenHash" = $1 AND "resetTokenExpiry" > NOW()`,
      [tokenHash],
    );
    const admin = rows[0];
    if (!admin) return res.status(400).json({ error: 'This reset link is invalid or has expired. Please request a new one.' });

    const passwordHash = await bcrypt.hash(password, 12);
    await query(
      `UPDATE super_admins SET "passwordHash" = $1, "resetTokenHash" = NULL, "resetTokenExpiry" = NULL WHERE id = $2`,
      [passwordHash, admin.id],
    );

    return res.json({ success: true });
  } catch (err) {
    console.error('[admin-auth/reset-password]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

module.exports = router;
