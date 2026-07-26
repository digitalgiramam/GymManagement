/**
 * Member Portal routes — for authenticated gym members
 * GET /api/member-portal/me
 * GET /api/member-portal/me/attendance
 * GET /api/member-portal/me/payments
 */

const express   = require('express');
const { query } = require('../lib/db');

const router = express.Router();

function requireMemberRole(req, res, next) {
  if (req.user?.role !== 'MEMBER' || !req.user?.memberId) {
    return res.status(403).json({ error: 'Member access only.' });
  }
  next();
}

router.use(requireMemberRole);

// ── GET /api/member-portal/me ──────────────────────────────────────────────
router.get('/me', async (req, res) => {
  const { memberId, tenantId } = req.user;
  try {
    const { rows } = await query(
      `SELECT m.*,
              p.id AS "planId_", p.name AS "planName_", p."durationDays" AS "planDays_",
              p.fee AS "planFee_", p."isActive" AS "planActive_"
       FROM members m JOIN plans p ON p.id = m."planId"
       WHERE m.id = $1 AND m."tenantId" = $2`,
      [memberId, tenantId],
    );
    if (!rows[0]) return res.status(404).json({ error: 'Member not found.' });

    const row  = rows[0];
    const plan = { id: row.planId_, name: row.planName_, durationDays: row.planDays_, fee: row.planFee_, isActive: row.planActive_ };
    const { planId_: _1, planName_: _2, planDays_: _3, planFee_: _4, planActive_: _5, ...member } = row;

    const joinDate = new Date(member.joinDate);
    const expiry   = new Date(joinDate.getTime() + plan.durationDays * 86400000);
    const daysLeft = Math.ceil((expiry.getTime() - Date.now()) / 86400000);

    return res.json({
      id: member.id, fullName: member.fullName, phone: member.phone,
      email: member.email, location: member.location, joinDate: member.joinDate,
      status: member.status, plan,
      membershipExpiry: expiry.toISOString(), daysUntilExpiry: daysLeft,
    });
  } catch (err) {
    console.error('[member-portal/me]', err);
    return res.status(500).json({ error: 'Failed to fetch profile.' });
  }
});

// ── GET /api/member-portal/me/attendance ──────────────────────────────────
router.get('/me/attendance', async (req, res) => {
  const { memberId, tenantId } = req.user;
  try {
    const { rows } = await query(
      `SELECT * FROM attendance WHERE "memberId" = $1 AND "tenantId" = $2 ORDER BY "checkedInAt" DESC LIMIT 100`,
      [memberId, tenantId],
    );
    return res.json(rows);
  } catch (err) {
    console.error('[member-portal/attendance]', err);
    return res.status(500).json({ error: 'Failed to fetch attendance.' });
  }
});

// ── GET /api/member-portal/me/payments ────────────────────────────────────
router.get('/me/payments', async (req, res) => {
  const { memberId, tenantId } = req.user;
  try {
    const { rows } = await query(
      `SELECT p.*, pm.id AS "methodId_", pm.name AS "methodName_", pm."isActive" AS "methodActive_"
       FROM payments p
       JOIN payment_methods pm ON pm.id = p."methodId"
       WHERE p."memberId" = $1 AND p."tenantId" = $2
       ORDER BY p."paymentDate" DESC LIMIT 100`,
      [memberId, tenantId],
    );
    const payments = rows.map(r => {
      const { methodId_: mid, methodName_: mname, methodActive_: mactive, ...rest } = r;
      return { ...rest, method: { id: mid, name: mname, isActive: mactive } };
    });
    return res.json(payments);
  } catch (err) {
    console.error('[member-portal/payments]', err);
    return res.status(500).json({ error: 'Failed to fetch payments.' });
  }
});

module.exports = router;
