/**
 * Staff Portal routes — TRAINER & RECEPTIONIST only
 *
 * GET  /api/staff-portal/my-members    — members assigned to this trainer
 * GET  /api/staff-portal/my-attendance — today's check-ins across trainer's members
 * POST /api/staff-portal/attendance    — mark check-in for any member
 *
 * Auth: authenticateJWT (tenantId + staffId from token)
 * Trainers see only members WHERE trainerId = their staffId.
 * Receptionists can call GET /api/members directly (no restriction needed here).
 */

const express   = require('express');
const { z }     = require('zod');
const { query } = require('../lib/db');

const router = express.Router();

// ── GET /api/staff-portal/my-members ──────────────────────────────────────
// Returns members assigned to the logged-in trainer.
router.get('/my-members', async (req, res) => {
  const { tenantId, staffId } = req.user;
  if (!tenantId) return res.status(403).json({ error: 'No gym associated with this account.' });
  if (!staffId)  return res.status(403).json({ error: 'Not a staff account.' });

  try {
    const { rows } = await query(
      `SELECT m.*,
              p.id            AS "planId_",
              p.name          AS "planName_",
              p."durationDays" AS "planDays_",
              p.fee           AS "planFee_",
              p."isActive"    AS "planActive_",
              lp.amount                 AS "lpAmount_",
              lp."planFee"              AS "lpPlanFee_",
              lp."membershipExtendedTo" AS "lpExpiry_"
       FROM members m
       JOIN plans p ON p.id = m."planId"
       LEFT JOIN LATERAL (
         SELECT amount, "planFee", "membershipExtendedTo"
         FROM   payments
         WHERE  "memberId" = m.id AND "tenantId" = m."tenantId"
         ORDER  BY "paymentDate" DESC, id DESC
         LIMIT  1
       ) lp ON true
       WHERE m."tenantId" = $1
         AND m."trainerId" = $2
       ORDER BY m."fullName" ASC`,
      [tenantId, staffId],
    );

    const result = rows.map(row => {
      const plan = {
        id: row.planId_, name: row.planName_,
        durationDays: row.planDays_, fee: parseFloat(row.planFee_), isActive: row.planActive_,
      };
      const daysUntilExpiry = row.membershipExpiry
        ? Math.ceil((new Date(row.membershipExpiry).getTime() - Date.now()) / 86400000)
        : null;

      // payment status
      let paymentStatus = 'Not Paid', lastPaymentAmount = 0, lastPlanFee = 0, overdueAmount = 0;
      if (row.lpAmount_ != null) {
        const paid    = parseFloat(row.lpAmount_)  || 0;
        const planFee = parseFloat(row.lpPlanFee_) || 0;
        const ext     = row.lpExpiry_ ? new Date(row.lpExpiry_) : null;
        const isExpired = !ext || ext <= new Date();
        lastPaymentAmount = paid;
        lastPlanFee       = planFee;
        if (isExpired)              paymentStatus = 'Not Paid';
        else if (planFee > 0 && paid < planFee) { paymentStatus = 'Partial Paid'; overdueAmount = planFee - paid; }
        else                        paymentStatus = 'Full Paid';
      }

      const { planId_: _1, planName_: _2, planDays_: _3, planFee_: _4, planActive_: _5,
              lpAmount_: _6, lpPlanFee_: _7, lpExpiry_: _8, ...member } = row;
      return { ...member, plan, daysUntilExpiry, paymentStatus, lastPaymentAmount, lastPlanFee, overdueAmount };
    });

    return res.json(result);
  } catch (err) {
    console.error('[staff-portal/my-members]', err);
    return res.status(500).json({ error: 'Failed to fetch members.' });
  }
});

// ── GET /api/staff-portal/my-attendance ───────────────────────────────────
// Today's check-ins for the trainer's assigned members.
router.get('/my-attendance', async (req, res) => {
  const { tenantId, staffId } = req.user;
  if (!tenantId) return res.status(403).json({ error: 'No gym associated with this account.' });
  if (!staffId)  return res.status(403).json({ error: 'Not a staff account.' });

  try {
    const { rows } = await query(
      `SELECT a.*, m."fullName" AS "memberName_", m.phone AS "memberPhone_"
       FROM attendance a
       JOIN members m ON m.id = a."memberId"
       WHERE a."tenantId" = $1
         AND m."trainerId" = $2
         AND a."checkedInAt" >= CURRENT_DATE
       ORDER BY a."checkedInAt" DESC`,
      [tenantId, staffId],
    );

    const result = rows.map(row => {
      const { memberName_: fullName, memberPhone_: phone, ...att } = row;
      return { ...att, member: { id: att.memberId, fullName, phone } };
    });

    return res.json(result);
  } catch (err) {
    console.error('[staff-portal/my-attendance]', err);
    return res.status(500).json({ error: 'Failed to fetch attendance.' });
  }
});

// ── POST /api/staff-portal/attendance ─────────────────────────────────────
// Mark a check-in. Staff can check in any member in their tenant (same as /api/attendance).
const checkInSchema = z.object({
  memberId: z.number().int().positive('Member ID is required'),
});

router.post('/attendance', async (req, res) => {
  const { tenantId } = req.user;
  if (!tenantId) return res.status(403).json({ error: 'No gym associated with this account.' });

  const parsed = checkInSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { memberId } = parsed.data;

  try {
    // Verify member belongs to this tenant
    const { rows: mRows } = await query(
      `SELECT id FROM members WHERE id = $1 AND "tenantId" = $2`,
      [memberId, tenantId],
    );
    if (!mRows[0]) return res.status(404).json({ error: 'Member not found.' });

    const { rows } = await query(
      `INSERT INTO attendance ("tenantId","memberId","checkedInAt")
       VALUES ($1,$2,NOW()) RETURNING *`,
      [tenantId, memberId],
    );
    return res.status(201).json(rows[0]);
  } catch (err) {
    console.error('[staff-portal/attendance]', err);
    return res.status(500).json({ error: 'Failed to record attendance.' });
  }
});

module.exports = router;
