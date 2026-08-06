/**
 * Super Admin platform routes — cross-tenant management for the platform owner.
 * All routes require a SUPER_ADMIN JWT (see middleware/auth.js requireSuperAdmin).
 *
 * GET    /api/admin/analytics                — platform-wide totals + signup growth
 * GET    /api/admin/tenants                   — list every gym with owner/member/revenue summary
 * POST   /api/admin/tenants                   — create a new gym + owner account
 * GET    /api/admin/tenants/:id               — drill-in detail for one gym (support view)
 * PUT    /api/admin/tenants/:id/status        — suspend or reactivate a gym
 * DELETE /api/admin/tenants/:id                — permanently delete a gym and all its data
 * PUT    /api/admin/tenants/:id/owner          — edit the gym owner's name/email
 * POST   /api/admin/tenants/:id/owner/reset-password — set a new password for the owner
 * POST   /api/admin/tenants/:id/impersonate    — issue a short-lived OWNER token for support
 * PUT    /api/admin/tenants/:id/settings       — edit gym settings on the owner's behalf
 * GET/POST/PUT/DELETE /api/admin/tenants/:id/plans[/:planId]   — manage plans on the owner's behalf
 * GET/POST/PUT/DELETE /api/admin/tenants/:id/staff[/:staffId]  — manage staff on the owner's behalf
 */

const express = require('express');
const jwt     = require('jsonwebtoken');
const bcrypt  = require('bcryptjs');
const { z }   = require('zod');
const { query, transaction } = require('../lib/db');
const { PAYMENT_SELECT, formatPayment, recalculateMemberExpiry } = require('../lib/payments');

const router = express.Router();

function csvCell(value) {
  if (value === null || value === undefined) return '';
  const str = String(value);
  if (str.includes(',') || str.includes('"') || str.includes('\n')) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}
function toCSV(rows) {
  return rows.map(row => row.map(csvCell).join(',')).join('\n');
}

/** Loads a tenant row or sends a 404. Returns the row, or null (response already sent). */
async function loadTenantOr404(req, res) {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) { res.status(400).json({ error: 'Invalid tenant ID.' }); return null; }
  const { rows } = await query(`SELECT * FROM tenants WHERE id = $1`, [id]);
  if (!rows[0]) { res.status(404).json({ error: 'Tenant not found.' }); return null; }
  return rows[0];
}

// ── GET /api/admin/analytics ────────────────────────────────────────────────
router.get('/analytics', async (req, res) => {
  const months = Math.max(1, Math.min(24, parseInt(req.query.months, 10) || 6));
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

    // Tenant signups per month, last N months
    const { rows: signupGrowth } = await query(
      `SELECT to_char(date_trunc('month', "createdAt"), 'YYYY-MM') AS month,
              COUNT(*) AS count
       FROM tenants
       WHERE "createdAt" >= NOW() - ($1 || ' months')::INTERVAL
       GROUP BY 1 ORDER BY 1`,
      [months],
    );

    // Revenue per month, last N months (platform-wide, across all tenants)
    const { rows: revenueGrowth } = await query(
      `SELECT to_char(date_trunc('month', "paymentDate"), 'YYYY-MM') AS month,
              COALESCE(SUM(amount), 0) AS total
       FROM payments
       WHERE "paymentDate" >= NOW() - ($1 || ' months')::INTERVAL
       GROUP BY 1 ORDER BY 1`,
      [months],
    );

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
      `SELECT t.id, t.name, t.address, t.phone, t."isSuspended", t."createdAt", t."currencySymbol",
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

// ── POST /api/admin/tenants ─────────────────────────────────────────────────
// Create a brand-new gym + owner account (admin-driven onboarding).
const createTenantSchema = z.object({
  gymName:        z.string().min(2, 'Gym name must be at least 2 characters').max(200),
  address:        z.string().max(500).optional().or(z.literal('')).transform(v => v || null),
  phone:          z.string().max(20).optional().or(z.literal('')).transform(v => v || null),
  currencySymbol: z.string().max(5).optional().default('$'),
  ownerName:      z.string().min(1, 'Owner name is required').max(150),
  ownerEmail:     z.string().email('Valid owner email required'),
  ownerPassword:  z.string().min(6, 'Password must be at least 6 characters'),
});

router.post('/tenants', async (req, res) => {
  const result = createTenantSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const { gymName, address, phone, currencySymbol, ownerName, ownerEmail, ownerPassword } = result.data;
  const passwordHash = await bcrypt.hash(ownerPassword, 12);

  try {
    const tenant = await transaction(async (client) => {
      const { rows: [owner] } = await client.query(
        `INSERT INTO users (email, name, "passwordHash") VALUES ($1,$2,$3) RETURNING id, email, name`,
        [ownerEmail, ownerName, passwordHash],
      );

      const { rows: [t] } = await client.query(
        `INSERT INTO tenants (name, address, phone, "currencySymbol", "updatedAt")
         VALUES ($1,$2,$3,$4,NOW()) RETURNING *`,
        [gymName, address, phone, currencySymbol],
      );

      await client.query(
        `INSERT INTO plans ("tenantId", name, "durationDays", fee) VALUES
          ($1,'Monthly',30,1000), ($1,'Quarterly',90,2700), ($1,'Annual',365,9000)`,
        [t.id],
      );
      await client.query(
        `INSERT INTO payment_methods ("tenantId", name) VALUES
          ($1,'Cash'), ($1,'UPI'), ($1,'Card'), ($1,'Bank Transfer')`,
        [t.id],
      );
      await client.query(
        `INSERT INTO expense_categories ("tenantId", name) VALUES
          ($1,'Rent'), ($1,'Utilities'), ($1,'Equipment'),
          ($1,'Staff Salaries'), ($1,'Marketing'), ($1,'Maintenance'), ($1,'Other')`,
        [t.id],
      );

      await client.query(`UPDATE users SET "tenantId" = $1 WHERE id = $2`, [t.id, owner.id]);

      return { ...t, ownerName: owner.name, ownerEmail: owner.email };
    });
    return res.status(201).json(tenant);
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'An account with that owner email already exists.' });
    console.error('[admin/tenants/POST]', err);
    return res.status(500).json({ error: 'Failed to create gym.' });
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

// ── DELETE /api/admin/tenants/:id ───────────────────────────────────────────
// Permanently deletes a gym and everything tied to it (members, staff, plans,
// payments, attendance, expenses, progress data) via ON DELETE CASCADE, then
// removes the owner's login account too.
router.delete('/tenants/:id', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });

  try {
    const deleted = await transaction(async (client) => {
      const { rows: ownerRows } = await client.query(`SELECT id FROM users WHERE "tenantId" = $1`, [id]);
      const { rows: tRows } = await client.query(`DELETE FROM tenants WHERE id = $1 RETURNING id, name`, [id]);
      if (!tRows[0]) return null;
      for (const owner of ownerRows) {
        await client.query(`DELETE FROM users WHERE id = $1`, [owner.id]);
      }
      return tRows[0];
    });
    if (!deleted) return res.status(404).json({ error: 'Tenant not found.' });
    return res.json({ success: true, deleted });
  } catch (err) {
    console.error('[admin/tenants/:id/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete gym.' });
  }
});

// ── PUT /api/admin/tenants/:id/owner ────────────────────────────────────────
const ownerUpdateSchema = z.object({
  name:  z.string().min(1, 'Name is required').max(150),
  email: z.string().email('Valid email required'),
});

router.put('/tenants/:id/owner', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });

  const result = ownerUpdateSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  try {
    const { rows } = await query(
      `UPDATE users SET name = $1, email = $2 WHERE "tenantId" = $3 RETURNING id, name, email`,
      [result.data.name, result.data.email, id],
    );
    if (!rows[0]) return res.status(404).json({ error: 'No owner account found for this gym.' });
    return res.json(rows[0]);
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'That email is already in use by another account.' });
    console.error('[admin/tenants/:id/owner/PUT]', err);
    return res.status(500).json({ error: 'Failed to update owner.' });
  }
});

// ── POST /api/admin/tenants/:id/owner/reset-password ───────────────────────
const resetPasswordSchema = z.object({
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

router.post('/tenants/:id/owner/reset-password', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });

  const result = resetPasswordSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  try {
    const passwordHash = await bcrypt.hash(result.data.password, 12);
    const { rows } = await query(
      `UPDATE users SET "passwordHash" = $1 WHERE "tenantId" = $2 RETURNING id, name, email`,
      [passwordHash, id],
    );
    if (!rows[0]) return res.status(404).json({ error: 'No owner account found for this gym.' });
    return res.json({ success: true, owner: rows[0] });
  } catch (err) {
    console.error('[admin/tenants/:id/owner/reset-password/POST]', err);
    return res.status(500).json({ error: 'Failed to reset password.' });
  }
});

// ── POST /api/admin/tenants/:id/impersonate ─────────────────────────────────
// Issues a short-lived OWNER-role token so Super Admin can act as this gym's
// owner for support/debugging. Deliberately much shorter-lived than a normal
// 30-day owner token.
router.post('/tenants/:id/impersonate', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });

  try {
    const { rows } = await query(`SELECT id, name, email FROM users WHERE "tenantId" = $1 LIMIT 1`, [id]);
    const owner = rows[0];
    if (!owner) return res.status(404).json({ error: 'No owner account found for this gym.' });

    const token = jwt.sign(
      { userId: owner.id, tenantId: id, email: owner.email, role: 'OWNER', impersonatedBy: 'SUPER_ADMIN' },
      process.env.JWT_SECRET,
      { expiresIn: '2h' },
    );
    return res.json({ token, expiresIn: '2h', owner });
  } catch (err) {
    console.error('[admin/tenants/:id/impersonate/POST]', err);
    return res.status(500).json({ error: 'Failed to generate impersonation token.' });
  }
});

// ── PUT /api/admin/tenants/:id/settings ─────────────────────────────────────
const adminSettingsSchema = z.object({
  name:                 z.string().min(2).max(200).optional(),
  address:              z.string().max(500).optional().or(z.literal('')).transform(v => v || null),
  phone:                z.string().max(20).optional().or(z.literal('')).transform(v => v || null),
  contactPerson:        z.string().max(150).optional().or(z.literal('')).transform(v => v || null),
  currencySymbol:       z.string().max(5).optional(),
  checkInWindowMinutes: z.number().int().min(1).max(60).optional(),
  taxRate:              z.number().min(0).max(100).optional(),
});

router.put('/tenants/:id/settings', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });

  const result = adminSettingsSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  const sets = [`"updatedAt" = NOW()`];
  const vals = [];
  let p = 1;

  if (data.name                 !== undefined) { sets.push(`name = $${p++}`);                   vals.push(data.name); }
  if (data.address              !== undefined) { sets.push(`address = $${p++}`);                vals.push(data.address); }
  if (data.phone                !== undefined) { sets.push(`phone = $${p++}`);                  vals.push(data.phone); }
  if (data.contactPerson        !== undefined) { sets.push(`"contactPerson" = $${p++}`);        vals.push(data.contactPerson); }
  if (data.currencySymbol       !== undefined) { sets.push(`"currencySymbol" = $${p++}`);       vals.push(data.currencySymbol); }
  if (data.checkInWindowMinutes !== undefined) { sets.push(`"checkInWindowMinutes" = $${p++}`); vals.push(data.checkInWindowMinutes); }
  if (data.taxRate              !== undefined) { sets.push(`"taxRate" = $${p++}`);              vals.push(data.taxRate); }

  vals.push(id);

  try {
    const { rows } = await query(
      `UPDATE tenants SET ${sets.join(', ')} WHERE id = $${p}
       RETURNING id, name, address, phone, "contactPerson", "currencySymbol", "checkInWindowMinutes", "taxRate", "updatedAt"`,
      vals,
    );
    if (!rows[0]) return res.status(404).json({ error: 'Tenant not found.' });
    return res.json(rows[0]);
  } catch (err) {
    console.error('[admin/tenants/:id/settings/PUT]', err);
    return res.status(500).json({ error: 'Failed to update settings.' });
  }
});

// ── Plans — manage on the owner's behalf ────────────────────────────────────
const planSchema = z.object({
  name:         z.string().min(1, 'Name is required').max(100),
  durationDays: z.number().int().positive('Duration must be a positive integer'),
  fee:          z.number().positive('Fee must be positive'),
  isActive:     z.boolean().optional(),
});
const planUpdateSchema = planSchema.partial();

router.get('/tenants/:id/plans', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });
  try {
    const { rows } = await query(
      `SELECT p.*,
              (SELECT COUNT(*) FROM members WHERE "planId" = p.id AND "tenantId" = $1)::int AS "_count_members"
       FROM plans p WHERE p."tenantId" = $1 ORDER BY p."durationDays" ASC`,
      [id],
    );
    return res.json(rows.map(r => {
      const { _count_members, ...plan } = r;
      return { ...plan, _count: { members: _count_members } };
    }));
  } catch (err) {
    console.error('[admin/tenants/:id/plans/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch plans.' });
  }
});

router.post('/tenants/:id/plans', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });
  const result = planSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const { name, durationDays, fee, isActive = true } = result.data;
  try {
    const { rows } = await query(
      `INSERT INTO plans ("tenantId", name, "durationDays", fee, "isActive") VALUES ($1,$2,$3,$4,$5) RETURNING *`,
      [id, name, durationDays, fee, isActive],
    );
    return res.status(201).json(rows[0]);
  } catch (err) {
    console.error('[admin/tenants/:id/plans/POST]', err);
    return res.status(500).json({ error: 'Failed to create plan.' });
  }
});

router.put('/tenants/:id/plans/:planId', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  const planId = parseInt(req.params.planId, 10);
  if (isNaN(id) || isNaN(planId)) return res.status(400).json({ error: 'Invalid ID.' });

  const result = planUpdateSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  const sets = []; const vals = []; let p = 1;
  if (data.name         !== undefined) { sets.push(`name = $${p++}`);           vals.push(data.name); }
  if (data.durationDays !== undefined) { sets.push(`"durationDays" = $${p++}`); vals.push(data.durationDays); }
  if (data.fee          !== undefined) { sets.push(`fee = $${p++}`);            vals.push(data.fee); }
  if (data.isActive     !== undefined) { sets.push(`"isActive" = $${p++}`);     vals.push(data.isActive); }
  if (sets.length === 0) return res.status(400).json({ error: 'No fields to update.' });
  vals.push(planId, id);

  try {
    const { rowCount } = await query(
      `UPDATE plans SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`, vals,
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Plan not found.' });
    const { rows } = await query(`SELECT * FROM plans WHERE id = $1 AND "tenantId" = $2`, [planId, id]);
    return res.json(rows[0]);
  } catch (err) {
    console.error('[admin/tenants/:id/plans/:planId/PUT]', err);
    return res.status(500).json({ error: 'Failed to update plan.' });
  }
});

router.delete('/tenants/:id/plans/:planId', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  const planId = parseInt(req.params.planId, 10);
  if (isNaN(id) || isNaN(planId)) return res.status(400).json({ error: 'Invalid ID.' });
  try {
    const { rowCount } = await query(`DELETE FROM plans WHERE id = $1 AND "tenantId" = $2`, [planId, id]);
    if (rowCount === 0) return res.status(404).json({ error: 'Plan not found.' });
    return res.status(204).send();
  } catch (err) {
    if (err.code === '23503') return res.status(409).json({ error: 'Cannot delete a plan that has active members.' });
    console.error('[admin/tenants/:id/plans/:planId/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete plan.' });
  }
});

// ── Staff — manage on the owner's behalf ────────────────────────────────────
const staffSchema = z.object({
  fullName: z.string().min(1, 'Full name is required').max(150),
  email:    z.string().email('Invalid email'),
  phone:    z.string().max(20).optional().or(z.literal('')).transform(v => v || null),
  role:     z.enum(['OWNER', 'RECEPTIONIST', 'TRAINER']).default('RECEPTIONIST'),
  notes:    z.string().max(500).optional(),
  password: z.string().min(6).optional(),
});
const staffUpdateSchema = staffSchema.partial();

router.get('/tenants/:id/staff', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });
  try {
    const { rows } = await query(
      `SELECT id,"tenantId","fullName",email,phone,role,notes,"createdAt" FROM staff WHERE "tenantId" = $1 ORDER BY "createdAt" DESC`,
      [id],
    );
    return res.json(rows);
  } catch (err) {
    console.error('[admin/tenants/:id/staff/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch staff.' });
  }
});

router.post('/tenants/:id/staff', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });
  const result = staffSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const { fullName, email, phone, role, notes, password } = result.data;
  const passwordHash = password ? await bcrypt.hash(password, 12) : null;

  try {
    const { rows } = await query(
      `INSERT INTO staff ("tenantId","fullName",email,phone,role,notes,"passwordHash")
       VALUES ($1,$2,$3,$4,$5,$6,$7)
       RETURNING id,"tenantId","fullName",email,phone,role,notes,"createdAt"`,
      [id, fullName, email, phone ?? null, role, notes ?? null, passwordHash],
    );
    return res.status(201).json(rows[0]);
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'A staff member with that email already exists.' });
    console.error('[admin/tenants/:id/staff/POST]', err);
    return res.status(500).json({ error: 'Failed to add staff member.' });
  }
});

router.put('/tenants/:id/staff/:staffId', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  const staffId = parseInt(req.params.staffId, 10);
  if (isNaN(id) || isNaN(staffId)) return res.status(400).json({ error: 'Invalid ID.' });

  const result = staffUpdateSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  const sets = []; const vals = []; let p = 1;
  if (data.fullName !== undefined) { sets.push(`"fullName" = $${p++}`); vals.push(data.fullName); }
  if (data.email    !== undefined) { sets.push(`email = $${p++}`);      vals.push(data.email); }
  if (data.phone    !== undefined) { sets.push(`phone = $${p++}`);      vals.push(data.phone); }
  if (data.role     !== undefined) { sets.push(`role = $${p++}`);       vals.push(data.role); }
  if (data.notes    !== undefined) { sets.push(`notes = $${p++}`);      vals.push(data.notes); }
  if (data.password !== undefined) {
    const hash = await bcrypt.hash(data.password, 12);
    sets.push(`"passwordHash" = $${p++}`); vals.push(hash);
  }
  if (sets.length === 0) return res.status(400).json({ error: 'No fields to update.' });
  vals.push(staffId, id);

  try {
    const { rowCount } = await query(
      `UPDATE staff SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`, vals,
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Staff member not found.' });
    const { rows } = await query(
      `SELECT id,"tenantId","fullName",email,phone,role,notes,"createdAt" FROM staff WHERE id = $1 AND "tenantId" = $2`,
      [staffId, id],
    );
    return res.json(rows[0]);
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'Email already in use.' });
    console.error('[admin/tenants/:id/staff/:staffId/PUT]', err);
    return res.status(500).json({ error: 'Failed to update staff member.' });
  }
});

router.delete('/tenants/:id/staff/:staffId', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  const staffId = parseInt(req.params.staffId, 10);
  if (isNaN(id) || isNaN(staffId)) return res.status(400).json({ error: 'Invalid ID.' });
  try {
    const { rowCount } = await query(`DELETE FROM staff WHERE id = $1 AND "tenantId" = $2`, [staffId, id]);
    if (rowCount === 0) return res.status(404).json({ error: 'Staff member not found.' });
    return res.status(204).send();
  } catch (err) {
    console.error('[admin/tenants/:id/staff/:staffId/DELETE]', err);
    return res.status(500).json({ error: 'Failed to remove staff member.' });
  }
});

// ── Members — manage on the owner's behalf ──────────────────────────────────
const adminMemberSchema = z.object({
  fullName:  z.string().min(1, 'Full name is required').max(150),
  phone:     z.string().min(7, 'Phone is required').max(20),
  email:     z.string().email('Invalid email').optional().or(z.literal('')).transform(v => v || null),
  location:  z.string().max(200).optional().or(z.literal('')).transform(v => v || null),
  planId:    z.number().int().positive('Plan ID is required'),
  status:    z.enum(['Active', 'Inactive']).default('Active'),
  joinDate:  z.string().datetime().optional(),
  trainerId: z.number().int().positive().optional().nullable(),
  password:  z.string().min(6, 'Password must be at least 6 characters').optional().or(z.literal('')).transform(v => v || null),
  heightCm:  z.number().positive().max(300).optional().nullable(),
  dateOfBirth:           z.string().datetime().optional().nullable(),
  gender:                z.string().max(20).optional().or(z.literal('')).transform(v => v || null),
  bloodGroup:            z.string().max(10).optional().or(z.literal('')).transform(v => v || null),
  emergencyContactName:  z.string().max(150).optional().or(z.literal('')).transform(v => v || null),
  emergencyContactPhone: z.string().max(20).optional().or(z.literal('')).transform(v => v || null),
  referralSource:        z.string().max(50).optional().or(z.literal('')).transform(v => v || null),
  healthNotes:           z.string().max(1000).optional().or(z.literal('')).transform(v => v || null),
});
const adminMemberUpdateSchema = adminMemberSchema.partial();

router.get('/tenants/:id/members', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });
  try {
    const { rows } = await query(
      `SELECT m.*,
              p.id AS "planId_", p.name AS "planName_", p."durationDays" AS "planDays_", p.fee AS "planFee_",
              s."fullName" AS "trainerName_"
       FROM members m
       JOIN plans p ON p.id = m."planId"
       LEFT JOIN staff s ON s.id = m."trainerId"
       WHERE m."tenantId" = $1
       ORDER BY m."createdAt" DESC`,
      [id],
    );
    const members = rows.map(row => {
      const plan = { id: row.planId_, name: row.planName_, durationDays: row.planDays_, fee: parseFloat(row.planFee_) };
      const { planId_: _1, planName_: _2, planDays_: _3, planFee_: _4, trainerName_: trainerName, ...member } = row;
      return { ...member, plan, trainerName: trainerName ?? null };
    });
    return res.json(members);
  } catch (err) {
    console.error('[admin/tenants/:id/members/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch members.' });
  }
});

router.post('/tenants/:id/members', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });

  const result = adminMemberSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const {
    fullName, phone, email, location, planId, status, joinDate, trainerId, password, heightCm,
    dateOfBirth, gender, bloodGroup, emergencyContactName, emergencyContactPhone, referralSource, healthNotes,
  } = result.data;

  try {
    const passwordHash = password ? await bcrypt.hash(password, 12) : null;
    const { rows } = await query(
      `INSERT INTO members
         ("tenantId","fullName",phone,email,location,"planId",status,"joinDate","trainerId","passwordHash","heightCm",
          "dateOfBirth",gender,"bloodGroup","emergencyContactName","emergencyContactPhone","referralSource","healthNotes")
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18) RETURNING *`,
      [id, fullName, phone, email ?? null, location ?? null, planId, status,
       joinDate ? new Date(joinDate) : new Date(), trainerId ?? null, passwordHash, heightCm ?? null,
       dateOfBirth ? new Date(dateOfBirth) : null, gender ?? null, bloodGroup ?? null,
       emergencyContactName ?? null, emergencyContactPhone ?? null, referralSource ?? null, healthNotes ?? null],
    );
    const { rows: planRows } = await query(`SELECT * FROM plans WHERE id = $1`, [planId]);
    return res.status(201).json({ ...rows[0], plan: planRows[0] ?? null });
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'A member with that phone number already exists.' });
    if (err.code === '23503') return res.status(400).json({ error: 'Invalid plan ID.' });
    console.error('[admin/tenants/:id/members/POST]', err);
    return res.status(500).json({ error: 'Failed to create member.' });
  }
});

router.put('/tenants/:id/members/:memberId', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  const memberId = parseInt(req.params.memberId, 10);
  if (isNaN(id) || isNaN(memberId)) return res.status(400).json({ error: 'Invalid ID.' });

  const result = adminMemberUpdateSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  const sets = []; const vals = []; let p = 1;

  if (data.fullName  !== undefined) { sets.push(`"fullName" = $${p++}`);   vals.push(data.fullName); }
  if (data.phone     !== undefined) { sets.push(`phone = $${p++}`);        vals.push(data.phone); }
  if (data.email     !== undefined) { sets.push(`email = $${p++}`);        vals.push(data.email); }
  if (data.location  !== undefined) { sets.push(`location = $${p++}`);     vals.push(data.location); }
  if (data.planId    !== undefined) { sets.push(`"planId" = $${p++}`);     vals.push(data.planId); }
  if (data.status    !== undefined) { sets.push(`status = $${p++}`);       vals.push(data.status); }
  if (data.joinDate  !== undefined) { sets.push(`"joinDate" = $${p++}`);   vals.push(new Date(data.joinDate)); }
  if (data.trainerId !== undefined) { sets.push(`"trainerId" = $${p++}`);  vals.push(data.trainerId ?? null); }
  if (data.heightCm  !== undefined) { sets.push(`"heightCm" = $${p++}`);   vals.push(data.heightCm ?? null); }
  if (data.dateOfBirth           !== undefined) { sets.push(`"dateOfBirth" = $${p++}`);           vals.push(data.dateOfBirth ? new Date(data.dateOfBirth) : null); }
  if (data.gender                !== undefined) { sets.push(`gender = $${p++}`);                  vals.push(data.gender); }
  if (data.bloodGroup            !== undefined) { sets.push(`"bloodGroup" = $${p++}`);            vals.push(data.bloodGroup); }
  if (data.emergencyContactName  !== undefined) { sets.push(`"emergencyContactName" = $${p++}`);  vals.push(data.emergencyContactName); }
  if (data.emergencyContactPhone !== undefined) { sets.push(`"emergencyContactPhone" = $${p++}`); vals.push(data.emergencyContactPhone); }
  if (data.referralSource        !== undefined) { sets.push(`"referralSource" = $${p++}`);        vals.push(data.referralSource); }
  if (data.healthNotes           !== undefined) { sets.push(`"healthNotes" = $${p++}`);           vals.push(data.healthNotes); }
  if (data.password) {
    const hash = await bcrypt.hash(data.password, 12);
    sets.push(`"passwordHash" = $${p++}`); vals.push(hash);
  }

  if (sets.length === 0) return res.status(400).json({ error: 'No fields to update.' });
  vals.push(memberId, id);

  try {
    const { rowCount } = await query(
      `UPDATE members SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`, vals,
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Member not found.' });

    const { rows } = await query(
      `SELECT m.*, p.id AS "planId_", p.name AS "planName_", p."durationDays" AS "planDays_", p.fee AS "planFee_"
       FROM members m JOIN plans p ON p.id = m."planId"
       WHERE m.id = $1 AND m."tenantId" = $2`,
      [memberId, id],
    );
    const row  = rows[0];
    const plan = { id: row.planId_, name: row.planName_, durationDays: row.planDays_, fee: parseFloat(row.planFee_) };
    const { planId_: _1, planName_: _2, planDays_: _3, planFee_: _4, ...member } = row;
    return res.json({ ...member, plan });
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'Phone number already in use.' });
    console.error('[admin/tenants/:id/members/:memberId/PUT]', err);
    return res.status(500).json({ error: 'Failed to update member.' });
  }
});

router.delete('/tenants/:id/members/:memberId', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  const memberId = parseInt(req.params.memberId, 10);
  if (isNaN(id) || isNaN(memberId)) return res.status(400).json({ error: 'Invalid ID.' });
  try {
    const { rowCount } = await query(`DELETE FROM members WHERE id = $1 AND "tenantId" = $2`, [memberId, id]);
    if (rowCount === 0) return res.status(404).json({ error: 'Member not found.' });
    return res.status(204).send();
  } catch (err) {
    console.error('[admin/tenants/:id/members/:memberId/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete member.' });
  }
});

// ── Lightweight lookups for the Payments form ───────────────────────────────
router.get('/tenants/:id/members-lite', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });
  try {
    const { rows } = await query(
      `SELECT id, "fullName", phone, "planId" FROM members WHERE "tenantId" = $1 ORDER BY "fullName" ASC`,
      [id],
    );
    return res.json(rows);
  } catch (err) {
    console.error('[admin/tenants/:id/members-lite/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch members.' });
  }
});

router.get('/tenants/:id/payment-methods', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });
  try {
    const { rows } = await query(
      `SELECT * FROM payment_methods WHERE "tenantId" = $1 AND "isActive" = true ORDER BY name`,
      [id],
    );
    return res.json(rows);
  } catch (err) {
    console.error('[admin/tenants/:id/payment-methods/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch payment methods.' });
  }
});

// ── Payments — manage on the owner's behalf ─────────────────────────────────
router.get('/tenants/:id/payments', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });
  try {
    const { rows } = await query(
      `${PAYMENT_SELECT} WHERE p."tenantId" = $1 ORDER BY p."paymentDate" DESC, p.id DESC LIMIT 200`,
      [id],
    );
    return res.json(rows.map(formatPayment));
  } catch (err) {
    console.error('[admin/tenants/:id/payments/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch payments.' });
  }
});

const adminPaymentSchema = z.object({
  memberId:    z.number().int().positive(),
  amount:      z.number().positive('Amount must be greater than 0'),
  methodId:    z.number().int().positive(),
  notes:       z.string().max(500).nullable().optional(),
  paymentDate: z.string().datetime().optional(),
});

router.post('/tenants/:id/payments', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });

  const parsed = adminPaymentSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { memberId, amount, methodId, notes, paymentDate } = parsed.data;

  try {
    const newId = await transaction(async (client) => {
      const { rows: mRows } = await client.query(
        `SELECT m."membershipExpiry", p.id AS "planId", p."durationDays", p.fee AS "planFee"
         FROM   members m JOIN plans p ON p.id = m."planId"
         WHERE  m.id = $1 AND m."tenantId" = $2`,
        [memberId, id],
      );
      if (!mRows[0]) { const e = new Error('Member not found.'); e.status = 404; throw e; }

      const { rows: pmRows } = await client.query(
        `SELECT id FROM payment_methods WHERE id = $1 AND "tenantId" = $2`,
        [methodId, id],
      );
      if (!pmRows[0]) { const e = new Error('Payment method not found.'); e.status = 404; throw e; }

      const { planId, durationDays: planDurationDays, planFee } = mRows[0];
      const payDate = paymentDate ? new Date(paymentDate) : new Date();

      const { rows: pRows } = await client.query(
        `INSERT INTO payments
           ("tenantId","memberId","methodId","planId","planDurationDays","planFee",amount,notes,"paymentDate")
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)
         RETURNING id`,
        [id, memberId, methodId, planId, planDurationDays, planFee, amount, notes ?? null, payDate],
      );

      await recalculateMemberExpiry(client, memberId, id);
      await client.query(`UPDATE members SET status = 'Active' WHERE id = $1 AND "tenantId" = $2`, [memberId, id]);

      return pRows[0].id;
    });

    const { rows } = await query(`${PAYMENT_SELECT} WHERE p.id = $1`, [newId]);
    return res.status(201).json(formatPayment(rows[0]));
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    console.error('[admin/tenants/:id/payments/POST]', err);
    return res.status(500).json({ error: 'Failed to record payment.' });
  }
});

const adminPaymentUpdateSchema = z.object({
  amount:   z.number().positive().optional(),
  methodId: z.number().int().positive().optional(),
  notes:    z.string().max(500).nullable().optional(),
});

router.put('/tenants/:id/payments/:paymentId', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  const paymentId = parseInt(req.params.paymentId, 10);
  if (isNaN(id) || isNaN(paymentId)) return res.status(400).json({ error: 'Invalid ID.' });

  const parsed = adminPaymentUpdateSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  try {
    await transaction(async (client) => {
      const { rows } = await client.query(
        `SELECT * FROM payments WHERE id = $1 AND "tenantId" = $2`, [paymentId, id],
      );
      if (!rows[0]) { const e = new Error('Payment not found.'); e.status = 404; throw e; }

      const { amount, methodId, notes } = parsed.data;

      if (methodId !== undefined) {
        const { rows: pmRows } = await client.query(
          `SELECT id FROM payment_methods WHERE id = $1 AND "tenantId" = $2`, [methodId, id],
        );
        if (!pmRows[0]) { const e = new Error('Payment method not found.'); e.status = 404; throw e; }
      }

      const sets = []; const vals = []; let p = 1;
      if (amount   !== undefined) { sets.push(`amount = $${p++}`);      vals.push(amount); }
      if (methodId !== undefined) { sets.push(`"methodId" = $${p++}`);  vals.push(methodId); }
      if ('notes' in parsed.data) { sets.push(`notes = $${p++}`);       vals.push(notes ?? null); }

      if (sets.length > 0) {
        vals.push(paymentId, id);
        await client.query(
          `UPDATE payments SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`, vals,
        );
      }

      await recalculateMemberExpiry(client, rows[0].memberId, id);
    });

    const { rows } = await query(`${PAYMENT_SELECT} WHERE p.id = $1`, [paymentId]);
    return res.json(formatPayment(rows[0]));
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    console.error('[admin/tenants/:id/payments/:paymentId/PUT]', err);
    return res.status(500).json({ error: 'Failed to update payment.' });
  }
});

// ── CSV Exports — support/reporting ─────────────────────────────────────────
router.get('/tenants/:id/export/members', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });
  try {
    const { rows } = await query(
      `SELECT m.id, m."fullName", m.phone, m.email, m.location,
              p.name AS plan_name, p.fee AS plan_fee, m.status, m."joinDate"
       FROM members m JOIN plans p ON p.id = m."planId"
       WHERE m."tenantId" = $1
       ORDER BY m."createdAt" DESC`,
      [id],
    );
    const header = ['ID', 'Full Name', 'Phone', 'Email', 'Location', 'Plan', 'Plan Fee', 'Status', 'Join Date'];
    const csvRows = rows.map(m => [
      m.id, m.fullName, m.phone, m.email, m.location,
      m.plan_name, m.plan_fee, m.status,
      m.joinDate ? new Date(m.joinDate).toISOString().split('T')[0] : '',
    ]);
    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename="members.csv"');
    return res.send(toCSV([header, ...csvRows]));
  } catch (err) {
    console.error('[admin/tenants/:id/export/members]', err);
    return res.status(500).json({ error: 'Failed to export members.' });
  }
});

router.get('/tenants/:id/export/payments', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid tenant ID.' });
  try {
    const { rows } = await query(
      `SELECT p.id, p."paymentDate", m."fullName", m.phone, p.amount, pm.name AS method_name, p.notes
       FROM payments p
       JOIN members m ON m.id = p."memberId"
       JOIN payment_methods pm ON pm.id = p."methodId"
       WHERE p."tenantId" = $1
       ORDER BY p."paymentDate" DESC`,
      [id],
    );
    const header = ['ID', 'Date', 'Member', 'Phone', 'Amount', 'Method', 'Notes'];
    const csvRows = rows.map(p => [
      p.id,
      p.paymentDate ? new Date(p.paymentDate).toISOString().split('T')[0] : '',
      p.fullName, p.phone, p.amount, p.method_name, p.notes,
    ]);
    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename="payments.csv"');
    return res.send(toCSV([header, ...csvRows]));
  } catch (err) {
    console.error('[admin/tenants/:id/export/payments]', err);
    return res.status(500).json({ error: 'Failed to export payments.' });
  }
});

module.exports = router;
