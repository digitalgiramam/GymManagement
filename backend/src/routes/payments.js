/**
 * Payments routes (multi-tenant)
 *
 * GET    /methods         — list active payment methods
 * POST   /methods         — create payment method
 * GET    /expiring        — members whose membership expires within ?days (default 30)
 * GET    /                — list payments (filters: memberId, startDate, endDate)
 * POST   /                — record payment → extends membership expiry
 * PUT    /:id             — edit payment amount/method/notes → recalculates expiry
 * DELETE /:id             — delete payment → recalculates expiry
 */

const express                  = require('express');
const { z }                    = require('zod');
const { query, transaction }   = require('../lib/db');

const router = express.Router();

// ── SQL fragment reused for all payment fetches ────────────────────────────

const PAYMENT_SELECT = `
  SELECT p.*,
         m.id               AS m_id,
         m."fullName"       AS m_name,
         m.phone            AS m_phone,
         m."membershipExpiry" AS m_expiry,
         pm.id              AS pm_id,
         pm.name            AS pm_name,
         pl.name            AS pl_name
  FROM   payments p
  JOIN   members m          ON m.id  = p."memberId"
  JOIN   payment_methods pm ON pm.id = p."methodId"
  LEFT JOIN plans pl        ON pl.id = p."planId"
`;

function formatPayment(row) {
  const { m_id, m_name, m_phone, m_expiry, pm_id, pm_name, pl_name, ...p } = row;
  const now        = new Date();
  const paidAmount = parseFloat(p.amount)  || 0;
  const planFee    = parseFloat(p.planFee) || 0;

  // "Overdue" when subscription period has lapsed.
  // "Partial" when subscription is still active but amount < plan fee.
  // "Active"  when fully paid and subscription is still active.
  const extendedTo = p.membershipExtendedTo ? new Date(p.membershipExtendedTo) : null;
  const isExpired  = !extendedTo || extendedTo <= now;

  let membershipStatus;
  if (isExpired) {
    membershipStatus = 'Overdue';
  } else if (planFee > 0 && paidAmount < planFee) {
    membershipStatus = 'Partial';
  } else {
    membershipStatus = 'Active';
  }

  // Amount outstanding (0 for fully-paid or expired records)
  const overdueAmount = isExpired ? 0 : Math.max(0, planFee - paidAmount);

  return {
    ...p,
    amount: paidAmount,
    planFee,
    overdueAmount,
    planName: pl_name ?? null,
    membershipStatus,
    member: m_id  != null ? { id: m_id,  fullName: m_name,  phone: m_phone } : null,
    method: pm_id != null ? { id: pm_id, name: pm_name }                     : null,
  };
}

// ── Core helper: replay all payments → recalculate expiry chain ────────────

/**
 * Replays every payment for `memberId` in chronological order to recompute
 * `membershipExtendedTo` on each row and update `members.membershipExpiry`.
 *
 * Expiry logic per payment:
 *   base    = MAX(previous expiry, paymentDate)   — never back-date expiry
 *   newExp  = base + planDurationDays days
 *
 * Must be called inside an active transaction (`client` is a pg.PoolClient).
 */
async function recalculateMemberExpiry(client, memberId, tenantId) {
  const { rows } = await client.query(
    `SELECT id, "paymentDate", "planDurationDays"
     FROM   payments
     WHERE  "memberId" = $1 AND "tenantId" = $2
     ORDER  BY "paymentDate" ASC, id ASC`,
    [memberId, tenantId],
  );

  let currentExpiry = null;

  for (const p of rows) {
    const payDate = new Date(p.paymentDate);
    const base    = (currentExpiry && currentExpiry > payDate) ? currentExpiry : payDate;
    currentExpiry = new Date(base);
    currentExpiry.setDate(currentExpiry.getDate() + p.planDurationDays);

    await client.query(
      `UPDATE payments SET "membershipExtendedTo" = $1 WHERE id = $2`,
      [currentExpiry, p.id],
    );
  }

  // Write final expiry (null if member has no payments)
  await client.query(
    `UPDATE members SET "membershipExpiry" = $1 WHERE id = $2 AND "tenantId" = $3`,
    [currentExpiry, memberId, tenantId],
  );

  return currentExpiry;
}

// ── Payment Methods ────────────────────────────────────────────────────────

router.get('/methods', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const { rows } = await query(
      `SELECT * FROM payment_methods
       WHERE "tenantId" = $1 AND "isActive" = true
       ORDER BY name`,
      [tenantId],
    );
    return res.json(rows);
  } catch (err) {
    console.error('[payments/methods/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch payment methods.' });
  }
});

router.post('/methods', async (req, res) => {
  const tenantId = req.user.tenantId;
  const schema = z.object({
    name:     z.string().min(1).max(100),
    isActive: z.boolean().default(true),
  });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  try {
    const { rows } = await query(
      `INSERT INTO payment_methods ("tenantId", name, "isActive")
       VALUES ($1, $2, $3) RETURNING *`,
      [tenantId, parsed.data.name, parsed.data.isActive],
    );
    return res.status(201).json(rows[0]);
  } catch (err) {
    console.error('[payments/methods/POST]', err);
    return res.status(500).json({ error: 'Failed to create payment method.' });
  }
});

// ── Expiring Members ──────────────────────────────────────────────────────

router.get('/expiring', async (req, res) => {
  const tenantId = req.user.tenantId;
  const days     = Math.max(1, Math.min(365, parseInt(req.query.days ?? '30', 10)));

  try {
    const { rows } = await query(
      `SELECT m.*,
              p.id          AS "planId_",
              p.name        AS "planName_",
              p."durationDays" AS "planDays_",
              p.fee         AS "planFee_",
              p."isActive"  AS "planActive_"
       FROM   members m
       JOIN   plans p ON p.id = m."planId"
       WHERE  m."tenantId" = $1
         AND  m.status = 'Active'
         AND  m."membershipExpiry" IS NOT NULL
         AND  m."membershipExpiry" <= NOW() + ($2 || ' days')::INTERVAL
       ORDER  BY m."membershipExpiry" ASC`,
      [tenantId, days],
    );

    const now = Date.now();
    const result = rows.map(row => {
      const plan = {
        id:          row.planId_,
        name:        row.planName_,
        durationDays: row.planDays_,
        fee:         parseFloat(row.planFee_),
        isActive:    row.planActive_,
      };
      const { planId_: _1, planName_: _2, planDays_: _3, planFee_: _4, planActive_: _5, ...member } = row;
      const daysUntilExpiry = Math.ceil((new Date(member.membershipExpiry).getTime() - now) / 86400000);
      return { ...member, plan, daysUntilExpiry };
    });

    return res.json(result);
  } catch (err) {
    console.error('[payments/expiring]', err);
    return res.status(500).json({ error: 'Failed to fetch expiring members.' });
  }
});

// ── List Payments ─────────────────────────────────────────────────────────

router.get('/', async (req, res) => {
  const tenantId  = req.user.tenantId;
  const memberId  = req.query.memberId  ? parseInt(req.query.memberId,  10) : null;
  const startDate = req.query.startDate || null;
  const endDate   = req.query.endDate   || null;

  try {
    const { rows } = await query(
      `${PAYMENT_SELECT}
       WHERE  p."tenantId" = $1
         AND  ($2::int  IS NULL OR p."memberId"    = $2)
         AND  ($3::text IS NULL OR p."paymentDate" >= $3::timestamptz)
         AND  ($4::text IS NULL OR p."paymentDate" <= $4::timestamptz)
       ORDER  BY p."paymentDate" DESC, p.id DESC
       LIMIT  200`,
      [tenantId, memberId, startDate, endDate],
    );
    return res.json(rows.map(formatPayment));
  } catch (err) {
    console.error('[payments/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch payments.' });
  }
});

// ── Create Payment ────────────────────────────────────────────────────────

const paymentSchema = z.object({
  memberId:    z.number().int().positive(),
  amount:      z.number().positive('Amount must be greater than 0'),
  methodId:    z.number().int().positive(),
  notes:       z.string().max(500).nullable().optional(),
  paymentDate: z.string().datetime().optional(),
});

router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const parsed   = paymentSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { memberId, amount, methodId, notes, paymentDate } = parsed.data;

  try {
    const newId = await transaction(async (client) => {
      // Fetch member's current plan (including fee to snapshot at payment time)
      const { rows: mRows } = await client.query(
        `SELECT m."membershipExpiry", p.id AS "planId", p."durationDays", p.fee AS "planFee"
         FROM   members m
         JOIN   plans p ON p.id = m."planId"
         WHERE  m.id = $1 AND m."tenantId" = $2`,
        [memberId, tenantId],
      );
      if (!mRows[0]) {
        const e = new Error('Member not found.'); e.status = 404; throw e;
      }

      // Verify payment method belongs to this tenant
      const { rows: pmRows } = await client.query(
        `SELECT id FROM payment_methods WHERE id = $1 AND "tenantId" = $2`,
        [methodId, tenantId],
      );
      if (!pmRows[0]) {
        const e = new Error('Payment method not found.'); e.status = 404; throw e;
      }

      const { planId, durationDays: planDurationDays, planFee } = mRows[0];
      const payDate = paymentDate ? new Date(paymentDate) : new Date();

      // Insert payment — planFee snapshotted so partial-payment detection
      // remains correct even if the plan fee changes later.
      const { rows: pRows } = await client.query(
        `INSERT INTO payments
           ("tenantId","memberId","methodId","planId","planDurationDays","planFee",amount,notes,"paymentDate")
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)
         RETURNING id`,
        [tenantId, memberId, methodId, planId, planDurationDays, planFee, amount, notes ?? null, payDate],
      );

      // Replay all payments to set correct membershipExtendedTo + update member expiry
      await recalculateMemberExpiry(client, memberId, tenantId);

      // Also ensure member is Active after payment
      await client.query(
        `UPDATE members SET status = 'Active' WHERE id = $1 AND "tenantId" = $2`,
        [memberId, tenantId],
      );

      return pRows[0].id;
    });

    const { rows } = await query(`${PAYMENT_SELECT} WHERE p.id = $1`, [newId]);
    return res.status(201).json(formatPayment(rows[0]));
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    console.error('[payments/POST]', err);
    return res.status(500).json({ error: 'Failed to record payment.' });
  }
});

// ── Edit Payment ──────────────────────────────────────────────────────────

router.put('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid payment ID.' });

  const schema = z.object({
    amount:   z.number().positive().optional(),
    methodId: z.number().int().positive().optional(),
    notes:    z.string().max(500).nullable().optional(),
  });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  try {
    await transaction(async (client) => {
      const { rows } = await client.query(
        `SELECT * FROM payments WHERE id = $1 AND "tenantId" = $2`,
        [id, tenantId],
      );
      if (!rows[0]) { const e = new Error('Payment not found.'); e.status = 404; throw e; }

      const { amount, methodId, notes } = parsed.data;

      if (methodId !== undefined) {
        const { rows: pmRows } = await client.query(
          `SELECT id FROM payment_methods WHERE id = $1 AND "tenantId" = $2`,
          [methodId, tenantId],
        );
        if (!pmRows[0]) { const e = new Error('Payment method not found.'); e.status = 404; throw e; }
      }

      // Build SET clause dynamically to avoid type-inference issues with NULL parameters
      const sets = [];
      const vals = [];
      let p = 1;

      if (amount  !== undefined) { sets.push(`amount = $${p++}`);     vals.push(amount); }
      if (methodId !== undefined) { sets.push(`"methodId" = $${p++}`); vals.push(methodId); }
      if ('notes' in parsed.data) { sets.push(`notes = $${p++}`);      vals.push(notes ?? null); }

      if (sets.length > 0) {
        vals.push(id, tenantId);
        await client.query(
          `UPDATE payments SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`,
          vals,
        );
      }

      await recalculateMemberExpiry(client, rows[0].memberId, tenantId);
    });

    const { rows } = await query(`${PAYMENT_SELECT} WHERE p.id = $1`, [id]);
    return res.json(formatPayment(rows[0]));
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    console.error('[payments/PUT]', err);
    if (err.code === '42703') return res.status(500).json({ error: `DB migration needed: ${err.message}` });
    if (err.code === '42P01') return res.status(500).json({ error: `DB migration needed: table ${err.message}` });
    return res.status(500).json({ error: 'Failed to update payment.' });
  }
});

// ── Delete Payment ────────────────────────────────────────────────────────

router.delete('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid payment ID.' });

  try {
    await transaction(async (client) => {
      const { rows } = await client.query(
        `DELETE FROM payments WHERE id = $1 AND "tenantId" = $2 RETURNING *`,
        [id, tenantId],
      );
      if (!rows[0]) { const e = new Error('Payment not found.'); e.status = 404; throw e; }
      await recalculateMemberExpiry(client, rows[0].memberId, tenantId);
    });
    return res.status(204).end();
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    console.error('[payments/DELETE]', err);
    // Help diagnose missing migration columns
    if (err.code === '42703') return res.status(500).json({ error: `DB migration needed: ${err.message}` });
    if (err.code === '42P01') return res.status(500).json({ error: `DB migration needed: table ${err.message}` });
    return res.status(500).json({ error: 'Failed to delete payment.' });
  }
});

module.exports = router;
