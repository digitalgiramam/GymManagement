/**
 * Members routes (multi-tenant)
 * GET    /api/members       — list with optional ?search=
 * POST   /api/members       — create
 * GET    /api/members/:id   — detail + attendance
 * PUT    /api/members/:id   — update
 * DELETE /api/members/:id   — delete
 */

const express    = require('express');
const { z }      = require('zod');
const { query }  = require('../lib/db');

const router = express.Router();

const memberSchema = z.object({
  fullName: z.string().min(1, 'Full name is required').max(150),
  phone:    z.string().min(7, 'Phone is required').max(20),
  email:    z.string().email('Invalid email').optional().or(z.literal('')).transform(v => v || null),
  location: z.string().max(200).optional().or(z.literal('')).transform(v => v || null),
  planId:   z.number().int().positive('Plan ID is required'),
  status:   z.enum(['Active', 'Inactive']).default('Active'),
  joinDate: z.string().datetime().optional(),
});

const memberUpdateSchema = memberSchema.partial();

/**
 * Attach plan + compute daysUntilExpiry from the stored membershipExpiry column.
 * Expiry is now owned by the payments route (set on each payment) — not calculated here.
 */
function enrichMember(member, plan) {
  const daysUntilExpiry = member.membershipExpiry
    ? Math.ceil((new Date(member.membershipExpiry).getTime() - Date.now()) / 86400000)
    : null;
  return { ...member, plan, daysUntilExpiry };
}

// ── GET /api/members ───────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const search   = req.query.search?.toString().trim() || null;

  try {
    const { rows } = await query(
      `SELECT m.*,
              p.id AS "planId_", p.name AS "planName_", p."durationDays" AS "planDays_",
              p.fee AS "planFee_", p."isActive" AS "planActive_"
       FROM members m
       JOIN plans p ON p.id = m."planId"
       WHERE m."tenantId" = $1
         AND ($2::text IS NULL
              OR m."fullName" ILIKE '%' || $2 || '%'
              OR m.phone ILIKE '%' || $2 || '%')
       ORDER BY m."createdAt" DESC`,
      [tenantId, search],
    );

    const enriched = rows.map(row => {
      const plan = { id: row.planId_, name: row.planName_, durationDays: row.planDays_, fee: parseFloat(row.planFee_), isActive: row.planActive_ };
      const { planId_: _1, planName_: _2, planDays_: _3, planFee_: _4, planActive_: _5, ...member } = row;
      return enrichMember(member, plan);
    });

    return res.json(enriched);
  } catch (err) {
    console.error('[members/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch members.' });
  }
});

// ── GET /api/members/:id ───────────────────────────────────────────────────
router.get('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid member ID.' });

  try {
    const { rows: mRows } = await query(
      `SELECT m.*,
              p.id AS "planId_", p.name AS "planName_", p."durationDays" AS "planDays_",
              p.fee AS "planFee_", p."isActive" AS "planActive_"
       FROM members m
       JOIN plans p ON p.id = m."planId"
       WHERE m.id = $1 AND m."tenantId" = $2`,
      [id, tenantId],
    );
    if (!mRows[0]) return res.status(404).json({ error: 'Member not found.' });

    const { rows: attRows } = await query(
      `SELECT * FROM attendance WHERE "memberId" = $1 AND "tenantId" = $2 ORDER BY "checkedInAt" DESC LIMIT 50`,
      [id, tenantId],
    );

    const row  = mRows[0];
    const plan = { id: row.planId_, name: row.planName_, durationDays: row.planDays_, fee: parseFloat(row.planFee_), isActive: row.planActive_ };
    const { planId_: _1, planName_: _2, planDays_: _3, planFee_: _4, planActive_: _5, ...member } = row;

    const enriched = enrichMember(member, plan);
    return res.json({ ...enriched, attendance: attRows });
  } catch (err) {
    console.error('[members/GET/:id]', err);
    return res.status(500).json({ error: 'Failed to fetch member.' });
  }
});

// ── POST /api/members ──────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = memberSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const { fullName, phone, email, location, planId, status, joinDate } = result.data;

  try {
    const { rows } = await query(
      `INSERT INTO members ("tenantId","fullName",phone,email,location,"planId",status,"joinDate")
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
      [tenantId, fullName, phone, email ?? null, location ?? null, planId, status, joinDate ? new Date(joinDate) : new Date()],
    );
    const { rows: planRows } = await query(`SELECT * FROM plans WHERE id = $1`, [planId]);
    return res.status(201).json({ ...rows[0], plan: planRows[0] ?? null });
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'A member with that phone number already exists.' });
    if (err.code === '23503') return res.status(400).json({ error: 'Invalid plan ID.' });
    console.error('[members/POST]', err);
    return res.status(500).json({ error: 'Failed to create member.' });
  }
});

// ── PUT /api/members/:id ───────────────────────────────────────────────────
router.put('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid member ID.' });

  const result = memberUpdateSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  const sets = [];
  const vals = [];
  let p = 1;

  if (data.fullName  !== undefined) { sets.push(`"fullName" = $${p++}`);  vals.push(data.fullName); }
  if (data.phone     !== undefined) { sets.push(`phone = $${p++}`);       vals.push(data.phone); }
  if (data.email     !== undefined) { sets.push(`email = $${p++}`);       vals.push(data.email); }
  if (data.location  !== undefined) { sets.push(`location = $${p++}`);    vals.push(data.location); }
  if (data.planId    !== undefined) { sets.push(`"planId" = $${p++}`);    vals.push(data.planId); }
  if (data.status    !== undefined) { sets.push(`status = $${p++}`);      vals.push(data.status); }
  if (data.joinDate  !== undefined) { sets.push(`"joinDate" = $${p++}`);  vals.push(new Date(data.joinDate)); }

  if (sets.length === 0) return res.status(400).json({ error: 'No fields to update.' });
  vals.push(id, tenantId);

  try {
    const { rowCount } = await query(
      `UPDATE members SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`,
      vals,
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Member not found.' });

    const { rows } = await query(
      `SELECT m.*, p.id AS "planId_", p.name AS "planName_", p."durationDays" AS "planDays_",
              p.fee AS "planFee_", p."isActive" AS "planActive_"
       FROM members m JOIN plans p ON p.id = m."planId"
       WHERE m.id = $1 AND m."tenantId" = $2`,
      [id, tenantId],
    );
    const row  = rows[0];
    const plan = { id: row.planId_, name: row.planName_, durationDays: row.planDays_, fee: row.planFee_, isActive: row.planActive_ };
    const { planId_: _1, planName_: _2, planDays_: _3, planFee_: _4, planActive_: _5, ...member } = row;
    return res.json({ ...member, plan });
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'Phone number already in use.' });
    console.error('[members/PUT]', err);
    return res.status(500).json({ error: 'Failed to update member.' });
  }
});

// ── DELETE /api/members/:id ────────────────────────────────────────────────
router.delete('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid member ID.' });

  try {
    const { rowCount } = await query(
      `DELETE FROM members WHERE id = $1 AND "tenantId" = $2`, [id, tenantId],
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Member not found.' });
    return res.status(204).send();
  } catch (err) {
    console.error('[members/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete member.' });
  }
});

module.exports = router;
