/**
 * Attendance routes (multi-tenant)
 * POST /api/attendance  — record a check-in
 * GET  /api/attendance  — today's check-ins
 */

const express   = require('express');
const { z }     = require('zod');
const { query } = require('../lib/db');

const router = express.Router();

const checkInSchema = z.object({
  memberId: z.number().int().positive('memberId is required'),
});

// ── POST /api/attendance ───────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = checkInSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const { memberId } = result.data;

  try {
    // Get window setting
    const { rows: tRows } = await query(
      `SELECT "checkInWindowMinutes" FROM tenants WHERE id = $1`, [tenantId],
    );
    const windowMinutes = tRows[0]?.checkInWindowMinutes ?? 5;
    const windowMs = windowMinutes * 60 * 1000;

    // Verify member
    const { rows: mRows } = await query(
      `SELECT id, "fullName", status FROM members WHERE id = $1 AND "tenantId" = $2`, [memberId, tenantId],
    );
    if (!mRows[0]) return res.status(404).json({ error: 'Member not found.' });
    const member = mRows[0];

    // Check duplicate
    const cutoff = new Date(Date.now() - windowMs);
    const { rows: recentRows } = await query(
      `SELECT id FROM attendance WHERE "memberId" = $1 AND "tenantId" = $2 AND "checkedInAt" >= $3 LIMIT 1`,
      [memberId, tenantId, cutoff],
    );
    if (recentRows[0]) {
      return res.status(409).json({
        error: `${member.fullName} was already checked in within the last ${windowMinutes} minutes.`,
      });
    }

    const { rows } = await query(
      `INSERT INTO attendance ("memberId","tenantId") VALUES ($1,$2) RETURNING *`,
      [memberId, tenantId],
    );
    const record = rows[0];
    return res.status(201).json({
      ...record,
      member: { id: member.id, fullName: member.fullName },
    });
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
  const endOfDay   = new Date(startOfDay.getTime() + 86400000);

  try {
    const { rows } = await query(
      `SELECT a.*,
              m.id AS "memberId_", m."fullName" AS "memberName_", m.phone AS "memberPhone_",
              p.name AS "planName_"
       FROM attendance a
       JOIN members m ON m.id = a."memberId"
       JOIN plans p ON p.id = m."planId"
       WHERE a."tenantId" = $1 AND a."checkedInAt" >= $2 AND a."checkedInAt" < $3
       ORDER BY a."checkedInAt" DESC`,
      [tenantId, startOfDay, endOfDay],
    );

    const records = rows.map(r => {
      const { memberId_: mid, memberName_: mname, memberPhone_: mphone, planName_: pname, ...att } = r;
      return { ...att, member: { id: mid, fullName: mname, phone: mphone, plan: { name: pname } } };
    });

    return res.json(records);
  } catch (err) {
    console.error('[attendance/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch attendance.' });
  }
});

module.exports = router;
