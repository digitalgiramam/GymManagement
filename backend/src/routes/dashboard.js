/**
 * Dashboard route (multi-tenant)
 * GET /api/dashboard/stats
 *
 * Returns:
 *   totalActiveMembers    — count of Active members
 *   totalInactiveMembers  — count of Inactive members
 *   todayCheckIns         — check-ins since midnight today
 *   currentMonthRevenue   — sum of payments in current calendar month
 *   currentMonthExpenses  — sum of expenses in current calendar month
 *   netProfit             — revenue - expenses
 *   last5CheckIns         — 5 most recent attendance records
 *   last5Payments         — 5 most recent payment records
 *   last5Expenses         — 5 most recent expenses
 *
 * All queries scoped to req.user.tenantId
 */

const express = require('express');
const prisma = require('../lib/prisma');

const router = express.Router();

router.get('/stats', async (req, res) => {
  const tenantId   = req.user.tenantId;
  const now        = new Date();
  const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);

  try {
    const [
      totalActive,
      totalInactive,
      todayCheckIns,
      monthRevenueAgg,
      monthExpenseAgg,
      last5CheckIns,
      last5Payments,
      last5Expenses,
    ] = await prisma.$transaction([
      prisma.member.count({ where: { tenantId, status: 'Active'   } }),
      prisma.member.count({ where: { tenantId, status: 'Inactive' } }),
      prisma.attendance.count({
        where: { tenantId, checkedInAt: { gte: startOfDay } },
      }),
      prisma.payment.aggregate({
        _sum: { amount: true },
        where: { tenantId, paymentDate: { gte: startOfMonth } },
      }),
      prisma.expense.aggregate({
        _sum: { amount: true },
        where: { tenantId, expenseDate: { gte: startOfMonth } },
      }),
      prisma.attendance.findMany({
        take:    5,
        where:   { tenantId },
        orderBy: { checkedInAt: 'desc' },
        include: { member: { select: { id: true, fullName: true, phone: true } } },
      }),
      prisma.payment.findMany({
        take:    5,
        where:   { tenantId },
        orderBy: { paymentDate: 'desc' },
        include: {
          member: { select: { id: true, fullName: true, phone: true } },
          method: { select: { id: true, name: true } },
        },
      }),
      prisma.expense.findMany({
        take:    5,
        where:   { tenantId },
        orderBy: { expenseDate: 'desc' },
        include: { category: { select: { id: true, name: true } } },
      }),
    ]);

    const revenue  = monthRevenueAgg._sum.amount  ?? 0;
    const expenses = monthExpenseAgg._sum.amount ?? 0;

    return res.json({
      totalActiveMembers:   totalActive,
      totalInactiveMembers: totalInactive,
      todayCheckIns,
      currentMonthRevenue:  revenue,
      currentMonthExpenses: expenses,
      netProfit:            revenue - expenses,
      last5CheckIns,
      last5Payments,
      last5Expenses,
    });
  } catch (err) {
    console.error('[dashboard/stats]', err);
    return res.status(500).json({ error: 'Failed to fetch dashboard stats.' });
  }
});

module.exports = router;
