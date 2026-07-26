/**
 * Staff routes (multi-tenant)
 * GET    /api/staff
 * POST   /api/staff
 * PUT    /api/staff/:id
 * DELETE /api/staff/:id
 */

const express   = require('express');
const bcrypt    = require('bcryptjs');
const { z }     = require('zod');
const { query } = require('../lib/db');

const router = express.Router();

const staffSchema = z.object({
  fullName: z.string().min(1, 'Full name is required').max(150),
  email:    z.string().email('Invalid email'),
  phone:    z.string().max(20).optional().or(z.literal('')).transform(v => v || null),
  role:     z.enum(['OWNER', 'RECEPTIONIST', 'TRAINER']).default('RECEPTIONIST'),
  notes:    z.string().max(500).optional(),
  password: z.string().min(6).optional(),
});

const staffUpdateSchema = staffSchema.partial();

// ── GET /api/staff ─────────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const { rows } = await query(
      `SELECT id,"tenantId","fullName",email,phone,role,notes,"createdAt" FROM staff WHERE "tenantId" = $1 ORDER BY "createdAt" DESC`,
      [tenantId],
    );
    return res.json(rows);
  } catch (err) {
    console.error('[staff/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch staff.' });
  }
});

// ── POST /api/staff ────────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = staffSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const { fullName, email, phone, role, notes, password } = result.data;
  const passwordHash = password ? await bcrypt.hash(password, 12) : null;

  try {
    const { rows } = await query(
      `INSERT INTO staff ("tenantId","fullName",email,phone,role,notes,"passwordHash")
       VALUES ($1,$2,$3,$4,$5,$6,$7)
       RETURNING id,"tenantId","fullName",email,phone,role,notes,"createdAt"`,
      [tenantId, fullName, email, phone ?? null, role, notes ?? null, passwordHash],
    );
    return res.status(201).json(rows[0]);
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'A staff member with that email already exists.' });
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
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  const sets = [];
  const vals = [];
  let p = 1;

  if (data.fullName !== undefined) { sets.push(`"fullName" = $${p++}`); vals.push(data.fullName); }
  if (data.email    !== undefined) { sets.push(`email = $${p++}`);      vals.push(data.email); }
  if (data.phone    !== undefined) { sets.push(`phone = $${p++}`);      vals.push(data.phone); }
  if (data.role     !== undefined) { sets.push(`role = $${p++}`);       vals.push(data.role); }
  if (data.notes    !== undefined) { sets.push(`notes = $${p++}`);      vals.push(data.notes); }
  if (data.password !== undefined) {
    const hash = await bcrypt.hash(data.password, 12);
    sets.push(`"passwordHash" = $${p++}`); vals.push(hash);
  }

  if (sets.length === 0) return res.status(400).json({ error: 'No fields to update.' });
  vals.push(id, tenantId);

  try {
    const { rowCount } = await query(
      `UPDATE staff SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`, vals,
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Staff member not found.' });
    const { rows } = await query(
      `SELECT id,"tenantId","fullName",email,phone,role,notes,"createdAt" FROM staff WHERE id = $1 AND "tenantId" = $2`,
      [id, tenantId],
    );
    return res.json(rows[0]);
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'Email already in use.' });
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
    const { rowCount } = await query(
      `DELETE FROM staff WHERE id = $1 AND "tenantId" = $2`, [id, tenantId],
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Staff member not found.' });
    return res.status(204).send();
  } catch (err) {
    console.error('[staff/DELETE]', err);
    return res.status(500).json({ error: 'Failed to remove staff member.' });
  }
});

module.exports = router;
