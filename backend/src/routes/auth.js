/**
 * Auth routes — email / password
 *
 * POST /api/auth/register      { email, password, name }          — gym owner registration
 * POST /api/auth/login         { email, password }                — gym owner login
 * POST /api/auth/staff-login   { email, password }                — staff member login
 * POST /api/auth/member-login  { email, password }                — gym member login
 */

const express = require('express');
const jwt     = require('jsonwebtoken');
const bcrypt  = require('bcryptjs');
const { z }   = require('zod');
const prisma = require('../lib/prisma');

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

function makeToken(payload) {
  return jwt.sign(payload, process.env.JWT_SECRET, { expiresIn: '30d' });
}

function ownerToken(user) {
  return makeToken({ userId: user.id, tenantId: user.tenantId, email: user.email, role: 'OWNER' });
}

function staffToken(staff) {
  return makeToken({ userId: staff.id, tenantId: staff.tenantId, email: staff.email, role: 'STAFF', staffId: staff.id });
}

function memberToken(member) {
  return makeToken({ userId: member.id, tenantId: member.tenantId, email: member.email ?? `member_${member.id}`, role: 'MEMBER', memberId: member.id });
}

function ownerResponse(user) {
  return { id: user.id, email: user.email, name: user.name, tenantId: user.tenantId, role: 'OWNER' };
}

function staffResponse(staff) {
  return { id: staff.id, email: staff.email, name: staff.fullName, tenantId: staff.tenantId, role: staff.role };
}

function memberResponse(member) {
  return { id: member.id, email: member.email, name: member.fullName, tenantId: member.tenantId, role: 'MEMBER' };
}

// ── POST /api/auth/register ────────────────────────────────────────────────
router.post('/register', async (req, res) => {
  const parsed = registerSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: parsed.error.errors[0].message });
  }

  const { email, password, name } = parsed.data;
  const passwordHash = await bcrypt.hash(password, 12);

  let user;
  try {
    user = await prisma.user.create({ data: { email, name, passwordHash } });
  } catch (err) {
    if (err.code === 'P2002') {
      return res.status(409).json({ error: 'An account with this email already exists.' });
    }
    console.error('[auth/register]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }

  return res.status(201).json({ token: ownerToken(user), user: ownerResponse(user) });
});

// ── POST /api/auth/login ───────────────────────────────────────────────────
router.post('/login', async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: parsed.error.errors[0].message });
  }

  const { email, password } = parsed.data;
  const user = await prisma.user.findUnique({ where: { email } });
  if (!user) return res.status(401).json({ error: 'Invalid email or password.' });

  const valid = await bcrypt.compare(password, user.passwordHash);
  if (!valid) return res.status(401).json({ error: 'Invalid email or password.' });

  return res.json({ token: ownerToken(user), user: ownerResponse(user) });
});

// ── POST /api/auth/staff-login ─────────────────────────────────────────────
router.post('/staff-login', async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: parsed.error.errors[0].message });
  }

  const { email, password } = parsed.data;

  // Staff email is unique per tenant but not globally — find by email across all tenants
  const staff = await prisma.staff.findFirst({
    where: { email },
  });

  if (!staff || !staff.passwordHash) {
    return res.status(401).json({ error: 'Invalid email or password, or login not enabled for this account.' });
  }

  const valid = await bcrypt.compare(password, staff.passwordHash);
  if (!valid) return res.status(401).json({ error: 'Invalid email or password.' });

  return res.json({ token: staffToken(staff), user: staffResponse(staff) });
});

// ── POST /api/auth/member-login ────────────────────────────────────────────
router.post('/member-login', async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: parsed.error.errors[0].message });
  }

  const { email, password } = parsed.data;

  const member = await prisma.member.findFirst({
    where: { email },
  });

  if (!member || !member.passwordHash) {
    return res.status(401).json({ error: 'Invalid email or password, or login not enabled for this account.' });
  }

  const valid = await bcrypt.compare(password, member.passwordHash);
  if (!valid) return res.status(401).json({ error: 'Invalid email or password.' });

  return res.json({ token: memberToken(member), user: memberResponse(member) });
});

module.exports = router;
