/**
 * Member Portal routes — for authenticated gym members
 * All routes require JWT with role = 'MEMBER' and req.user.memberId
 *
 * GET  /api/member-portal/me            — own profile
 * GET  /api/member-portal/me/attendance — own attendance history
 * GET  /api/member-portal/me/payments   — own payment history
 */

const express = require('express');
const prisma = require('../lib/prisma');

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
    const member = await prisma.member.findFirst({
      where: { id: memberId, tenantId },
      include: { plan: true },
    });
    if (!member) return res.status(404).json({ error: 'Member not found.' });

    // Compute membership expiry
    const joinDate = new Date(member.joinDate);
    const expiry   = new Date(joinDate.getTime() + member.plan.durationDays * 86400_000);
    const daysLeft = Math.ceil((expiry - Date.now()) / 86400_000);

    return res.json({
      id:               member.id,
      fullName:         member.fullName,
      phone:            member.phone,
      email:            member.email,
      location:         member.location,
      joinDate:         member.joinDate,
      status:           member.status,
      plan:             member.plan,
      membershipExpiry: expiry.toISOString(),
      daysUntilExpiry:  daysLeft,
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
    const records = await prisma.attendance.findMany({
      where:   { memberId, tenantId },
      orderBy: { checkedInAt: 'desc' },
      take:    100,
    });
    return res.json(records);
  } catch (err) {
    console.error('[member-portal/attendance]', err);
    return res.status(500).json({ error: 'Failed to fetch attendance.' });
  }
});

// ── GET /api/member-portal/me/payments ────────────────────────────────────
router.get('/me/payments', async (req, res) => {
  const { memberId, tenantId } = req.user;
  try {
    const payments = await prisma.payment.findMany({
      where:   { memberId, tenantId },
      include: { method: true },
      orderBy: { paymentDate: 'desc' },
      take:    100,
    });
    return res.json(payments);
  } catch (err) {
    console.error('[member-portal/payments]', err);
    return res.status(500).json({ error: 'Failed to fetch payments.' });
  }
});

module.exports = router;
