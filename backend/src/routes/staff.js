/**
 * Staff routes (multi-tenant)
 * GET    /api/staff       — list all staff
 * POST   /api/staff       — add a staff member
 * PUT    /api/staff/:id   — update a staff member
 * DELETE /api/staff/:id   — remove a staff member
 *
 * Roles: OWNER | RECEPTIONIST | TRAINER
 * All queries scoped to req.user.tenantId
 */

const express = require('express');
const bcrypt  = require('bcryptjs');
const { z }   = require('zod');
const prisma = require('../lib/prisma');

const router = express.Router();

const staffSchema = z.object({
  fullName: z.string().min(1, 'Full name is required').max(150),
  email:    z.string().email('Invalid email'),
  phone:    z.string().max(20).optional().or(z.literal('')).transform(v => v || null),
  role:     z.enum(['OWNER', 'RECEPTIONIST', 'TRAINER']).default('RECEPTIONIST'),
  notes:    z.string().max(500).optional(),
  password: z.string().min(6).optional(), // optional login password
});

const staffUpdateSchema = staffSchema.partial();

// ── GET /api/staff ─────────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const staff = await prisma.staff.findMany({
      where:   { tenantId },
      orderBy: { createdAt: 'desc' },
    });
    return res.json(staff);
  } catch (err) {
    console.error('[staff/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch staff.' });
  }
});

// ── POST /api/staff ────────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = staffSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  try {
    const { password, ...staffData } = result.data;
    const passwordHash = password ? await bcrypt.hash(password, 12) : undefined;

    const member = await prisma.staff.create({
      data: { ...staffData, tenantId, ...(passwordHash ? { passwordHash } : {}) },
    });
    return res.status(201).json(member);
  } catch (err) {
    if (err.code === 'P2002') {
      return res.status(409).json({ error: 'A staff member with that email already exists.' });
    }
    console.error('[staff/POST]', err);
    return res.status(500).json({ error: 'Failed to add staff member.' });
  }
});

// ── PUT /api/staff/:id ─────────────────────────────────────────────────────
router.put('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid staff ID.' });

  const result = staffUpdateSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  try {
    const { password, ...updateData } = result.data;
    const passwordHash = password ? await bcrypt.hash(password, 12) : undefined;

    const update = await prisma.staff.updateMany({
      where: { id, tenantId },
      data: { ...updateData, ...(passwordHash ? { passwordHash } : {}) },
    });
    if (update.count === 0) return res.status(404).json({ error: 'Staff member not found.' });
    const staff = await prisma.staff.findFirst({ where: { id, tenantId } });
    return res.json(staff);
  } catch (err) {
    if (err.code === 'P2002') {
      return res.status(409).json({ error: 'Email already in use.' });
    }
    console.error('[staff/PUT]', err);
    return res.status(500).json({ error: 'Failed to update staff member.' });
  }
});

// ── DELETE /api/staff/:id ──────────────────────────────────────────────────
router.delete('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid staff ID.' });

  try {
    const result = await prisma.staff.deleteMany({ where: { id, tenantId } });
    if (result.count === 0) return res.status(404).json({ error: 'Staff member not found.' });
    return res.status(204).send();
  } catch (err) {
    console.error('[staff/DELETE]', err);
    return res.status(500).json({ error: 'Failed to remove staff member.' });
  }
});

module.exports = router;
