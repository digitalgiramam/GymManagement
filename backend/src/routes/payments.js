/**
 * Payments routes (multi-tenant)
 * GET  /api/payments/methods      — list payment methods
 * POST /api/payments/methods      — create payment method
 * PUT  /api/payments/methods/:id  — update payment method
 * GET  /api/payments              — list payments
 * POST /api/payments              — record payment
 */

const express   = require('express');
const { z }     = require('zod');
const { query } = require('../lib/db');

const router = express.Router();

const paymentSchema = z.object({
  memberId:    z.number().int().positive('memberId is required'),
  amount:      z.number().positive('Amount must be positive'),
  methodId:    z.number().int().positive('methodId is required'),
  notes:       z.string().max(500).optional(),
  paymentDate: z.string().datetime().optional(),
});

const paymentMethodSchema = z.object({
  name:     z.string().min(1).max(50),
  isActive: z.boolean().optional(),
});

// ── GET /api/payments/methods ──────────────────────────────────────────────
router.get('/methods', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const { rows } = await query(
      `SELECT * FROM payment_methods WHERE "tenantId" = $1 ORDER BY id ASC`, [tenantId],
    );
    return res.json(rows);
  } catch (err) {
    console.error('[payments/methods/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch payment methods.' });
  }
});

// ── POST /api/payments/methods ─────────────────────────────────────────────
router.post('/methods', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = paymentMethodSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const { name, isActive = true } = result.data;
  try {
    const { rows } = await query(
      `INSERT INTO payment_methods ("tenantId", name, "isActive") VALUES ($1,$2,$3) RETURNING *`,
      [tenantId, name, isActive],
    );
    return res.status(201).json(rows[0]);
  } catch (err) {
    console.error('[payments/methods/POST]', err);
    return res.status(500).json({ error: 'Failed to create payment method.' });
  }
});

// ── PUT /api/payments/methods/:id ──────────────────────────────────────────
router.put('/methods/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid method ID.' });

  const result = paymentMethodSchema.partial().safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  const sets = [];
  const vals = [];
  let p = 1;
  if (data.name     !== undefined) { sets.push(`name = $${p++}`);          vals.push(data.name); }
  if (data.isActive !== undefined) { sets.push(`"isActive" = $${p++}`);    vals.push(data.isActive); }
  if (sets.length === 0) return res.status(400).json({ error: 'No fields to update.' });
  vals.push(id, tenantId);

  try {
    const { rowCount } = await query(
      `UPDATE payment_methods SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`, vals,
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Payment method not found.' });
    const { rows } = await query(`SELECT * FROM payment_methods WHERE id = $1 AND "tenantId" = $2`, [id, tenantId]);
    return res.json(rows[0]);
  } catch (err) {
    console.error('[payments/methods/PUT]', err);
    return res.status(500).json({ error: 'Failed to update payment method.' });
  }
});

// ── GET /api/payments ──────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId               = req.user.tenantId;
  const { memberId, startDate, endDate } = req.query;

  const conditions = [`p."tenantId" = $1`];
  const vals       = [tenantId];
  let   idx        = 2;

  if (memberId) {
    const pid = parseInt(memberId, 10);
    if (isNaN(pid)) return res.status(400).json({ error: 'Invalid memberId.' });
    conditions.push(`p."memberId" = $${idx++}`);
    vals.push(pid);
  }
  if (startDate) {
    const d = new Date(startDate);
    if (isNaN(d.getTime())) return res.status(400).json({ error: 'Invalid startDate.' });
    conditions.push(`p."paymentDate" >= $${idx++}`);
    vals.push(d);
  }
  if (endDate) {
    const d = new Date(endDate);
    if (isNaN(d.getTime())) return res.status(400).json({ error: 'Invalid endDate.' });
    d.setHours(23, 59, 59, 999);
    conditions.push(`p."paymentDate" <= $${idx++}`);
    vals.push(d);
  }

  try {
    const { rows } = await query(
      `SELECT p.*,
              m.id AS "memberId_", m."fullName" AS "memberName_", m.phone AS "memberPhone_",
              pm.id AS "methodId_", pm.name AS "methodName_"
       FROM payments p
       JOIN members m ON m.id = p."memberId"
       JOIN payment_methods pm ON pm.id = p."methodId"
       WHERE ${conditions.join(' AND ')}
       ORDER BY p."paymentDate" DESC`,
      vals,
    );
    const payments = rows.map(r => {
      const { memberId_: mmid, memberName_: mmname, memberPhone_: mmphone,
              methodId_: mtid, methodName_: mtname, ...rest } = r;
      return { ...rest, member: { id: mmid, fullName: mmname, phone: mmphone }, method: { id: mtid, name: mtname } };
    });
    return res.json(payments);
  } catch (err) {
    console.error('[payments/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch payments.' });
  }
});

// ── POST /api/payments ─────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = paymentSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const { memberId, amount, methodId, notes, paymentDate } = result.data;

  try {
    const { rows: mRows } = await query(
      `SELECT id FROM members WHERE id = $1 AND "tenantId" = $2`, [memberId, tenantId],
    );
    if (!mRows[0]) return res.status(404).json({ error: 'Member not found.' });

    const { rows: pmRows } = await query(
      `SELECT id FROM payment_methods WHERE id = $1 AND "tenantId" = $2`, [methodId, tenantId],
    );
    if (!pmRows[0]) return res.status(400).json({ error: 'Invalid payment method.' });

    const { rows } = await query(
      `INSERT INTO payments ("tenantId","memberId",amount,"methodId",notes,"paymentDate")
       VALUES ($1,$2,$3,$4,$5,$6) RETURNING *`,
      [tenantId, memberId, amount, methodId, notes ?? null, paymentDate ? new Date(paymentDate) : new Date()],
    );
    const payment = rows[0];

    const { rows: fullRows } = await query(
      `SELECT p.*,
              m.id AS "memberId_", m."fullName" AS "memberName_", m.phone AS "memberPhone_",
              pm.id AS "methodId_", pm.name AS "methodName_"
       FROM payments p
       JOIN members m ON m.id = p."memberId"
       JOIN payment_methods pm ON pm.id = p."methodId"
       WHERE p.id = $1`,
      [payment.id],
    );
    const r = fullRows[0];
    const { memberId_: mmid, memberName_: mmname, memberPhone_: mmphone,
            methodId_: mtid, methodName_: mtname, ...rest } = r;
    return res.status(201).json({ ...rest, member: { id: mmid, fullName: mmname, phone: mmphone }, method: { id: mtid, name: mtname } });
  } catch (err) {
    console.error('[payments/POST]', err);
    return res.status(500).json({ error: 'Failed to record payment.' });
  }
});

module.exports = router;
