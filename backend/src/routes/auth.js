/**
 * Auth routes
 * POST /api/auth/register     — gym owner registration
 * POST /api/auth/login        — gym owner login
 * POST /api/auth/staff-login  — staff login
 * POST /api/auth/member-login — member login
 */

const express = require('express');
const jwt     = require('jsonwebtoken');
const bcrypt  = require('bcryptjs');
const { z }   = require('zod');
const { query } = require('../lib/db');

const router = express.Router();

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

    const token = makeToken({ userId: user.id, tenantId: user.tenantId, email: user.email, role: 'OWNER' });
    return res.json({
      token,
      user: { id: user.id, email: user.email, name: user.name, tenantId: user.tenantId, role: 'OWNER' },
    });
  } catch (err) {
    console.error('[auth/login]', err);
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

    const token = makeToken({ userId: staff.id, tenantId: staff.tenantId, email: staff.email, role: 'STAFF', staffId: staff.id });
    return res.json({
      token,
      user: { id: staff.id, email: staff.email, name: staff.fullName, tenantId: staff.tenantId, role: staff.role },
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

    const token = makeToken({
      userId: member.id, tenantId: member.tenantId,
      email: member.email ?? `member_${member.id}`, role: 'MEMBER', memberId: member.id,
    });
    return res.json({
      token,
      user: { id: member.id, email: member.email, name: member.fullName, tenantId: member.tenantId, role: 'MEMBER' },
    });
  } catch (err) {
    console.error('[auth/member-login]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

module.exports = router;
