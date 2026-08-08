/**
 * Auth routes — v2 (member login accepts email OR phone)
 * POST /api/auth/register     — gym owner registration
 * POST /api/auth/login        — gym owner login
 * POST /api/auth/staff-login  — staff login
 * POST /api/auth/member-login — member login
 */

const express = require('express');
const jwt     = require('jsonwebtoken');
const bcrypt  = require('bcryptjs');
const crypto  = require('crypto');
const { z }   = require('zod');
const { query } = require('../lib/db');
const { sendPasswordResetCodeEmail } = require('../lib/mailer');

const router = express.Router();

const RESET_CODE_TTL_MS = 15 * 60 * 1000; // 15 minutes — shorter-lived than a link since it's short/guessable

function hashResetCode(code) {
  return crypto.createHash('sha256').update(code).digest('hex');
}

const registerSchema = z.object({
  email:    z.string().email('Valid email required'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  name:     z.string().min(1, 'Name is required'),
});

const loginSchema = z.object({
  email:    z.string().email('Valid email required'),
  password: z.string().min(1, 'Password is required'),
});

// Members may log in with email OR phone — no format restriction on the identifier
const memberLoginSchema = z.object({
  email:    z.string().min(1, 'Email or phone is required'),
  password: z.string().min(1, 'Password is required'),
});

function makeToken(payload) {
  return jwt.sign(payload, process.env.JWT_SECRET, { expiresIn: '30d' });
}

/** Look up the tenant's configured currency symbol (falls back to "$"). */
async function getCurrencySymbol(tenantId) {
  if (!tenantId) return '$';
  try {
    const { rows } = await query(`SELECT "currencySymbol" FROM tenants WHERE id = $1`, [tenantId]);
    return rows[0]?.currencySymbol || '$';
  } catch (err) {
    console.error('[auth/getCurrencySymbol]', err);
    return '$';
  }
}

/** Returns true if the tenant exists and has been suspended by a Super Admin. */
async function isTenantSuspended(tenantId) {
  if (!tenantId) return false;
  try {
    const { rows } = await query(`SELECT "isSuspended" FROM tenants WHERE id = $1`, [tenantId]);
    return rows[0]?.isSuspended === true;
  } catch (err) {
    console.error('[auth/isTenantSuspended]', err);
    return false; // fail open on lookup error — don't lock everyone out over a transient DB blip
  }
}

const SUSPENDED_MESSAGE = 'This gym account has been suspended. Please contact support.';

// ── POST /api/auth/register ────────────────────────────────────────────────
router.post('/register', async (req, res) => {
  const parsed = registerSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { email, password, name } = parsed.data;
  const passwordHash = await bcrypt.hash(password, 12);

  try {
    const { rows } = await query(
      `INSERT INTO users (email, name, "passwordHash") VALUES ($1, $2, $3) RETURNING *`,
      [email, name, passwordHash],
    );
    const user = rows[0];
    const token = makeToken({ userId: user.id, tenantId: user.tenantId, email: user.email, role: 'OWNER' });
    return res.status(201).json({
      token,
      user: { id: user.id, email: user.email, name: user.name, tenantId: user.tenantId, role: 'OWNER' },
    });
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'An account with this email already exists.' });
    console.error('[auth/register]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

// ── POST /api/auth/login ───────────────────────────────────────────────────
router.post('/login', async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { email, password } = parsed.data;

  try {
    const { rows } = await query(`SELECT * FROM users WHERE email = $1`, [email]);
    const user = rows[0];
    if (!user) return res.status(401).json({ error: 'Invalid email or password.' });

    const valid = await bcrypt.compare(password, user.passwordHash);
    if (!valid) return res.status(401).json({ error: 'Invalid email or password.' });

    if (await isTenantSuspended(user.tenantId)) {
      return res.status(403).json({ error: SUSPENDED_MESSAGE, code: 'TENANT_SUSPENDED' });
    }

    const token = makeToken({ userId: user.id, tenantId: user.tenantId, email: user.email, role: 'OWNER' });
    const currencySymbol = await getCurrencySymbol(user.tenantId);
    return res.json({
      token,
      user: { id: user.id, email: user.email, name: user.name, tenantId: user.tenantId, role: 'OWNER', currencySymbol },
    });
  } catch (err) {
    console.error('[auth/login]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

// ── POST /api/auth/forgot-password ──────────────────────────────────────────
// Gym Owner only (mobile app). Emails a 6-digit code — no deep-linking set up
// in the app yet, so a code the user types in is simpler than a link.
const forgotPasswordSchema = z.object({
  email: z.string().email('Valid email required'),
});

router.post('/forgot-password', async (req, res) => {
  const parsed = forgotPasswordSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { email } = parsed.data;
  // Always the same generic response — don't leak whether an account exists.
  const genericResponse = { message: 'If an account exists for that email, a reset code has been sent.' };

  try {
    const { rows } = await query(`SELECT id, email FROM users WHERE email = $1 LIMIT 1`, [email]);
    const user = rows[0];
    if (!user) return res.json(genericResponse);

    const code     = crypto.randomInt(100000, 1000000).toString(); // 6 digits, zero-padded by range
    const codeHash = hashResetCode(code);
    const expiry   = new Date(Date.now() + RESET_CODE_TTL_MS);

    await query(
      `UPDATE users SET "resetCodeHash" = $1, "resetCodeExpiry" = $2 WHERE id = $3`,
      [codeHash, expiry, user.id],
    );

    await sendPasswordResetCodeEmail(user.email, code);
    return res.json(genericResponse);
  } catch (err) {
    console.error('[auth/forgot-password]', err);
    return res.json(genericResponse); // still generic, even on internal error
  }
});

// ── POST /api/auth/reset-password ───────────────────────────────────────────
const resetPasswordSchema = z.object({
  email:    z.string().email('Valid email required'),
  code:     z.string().min(6, 'Reset code is required').max(6),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

router.post('/reset-password', async (req, res) => {
  const parsed = resetPasswordSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { email, code, password } = parsed.data;
  const codeHash = hashResetCode(code);

  try {
    const { rows } = await query(
      `SELECT id FROM users WHERE email = $1 AND "resetCodeHash" = $2 AND "resetCodeExpiry" > NOW()`,
      [email, codeHash],
    );
    const user = rows[0];
    if (!user) return res.status(400).json({ error: 'This code is invalid or has expired. Please request a new one.' });

    const passwordHash = await bcrypt.hash(password, 12);
    await query(
      `UPDATE users SET "passwordHash" = $1, "resetCodeHash" = NULL, "resetCodeExpiry" = NULL WHERE id = $2`,
      [passwordHash, user.id],
    );

    return res.json({ success: true });
  } catch (err) {
    console.error('[auth/reset-password]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

// ── POST /api/auth/staff-login ─────────────────────────────────────────────
router.post('/staff-login', async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { email, password } = parsed.data;

  try {
    const { rows } = await query(`SELECT * FROM staff WHERE email = $1 LIMIT 1`, [email]);
    const staff = rows[0];
    if (!staff || !staff.passwordHash) {
      return res.status(401).json({ error: 'Invalid email or password, or login not enabled for this account.' });
    }

    const valid = await bcrypt.compare(password, staff.passwordHash);
    if (!valid) return res.status(401).json({ error: 'Invalid email or password.' });

    if (await isTenantSuspended(staff.tenantId)) {
      return res.status(403).json({ error: SUSPENDED_MESSAGE, code: 'TENANT_SUSPENDED' });
    }

    const token = makeToken({ userId: staff.id, tenantId: staff.tenantId, email: staff.email, role: 'STAFF', staffId: staff.id });
    const currencySymbol = await getCurrencySymbol(staff.tenantId);
    return res.json({
      token,
      user: { id: staff.id, email: staff.email, name: staff.fullName, tenantId: staff.tenantId, role: staff.role, currencySymbol },
    });
  } catch (err) {
    console.error('[auth/staff-login]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

// ── POST /api/auth/member-login ────────────────────────────────────────────
router.post('/member-login', async (req, res) => {
  const parsed = memberLoginSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { email, password } = parsed.data;

  try {
    // Accept email OR phone as the login identifier
    const { rows } = await query(
      `SELECT * FROM members WHERE (email = $1 OR phone = $1) LIMIT 1`,
      [email],
    );
    const member = rows[0];
    if (!member || !member.passwordHash) {
      return res.status(401).json({ error: 'Invalid email or password, or login not enabled for this account.' });
    }

    const valid = await bcrypt.compare(password, member.passwordHash);
    if (!valid) return res.status(401).json({ error: 'Invalid email or password.' });

    if (await isTenantSuspended(member.tenantId)) {
      return res.status(403).json({ error: SUSPENDED_MESSAGE, code: 'TENANT_SUSPENDED' });
    }

    const token = makeToken({
      userId: member.id, tenantId: member.tenantId,
      email: member.email ?? `member_${member.id}`, role: 'MEMBER', memberId: member.id,
    });
    const currencySymbol = await getCurrencySymbol(member.tenantId);
    return res.json({
      token,
      user: { id: member.id, email: member.email, name: member.fullName, tenantId: member.tenantId, role: 'MEMBER', currencySymbol },
    });
  } catch (err) {
    console.error('[auth/member-login]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

module.exports = router;
