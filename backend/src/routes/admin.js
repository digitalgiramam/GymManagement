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

const router = express.Router();

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

module.exports = router;
