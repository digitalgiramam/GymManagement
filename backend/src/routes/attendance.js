/**
 * Attendance routes
 * POST /api/attendance  — record a check-in (prevents duplicate within 5 min)
 * GET  /api/attendance  — today's check-ins
 */

const express = require('express');
const { z }   = require('zod');
const { PrismaClient } = require('@prisma/client');

const router = express.Router();
const prisma = new PrismaClient();

const DUPLICATE_WINDOW_MS = 5 * 60 * 1000; // 5 minutes

const checkInSchema = z.object({
  memberId: z.number().int().positive('memberId is required'),
});

// ── POST /api/attendance ───────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const result = checkInSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  const { memberId } = result.data;

  try {
    // Verify member exists
    const member = await prisma.member.findUnique({
      where:   { id: memberId },
      select:  { id: true, fullName: true, status: true },
    });
    if (!member) return res.status(404).json({ error: 'Member not found.' });

    // Check for a check-in within the last 5 minutes
    const recentCutoff = new Date(Date.now() - DUPLICATE_WINDOW_MS);
    const recent = await prisma.attendance.findFirst({
      where: {
        memberId,
        checkedInAt: { gte: recentCutoff },
      },
    });
    if (recent) {
      return res.status(409).json({
        error: `${member.fullName} was already checked in within the last 5 minutes.`,
      });
    }

    const record = await prisma.attendance.create({
      data:    { memberId },
      include: { member: { select: { fullName: true, phone: true } } },
    });

    return res.status(201).json(record);
  } catch (err) {
    console.error('[attendance/POST]', err);
    return res.status(500).json({ error: 'Failed to record check-in.' });
  }
});

// ── GET /api/attendance ────────────────────────────────────────────────────
// Returns today's check-ins (from midnight local → next midnight)
router.get('/', async (req, res) => {
  const now       = new Date();
  const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const endOfDay   = new Date(startOfDay.getTime() + 24 * 60 * 60 * 1000);

  try {
    const records = await prisma.attendance.findMany({
      where: {
        checkedInAt: { gte: startOfDay, lt: endOfDay },
      },
      include: {
        member: { select: { id: true, fullName: true, phone: true, plan: { select: { name: true } } } },
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
