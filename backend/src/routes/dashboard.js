/**
 * Dashboard route
 * GET /api/dashboard/stats
 *
 * Returns:
 *   totalActiveMembers    — count of Active members
 *   totalInactiveMembers  — count of Inactive members
 *   todayCheckIns         — check-ins since midnight today
 *   currentMonthRevenue   — sum of payments in current calendar month
 *   last5CheckIns         — 5 most recent attendance records
 *   last5Payments         — 5 most recent payment records
 */

const express = require('express');
const { PrismaClient } = require('@prisma/client');

const router = express.Router();
const prisma = new PrismaClient();

router.get('/stats', async (_req, res) => {
  const now         = new Date();
  const startOfDay  = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);

  try {
    const [
      totalActive,
      totalInactive,
      todayCheckIns,
      monthRevenueAgg,
      last5CheckIns,
      last5Payments,
    ] = await prisma.$transaction([
      prisma.member.count({ where: { status: 'Active'   } }),
      prisma.member.count({ where: { status: 'Inactive' } }),
      prisma.attendance.count({
        where: { checkedInAt: { gte: startOfDay } },
      }),
      prisma.payment.aggregate({
        _sum: { amount: true },
        where: { paymentDate: { gte: startOfMonth } },
      }),
      prisma.attendance.findMany({
        take:    5,
        orderBy: { checkedInAt: 'desc' },
        include: {
          member: { select: { id: true, fullName: true, phone: true } },
        },
      }),
      prisma.payment.findMany({
        take:    5,
        orderBy: { paymentDate: 'desc' },
        include: {
          member: { select: { id: true, fullName: true, phone: true } },
        },
      }),
    ]);

    return res.json({
      totalActiveMembers:   totalActive,
      totalInactiveMembers: totalInactive,
      todayCheckIns,
      currentMonthRevenue:  monthRevenueAgg._sum.amount ?? 0,
      last5CheckIns,
      last5Payments,
    });
  } catch (err) {
    console.error('[dashboard/stats]', err);
    return res.status(500).json({ error: 'Failed to fetch dashboard stats.' });
  }
});

module.exports = router;
