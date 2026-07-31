/**
 * Member Portal routes — for authenticated gym members
 * GET /api/member-portal/me            → profile + payment status
 * GET /api/member-portal/me/attendance → attendance history
 * GET /api/member-portal/me/payments   → payment history with status
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

// ── Shared helper: compute payment status from a payment row ───────────────
function computePaymentStatus(latestPayment) {
  if (!latestPayment) return { paymentStatus: 'Not Paid', overdueAmount: 0, lastPaymentAmount: 0 };

  const paid    = parseFloat(latestPayment.amount)  || 0;
  const planFee = parseFloat(latestPayment.planFee) || 0;
  const ext     = latestPayment.membershipExtendedTo ? new Date(latestPayment.membershipExtendedTo) : null;
  const now     = new Date();
  const isExpired = !ext || ext <= now;

  let paymentStatus;
  let overdueAmount = 0;

  if (isExpired) {
    paymentStatus = 'Not Paid';  // subscription has lapsed
  } else if (planFee > 0 && paid < planFee) {
    paymentStatus = 'Partial Paid';
    overdueAmount = planFee - paid;
  } else {
    paymentStatus = 'Full Paid';
  }

  return { paymentStatus, overdueAmount, lastPaymentAmount: paid, lastPlanFee: planFee };
}

// ── GET /api/member-portal/me ──────────────────────────────────────────────
router.get('/me', async (req, res) => {
  const { memberId, tenantId } = req.user;
  try {
    // Member + plan
    const { rows } = await query(
      `SELECT m.*,
              p.id          AS "planId_",
              p.name        AS "planName_",
              p."durationDays" AS "planDays_",
              p.fee         AS "planFee_",
              p."isActive"  AS "planActive_"
       FROM members m
       JOIN plans p ON p.id = m."planId"
       WHERE m.id = $1 AND m."tenantId" = $2`,
      [memberId, tenantId],
    );
    if (!rows[0]) return res.status(404).json({ error: 'Member not found.' });

    const row  = rows[0];
    const plan = {
      id: row.planId_, name: row.planName_,
      durationDays: row.planDays_, fee: parseFloat(row.planFee_), isActive: row.planActive_,
    };
    const { planId_: _1, planName_: _2, planDays_: _3, planFee_: _4, planActive_: _5, ...member } = row;

    // Latest payment — used for payment status + correct expiry
    const { rows: payRows } = await query(
      `SELECT amount, "planFee", "membershipExtendedTo"
       FROM   payments
       WHERE  "memberId" = $1 AND "tenantId" = $2
       ORDER  BY "paymentDate" DESC, id DESC
       LIMIT  1`,
      [memberId, tenantId],
    );

    // Expiry: prefer members.membershipExpiry (set by recalculate), fallback to joinDate arithmetic
    let membershipExpiry;
    if (member.membershipExpiry) {
      membershipExpiry = new Date(member.membershipExpiry);
    } else if (payRows[0]?.membershipExtendedTo) {
      membershipExpiry = new Date(payRows[0].membershipExtendedTo);
    } else {
      // No payments yet — fallback: joinDate + plan duration
      membershipExpiry = new Date(new Date(member.joinDate).getTime() + plan.durationDays * 86400000);
    }

    const daysLeft = Math.ceil((membershipExpiry.getTime() - Date.now()) / 86400000);

    const { paymentStatus, overdueAmount, lastPaymentAmount, lastPlanFee } =
      computePaymentStatus(payRows[0]);

    return res.json({
      id: member.id, fullName: member.fullName, phone: member.phone,
      email: member.email, location: member.location, joinDate: member.joinDate,
      status: member.status, plan,
      membershipExpiry:  membershipExpiry.toISOString(),
      daysUntilExpiry:   daysLeft,
      // Payment status fields
      paymentStatus,      // "Full Paid" | "Partial Paid" | "Not Paid"
      overdueAmount,      // outstanding balance (0 if fully paid or no subscription)
      lastPaymentAmount,  // amount paid in the latest payment
      lastPlanFee,        // plan fee at last payment time
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
      `SELECT * FROM attendance
       WHERE "memberId" = $1 AND "tenantId" = $2
       ORDER BY "checkedInAt" DESC LIMIT 100`,
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
      `SELECT p.*,
              pm.id    AS "methodId_",
              pm.name  AS "methodName_",
              pl.name  AS "planName_"
       FROM   payments p
       JOIN   payment_methods pm ON pm.id = p."methodId"
       LEFT JOIN plans pl        ON pl.id = p."planId"
       WHERE  p."memberId" = $1 AND p."tenantId" = $2
       ORDER  BY p."paymentDate" DESC
       LIMIT  100`,
      [memberId, tenantId],
    );

    const now = new Date();
    const payments = rows.map(r => {
      const { methodId_: mid, methodName_: mname, planName_: pname, ...p } = r;
      const paidAmount = parseFloat(p.amount)  || 0;
      const planFee    = parseFloat(p.planFee) || 0;
      const ext        = p.membershipExtendedTo ? new Date(p.membershipExtendedTo) : null;
      const isExpired  = !ext || ext <= now;
      const overdueAmount = isExpired ? 0 : Math.max(0, planFee - paidAmount);

      let membershipStatus;
      if (isExpired)                        membershipStatus = 'Not Paid';
      else if (planFee > 0 && paidAmount < planFee) membershipStatus = 'Partial Paid';
      else                                  membershipStatus = 'Full Paid';

      return {
        ...p,
        amount: paidAmount,
        planFee,
        overdueAmount,
        planName: pname ?? null,
        membershipStatus,
        method: { id: mid, name: mname },
      };
    });

    return res.json(payments);
  } catch (err) {
    console.error('[member-portal/payments]', err);
    return res.status(500).json({ error: 'Failed to fetch payments.' });
  }
});

module.exports = router;
