/**
 * Attendance routes (multi-tenant)
 * POST /api/attendance  — record a check-in (prevents duplicate within configurable window)
 * GET  /api/attendance  — today's check-ins
 *
 * All queries scoped to req.user.tenantId
 */

const express = require('express');
const { z }   = require('zod');
const prisma = require('../lib/prisma');

const router = express.Router();

const checkInSchema = z.object({
  memberId: z.number().int().positive('memberId is required'),
});

// ── POST /api/attendance ───────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = checkInSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  const { memberId } = result.data;

  try {
    // Get tenant settings for duplicate window
    const tenant = await prisma.tenant.findUnique({
      where:  { id: tenantId },
      select: { checkInWindowMinutes: true },
    });
    const windowMs = (tenant?.checkInWindowMinutes ?? 5) * 60 * 1000;

    // Verify member belongs to this tenant
    const member = await prisma.member.findFirst({
      where:  { id: memberId, tenantId },
      select: { id: true, fullName: true, status: true },
    });
    if (!member) return res.status(404).json({ error: 'Member not found.' });

    // Check for recent duplicate
    const recentCutoff = new Date(Date.now() - windowMs);
    const recent = await prisma.attendance.findFirst({
      where: { memberId, tenantId, checkedInAt: { gte: recentCutoff } },
    });
    if (recent) {
      return res.status(409).json({
        error: `${member.fullName} was already checked in within the last ${tenant?.checkInWindowMinutes ?? 5} minutes.`,
      });
    }

    const record = await prisma.attendance.create({
      data:    { memberId, tenantId },
      include: { member: { select: { id: true, fullName: true, phone: true } } },
    });

    return res.status(201).json(record);
  } catch (err) {
    console.error('[attendance/POST]', err);
    return res.status(500).json({ error: 'Failed to record check-in.' });
  }
});

// ── GET /api/attendance ────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const now      = new Date();
  const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const endOfDay   = new Date(startOfDay.getTime() + 24 * 60 * 60 * 1000);

  try {
    const records = await prisma.attendance.findMany({
      where: {
        tenantId,
        checkedInAt: { gte: startOfDay, lt: endOfDay },
      },
      include: {
        member: {
          select: {
            id: true, fullName: true, phone: true,
            plan: { select: { name: true } },
          },
        },
      },
      orderBy: { checkedInAt: 'desc' },
    });
    return res.json(records);
  } catch (err) {
    console.error('[attendance/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch attendance.' });
  }
});

module.exports = router;
