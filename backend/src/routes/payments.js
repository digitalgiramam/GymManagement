/**
 * Payments routes (multi-tenant)
 *
 * Wallet auto-adjustment
 * ──────────────────────
 * Every subscription payment computes:
 *   walletAdjustment = amountPaid − planFee
 *
 *   > 0  → member overpaid  → credit added to wallet
 *   < 0  → member underpaid → debt recorded in wallet
 *   = 0  → exact payment    → wallet unchanged
 *
 * The running walletBalance on the member is updated atomically.
 * On the next payment the staff sees the wallet balance and can collect
 * (planFee − walletBalance) accordingly.
 *
 * Edit  → re-computes the delta and adjusts walletBalance by the difference.
 * Delete→ reverses the stored walletAdjustment.
 *
 * Endpoints
 * ─────────
 * GET    /api/payments/methods          — list payment methods
 * POST   /api/payments/methods          — create payment method
 * PUT    /api/payments/methods/:id      — update payment method
 * GET    /api/payments/wallet/:memberId — member wallet balance
 * GET    /api/payments                  — list payments (filterable)
 * POST   /api/payments                  — record subscription payment
 * PUT    /api/payments/:id              — edit payment amount / method / notes
 * DELETE /api/payments/:id              — delete payment (reverses wallet)
 */

const express                = require('express');
const { z }                  = require('zod');
const { query, transaction } = require('../lib/db');

const router = express.Router();

// ── Validation schemas ─────────────────────────────────────────────────────

const paymentSchema = z.object({
  memberId:    z.number().int().positive('memberId is required'),
  amount:      z.number().nonnegative('Amount must be ≥ 0'),
  methodId:    z.number().int().positive('methodId is required'),
  notes:       z.string().max(500).optional(),
  paymentDate: z.string().datetime().optional(),
});

const updatePaymentSchema = z.object({
  amount:  z.number().nonnegative('Amount must be ≥ 0').optional(),
  methodId: z.number().int().positive().optional(),
  notes:   z.string().max(500).nullable().optional(),
});

const paymentMethodSchema = z.object({
  name:     z.string().min(1).max(50),
  isActive: z.boolean().optional(),
});

// ── Fetch full payment object by id ───────────────────────────────────────

async function fetchFullPayment(id) {
  const { rows } = await query(
    `SELECT p.*,
            m.id AS "memberId_", m."fullName" AS "memberName_", m.phone AS "memberPhone_",
            pm.id AS "methodId_", pm.name AS "methodName_"
     FROM payments p
     JOIN members m  ON m.id  = p."memberId"
     JOIN payment_methods pm ON pm.id = p."methodId"
     WHERE p.id = $1`,
    [id],
  );
  if (!rows[0]) return null;
  const { memberId_: mmid, memberName_: mmname, memberPhone_: mmphone,
          methodId_: mtid, methodName_: mtname, ...rest } = rows[0];
  return { ...rest, member: { id: mmid, fullName: mmname, phone: mmphone }, method: { id: mtid, name: mtname } };
}

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
  const sets = []; const vals = []; let p = 1;
  if (data.name     !== undefined) { sets.push(`name = $${p++}`);       vals.push(data.name); }
  if (data.isActive !== undefined) { sets.push(`"isActive" = $${p++}`); vals.push(data.isActive); }
  if (sets.length === 0) return res.status(400).json({ error: 'No fields to update.' });
  vals.push(id, tenantId);

  try {
    const { rowCount } = await query(
      `UPDATE payment_methods SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`, vals,
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Payment method not found.' });
    const { rows } = await query(
      `SELECT * FROM payment_methods WHERE id = $1 AND "tenantId" = $2`, [id, tenantId],
    );
    return res.json(rows[0]);
  } catch (err) {
    console.error('[payments/methods/PUT]', err);
    return res.status(500).json({ error: 'Failed to update payment method.' });
  }
});

// ── GET /api/payments/wallet/:memberId ────────────────────────────────────
router.get('/wallet/:memberId', async (req, res) => {
  const tenantId = req.user.tenantId;
  const memberId = parseInt(req.params.memberId, 10);
  if (isNaN(memberId)) return res.status(400).json({ error: 'Invalid memberId.' });

  try {
    const { rows } = await query(
      `SELECT id, "fullName", "walletBalance" FROM members WHERE id = $1 AND "tenantId" = $2`,
      [memberId, tenantId],
    );
    if (!rows[0]) return res.status(404).json({ error: 'Member not found.' });
    return res.json({
      memberId:      rows[0].id,
      fullName:      rows[0].fullName,
      walletBalance: parseFloat(rows[0].walletBalance) || 0,
    });
  } catch (err) {
    console.error('[payments/wallet/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch wallet balance.' });
  }
});

// ── GET /api/payments ──────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const { memberId, startDate, endDate } = req.query;

  const conditions = [`p."tenantId" = $1`];
  const vals = [tenantId]; let idx = 2;

  if (memberId) {
    const pid = parseInt(memberId, 10);
    if (isNaN(pid)) return res.status(400).json({ error: 'Invalid memberId.' });
    conditions.push(`p."memberId" = $${idx++}`); vals.push(pid);
  }
  if (startDate) {
    const d = new Date(startDate);
    if (isNaN(d.getTime())) return res.status(400).json({ error: 'Invalid startDate.' });
    conditions.push(`p."paymentDate" >= $${idx++}`); vals.push(d);
  }
  if (endDate) {
    const d = new Date(endDate);
    if (isNaN(d.getTime())) return res.status(400).json({ error: 'Invalid endDate.' });
    d.setHours(23, 59, 59, 999);
    conditions.push(`p."paymentDate" <= $${idx++}`); vals.push(d);
  }

  try {
    const { rows } = await query(
      `SELECT p.*,
              m.id AS "memberId_", m."fullName" AS "memberName_", m.phone AS "memberPhone_",
              pm.id AS "methodId_", pm.name AS "methodName_"
       FROM payments p
       JOIN members m  ON m.id  = p."memberId"
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
  const paidAt = paymentDate ? new Date(paymentDate) : new Date();

  try {
    const paymentId = await transaction(async (client) => {

      // ── Validate member + get plan fee ─────────────────────────────────
      const { rows: mRows } = await client.query(
        `SELECT m.*, p.fee AS "planFee"
         FROM members m JOIN plans p ON p.id = m."planId"
         WHERE m.id = $1 AND m."tenantId" = $2`,
        [memberId, tenantId],
      );
      if (!mRows[0]) throw Object.assign(new Error('Member not found.'), { status: 404 });

      // ── Validate payment method ─────────────────────────────────────────
      const { rows: pmRows } = await client.query(
        `SELECT id FROM payment_methods WHERE id = $1 AND "tenantId" = $2`,
        [methodId, tenantId],
      );
      if (!pmRows[0]) throw Object.assign(new Error('Invalid payment method.'), { status: 400 });

      const planFee         = parseFloat(mRows[0].planFee) || 0;
      const walletAdjustment = parseFloat((amount - planFee).toFixed(2));

      // ── Update wallet balance ───────────────────────────────────────────
      await client.query(
        `UPDATE members SET "walletBalance" = "walletBalance" + $1 WHERE id = $2`,
        [walletAdjustment, memberId],
      );

      // ── Insert payment ──────────────────────────────────────────────────
      const { rows } = await client.query(
        `INSERT INTO payments
           ("tenantId","memberId",amount,"methodId",notes,"paymentDate","walletAdjustment")
         VALUES ($1,$2,$3,$4,$5,$6,$7)
         RETURNING id`,
        [tenantId, memberId, amount, methodId, notes ?? null, paidAt, walletAdjustment],
      );
      return rows[0].id;
    });

    return res.status(201).json(await fetchFullPayment(paymentId));
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    console.error('[payments/POST]', err);
    return res.status(500).json({ error: 'Failed to record payment.' });
  }
});

// ── PUT /api/payments/:id ─────────────────────────────────────────────────
router.put('/:id', async (req, res) => {
  const tenantId  = req.user.tenantId;
  const paymentId = parseInt(req.params.id, 10);
  if (isNaN(paymentId)) return res.status(400).json({ error: 'Invalid payment ID.' });

  const result = updatePaymentSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  if (Object.keys(data).length === 0) return res.status(400).json({ error: 'No fields to update.' });

  try {
    await transaction(async (client) => {

      // ── Fetch existing payment (must belong to this tenant) ────────────
      const { rows: pRows2 } = await client.query(
        `SELECT p.*, pl.fee AS "planFee"
         FROM payments p
         JOIN members m ON m.id = p."memberId"
         JOIN plans pl ON pl.id = m."planId"
         WHERE p.id = $1 AND p."tenantId" = $2`,
        [paymentId, tenantId],
      );
      if (!pRows2[0]) throw Object.assign(new Error('Payment not found.'), { status: 404 });

      const existing  = pRows2[0];
      const planFee   = parseFloat(existing.planFee) || 0;
      const oldAmount = parseFloat(existing.amount);
      const oldAdj    = parseFloat(existing.walletAdjustment);

      // Determine new amount
      const newAmount = data.amount !== undefined ? data.amount : oldAmount;
      const newAdj    = parseFloat((newAmount - planFee).toFixed(2));
      const deltaAdj  = parseFloat((newAdj - oldAdj).toFixed(2));

      // ── Adjust wallet if amount changed ────────────────────────────────
      if (deltaAdj !== 0) {
        await client.query(
          `UPDATE members SET "walletBalance" = "walletBalance" + $1 WHERE id = $2`,
          [deltaAdj, existing.memberId],
        );
      }

      // ── Validate new method if provided ────────────────────────────────
      if (data.methodId !== undefined) {
        const { rows: pmRows } = await client.query(
          `SELECT id FROM payment_methods WHERE id = $1 AND "tenantId" = $2`,
          [data.methodId, tenantId],
        );
        if (!pmRows[0]) throw Object.assign(new Error('Invalid payment method.'), { status: 400 });
      }

      // ── Build UPDATE sets ───────────────────────────────────────────────
      const sets = [`"walletAdjustment" = $1`];
      const vals = [newAdj];
      let p = 2;
      if (data.amount   !== undefined) { sets.push(`amount = $${p++}`);     vals.push(data.amount); }
      if (data.methodId !== undefined) { sets.push(`"methodId" = $${p++}`); vals.push(data.methodId); }
      if (data.notes    !== undefined) { sets.push(`notes = $${p++}`);      vals.push(data.notes); }
      vals.push(paymentId, tenantId);

      await client.query(
        `UPDATE payments SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`,
        vals,
      );
    });

    return res.json(await fetchFullPayment(paymentId));
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    console.error('[payments/PUT]', err);
    return res.status(500).json({ error: 'Failed to update payment.' });
  }
});

// ── DELETE /api/payments/:id ──────────────────────────────────────────────
router.delete('/:id', async (req, res) => {
  const tenantId  = req.user.tenantId;
  const paymentId = parseInt(req.params.id, 10);
  if (isNaN(paymentId)) return res.status(400).json({ error: 'Invalid payment ID.' });

  try {
    await transaction(async (client) => {

      // ── Fetch payment ──────────────────────────────────────────────────
      const { rows } = await client.query(
        `SELECT * FROM payments WHERE id = $1 AND "tenantId" = $2`,
        [paymentId, tenantId],
      );
      if (!rows[0]) throw Object.assign(new Error('Payment not found.'), { status: 404 });

      const payment = rows[0];
      const adj     = parseFloat(payment.walletAdjustment) || 0;

      // ── Reverse wallet adjustment ──────────────────────────────────────
      if (adj !== 0) {
        await client.query(
          `UPDATE members SET "walletBalance" = "walletBalance" - $1 WHERE id = $2`,
          [adj, payment.memberId],
        );
      }

      // ── Delete payment ─────────────────────────────────────────────────
      await client.query(
        `DELETE FROM payments WHERE id = $1 AND "tenantId" = $2`,
        [paymentId, tenantId],
      );
    });

    return res.status(204).send();
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    console.error('[payments/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete payment.' });
  }
});

module.exports = router;
