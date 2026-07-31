/**
 * Dashboard route (multi-tenant)
 * GET /api/dashboard/stats
 */

const express   = require('express');
const { query } = require('../lib/db');

const router = express.Router();

router.get('/stats', async (req, res) => {
  const tenantId    = req.user.tenantId;
  const now         = new Date();
  const startOfDay  = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);

  try {
    // Run all aggregate queries in parallel
    const [
      activeRes,
      inactiveRes,
      todayRes,
      revenueRes,
      expenseRes,
      checkInsRes,
      expensesRes,
    ] = await Promise.all([
      query(`SELECT COUNT(*)::int AS count FROM members WHERE "tenantId" = $1 AND status = 'Active'`,   [tenantId]),
      query(`SELECT COUNT(*)::int AS count FROM members WHERE "tenantId" = $1 AND status = 'Inactive'`, [tenantId]),
      query(`SELECT COUNT(*)::int AS count FROM attendance WHERE "tenantId" = $1 AND "checkedInAt" >= $2`, [tenantId, startOfDay]),
      query(`SELECT COALESCE(SUM(amount), 0) AS total FROM payments WHERE "tenantId" = $1 AND "paymentDate" >= $2`, [tenantId, startOfMonth]),
      query(`SELECT COALESCE(SUM(amount), 0) AS total FROM expenses WHERE "tenantId" = $1 AND "expenseDate" >= $2`,  [tenantId, startOfMonth]),
      // Last 5 check-ins
      query(
        `SELECT a.*, m.id AS "memberId_", m."fullName" AS "memberName_", m.phone AS "memberPhone_"
         FROM attendance a
         JOIN members m ON m.id = a."memberId"
         WHERE a."tenantId" = $1
         ORDER BY a."checkedInAt" DESC LIMIT 5`,
        [tenantId],
      ),
      // Last 5 expenses
      query(
        `SELECT e.*, ec.id AS "catId_", ec.name AS "catName_"
         FROM expenses e
         JOIN expense_categories ec ON ec.id = e."categoryId"
         WHERE e."tenantId" = $1
         ORDER BY e."expenseDate" DESC LIMIT 5`,
        [tenantId],
      ),
    ]);

    const last5CheckIns = checkInsRes.rows.map(r => {
      const { memberId_: mid, memberName_: mname, memberPhone_: mphone, ...att } = r;
      return { ...att, member: { id: mid, fullName: mname, phone: mphone } };
    });

    const last5Expenses = expensesRes.rows.map(r => {
      const { catId_: cid, catName_: cname, ...rest } = r;
      return { ...rest, category: { id: cid, name: cname } };
    });

    const revenue  = parseFloat(revenueRes.rows[0].total);
    const expenses = parseFloat(expenseRes.rows[0].total);

    return res.json({
      totalActiveMembers:   activeRes.rows[0].count,
      totalInactiveMembers: inactiveRes.rows[0].count,
      todayCheckIns:        todayRes.rows[0].count,
      currentMonthRevenue:  revenue,
      currentMonthExpenses: expenses,
      netProfit:            revenue - expenses,
      last5CheckIns,
      last5Expenses,
    });
  } catch (err) {
    console.error('[dashboard/stats]', err);
    return res.status(500).json({ error: 'Failed to fetch dashboard stats.' });
  }
});

module.exports = router;
