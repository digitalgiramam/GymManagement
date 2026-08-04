/**
 * Super Admin platform routes — cross-tenant management for the platform owner.
 * All routes require a SUPER_ADMIN JWT (see middleware/auth.js requireSuperAdmin).
 *
 * GET  /api/admin/analytics         — platform-wide totals + signup growth
 * GET  /api/admin/tenants           — list every gym with owner/member/revenue summary
 * GET  /api/admin/tenants/:id       — drill-in detail for one gym (support view)
 * PUT  /api/admin/tenants/:id/status — suspend or reactivate a gym
 */

const express = require('express');
const { z }   = require('zod');
const { query } = require('../lib/db');

const router = express.Router();

// ── GET /api/admin/analytics ────────────────────────────────────────────────
router.get('/analytics', async (req, res) => {
  try {
    const { rows: totals } = await query(`
      SELECT
        (SELECT COUNT(*) FROM tenants)                                   AS "totalTenants",
        (SELECT COUNT(*) FROM tenants WHERE "isSuspended" = false)       AS "activeTenants",
        (SELECT COUNT(*) FROM tenants WHERE "isSuspended" = true)        AS "suspendedTenants",
        (SELECT COUNT(*) FROM members)                                   AS "totalMembers",
        (SELECT COUNT(*) FROM staff)                                     AS "totalStaff",
        (SELECT COALESCE(SUM(amount), 0) FROM payments)                  AS "totalRevenue"
    `);

    // Tenant signups per month, last 6 months
    const { rows: signupGrowth } = await query(`
      SELECT to_char(date_trunc('month', "createdAt"), 'YYYY-MM') AS month,
             COUNT(*) AS count
      FROM tenants
      WHERE "createdAt" >= NOW() - INTERVAL '6 months'
      GROUP BY 1 ORDER BY 1
    `);

    // Revenue per month, last 6 months (platform-wide, across all tenants)
    const { rows: revenueGrowth } = await query(`
      SELECT to_char(date_trunc('month', "paymentDate"), 'YYYY-MM') AS month,
             COALESCE(SUM(amount), 0) AS total
      FROM payments
      WHERE "paymentDate" >= NOW() - INTERVAL '6 months'
      GROUP BY 1 ORDER BY 1
    `);

    const t = totals[0];
    return res.json({
      totalTenants:     parseInt(t.totalTenants, 10),
      activeTenants:    parseInt(t.activeTenants, 10),
      suspendedTenants: parseInt(t.suspendedTenants, 10),
      totalMembers:     parseInt(t.totalMembers, 10),
      totalStaff:       parseInt(t.totalStaff, 10),
      totalRevenue:     parseFloat(t.totalRevenue),
      signupGrowth:     signupGrowth.map(r => ({ month: r.month, count: parseInt(r.count, 10) })),
      revenueGrowth:    revenueGrowth.map(r => ({ month: r.month, total: parseFloat(r.total) })),
    });
  } catch (err) {
    console.error('[admin/analytics]', err);
    return res.status(500).json({ error: 'Failed to fetch analytics.' });
  }
});

// ── GET /api/admin/tenants ──────────────────────────────────────────────────
router.get('/tenants', async (req, res) => {
  const search = req.query.search?.toString().trim() || null;
  try {
    const { rows } = await query(
      `SELECT t.id, t.name, t.address, t.phone, t."isSuspended", t."createdAt",
              u.name  AS "ownerName",
              u.email AS "ownerEmail",
              (SELECT COUNT(*) FROM members m WHERE m."tenantId" = t.id)  AS "memberCount",
              (SELECT COUNT(*) FROM staff s   WHERE s."tenantId" = t.id)  AS "staffCount",
              (SELECT COALESCE(SUM(amount), 0) FROM payments p WHERE p."tenantId" = t.id) AS "totalRevenue"
       FROM tenants t
       LEFT JOIN users u ON u."tenantId" = t.id
       WHERE ($1::text IS NULL OR t.name ILIKE '%' || $1 || '%' OR u.email ILIKE '%' || $1 || '%')
       ORDER BY t."createdAt" DESC`,
      [search],
    );

    const tenants = rows.map(r => ({
      ...r,
      memberCount:  parseInt(r.memberCount, 10),
      staffCount:   parseInt(r.staffCount, 10),
      totalRevenue: parseFloat(r.totalRevenue),
    }));
    return res.json(tenants);
  } catch (err) {
    console.error('[admin/tenants/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch tenants.' });
  }
});

// ── GET /api/admin/tenants/:id ──────────────────────────────────────────────
router.get('/tenants/:id', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });

  try {
    const { rows: tRows } = await query(
      `SELECT t.*, u.name AS "ownerName", u.email AS "ownerEmail"
       FROM tenants t LEFT JOIN users u ON u."tenantId" = t.id
       WHERE t.id = $1`,
      [id],
    );
    if (!tRows[0]) return res.status(404).json({ error: 'Tenant not found.' });

    const { rows: memberStats } = await query(
      `SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE status = 'Active') AS active
       FROM members WHERE "tenantId" = $1`,
      [id],
    );
    const { rows: staffRows } = await query(
      `SELECT id, "fullName", email, role FROM staff WHERE "tenantId" = $1 ORDER BY "createdAt" DESC LIMIT 20`,
      [id],
    );
    const { rows: revenueRows } = await query(
      `SELECT COALESCE(SUM(amount), 0) AS total, COUNT(*) AS count
       FROM payments WHERE "tenantId" = $1`,
      [id],
    );
    const { rows: recentPayments } = await query(
      `SELECT p.id, p.amount, p."paymentDate", m."fullName" AS "memberName"
       FROM payments p JOIN members m ON m.id = p."memberId"
       WHERE p."tenantId" = $1 ORDER BY p."paymentDate" DESC LIMIT 10`,
      [id],
    );

    return res.json({
      tenant: tRows[0],
      memberStats: { total: parseInt(memberStats[0].total, 10), active: parseInt(memberStats[0].active, 10) },
      staff: staffRows,
      revenue: { total: parseFloat(revenueRows[0].total), paymentCount: parseInt(revenueRows[0].count, 10) },
      recentPayments: recentPayments.map(p => ({ ...p, amount: parseFloat(p.amount) })),
    });
  } catch (err) {
    console.error('[admin/tenants/:id/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch tenant detail.' });
  }
});

// ── PUT /api/admin/tenants/:id/status ───────────────────────────────────────
const statusSchema = z.object({ isSuspended: z.boolean() });

router.put('/tenants/:id/status', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });

  const result = statusSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  try {
    const { rows } = await query(
      `UPDATE tenants SET "isSuspended" = $1 WHERE id = $2 RETURNING id, name, "isSuspended"`,
      [result.data.isSuspended, id],
    );
    if (!rows[0]) return res.status(404).json({ error: 'Tenant not found.' });
    return res.json(rows[0]);
  } catch (err) {
    console.error('[admin/tenants/:id/status/PUT]', err);
    return res.status(500).json({ error: 'Failed to update tenant status.' });
  }
});

module.exports = router;
