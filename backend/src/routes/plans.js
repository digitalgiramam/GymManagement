/**
 * Plans routes (multi-tenant)
 * GET    /api/plans
 * POST   /api/plans
 * PUT    /api/plans/:id
 * DELETE /api/plans/:id
 */

const express   = require('express');
const { z }     = require('zod');
const { query } = require('../lib/db');

const router = express.Router();

const planSchema = z.object({
  name:         z.string().min(1, 'Name is required').max(100),
  durationDays: z.number().int().positive('Duration must be a positive integer'),
  fee:          z.number().positive('Fee must be positive'),
  isActive:     z.boolean().optional(),
});

const planUpdateSchema = planSchema.partial();

// ── GET /api/plans ─────────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const { rows } = await query(
      `SELECT p.*,
              (SELECT COUNT(*) FROM members WHERE "planId" = p.id AND "tenantId" = $1)::int AS "_count_members"
       FROM plans p
       WHERE p."tenantId" = $1
       ORDER BY p."durationDays" ASC`,
      [tenantId],
    );
    const plans = rows.map(r => {
      const { _count_members, ...plan } = r;
      return { ...plan, _count: { members: _count_members } };
    });
    return res.json(plans);
  } catch (err) {
    console.error('[plans/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch plans.' });
  }
});

// ── POST /api/plans ────────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = planSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const { name, durationDays, fee, isActive = true } = result.data;
  try {
    const { rows } = await query(
      `INSERT INTO plans ("tenantId", name, "durationDays", fee, "isActive") VALUES ($1,$2,$3,$4,$5) RETURNING *`,
      [tenantId, name, durationDays, fee, isActive],
    );
    return res.status(201).json(rows[0]);
  } catch (err) {
    console.error('[plans/POST]', err);
    return res.status(500).json({ error: 'Failed to create plan.' });
  }
});

// ── PUT /api/plans/:id ─────────────────────────────────────────────────────
router.put('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid plan ID.' });

  const result = planUpdateSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  const sets = [];
  const vals = [];
  let p = 1;

  if (data.name         !== undefined) { sets.push(`name = $${p++}`);              vals.push(data.name); }
  if (data.durationDays !== undefined) { sets.push(`"durationDays" = $${p++}`);    vals.push(data.durationDays); }
  if (data.fee          !== undefined) { sets.push(`fee = $${p++}`);               vals.push(data.fee); }
  if (data.isActive     !== undefined) { sets.push(`"isActive" = $${p++}`);        vals.push(data.isActive); }

  if (sets.length === 0) return res.status(400).json({ error: 'No fields to update.' });
  vals.push(id, tenantId);

  try {
    const { rowCount } = await query(
      `UPDATE plans SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`,
      vals,
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Plan not found.' });
    const { rows } = await query(`SELECT * FROM plans WHERE id = $1 AND "tenantId" = $2`, [id, tenantId]);
    return res.json(rows[0]);
  } catch (err) {
    console.error('[plans/PUT]', err);
    return res.status(500).json({ error: 'Failed to update plan.' });
  }
});

// ── DELETE /api/plans/:id ──────────────────────────────────────────────────
router.delete('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid plan ID.' });

  try {
    const { rowCount } = await query(
      `DELETE FROM plans WHERE id = $1 AND "tenantId" = $2`, [id, tenantId],
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Plan not found.' });
    return res.status(204).send();
  } catch (err) {
    if (err.code === '23503') return res.status(409).json({ error: 'Cannot delete a plan that has active members.' });
    console.error('[plans/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete plan.' });
  }
});

module.exports = router;
