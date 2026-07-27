/**
 * Settings routes (multi-tenant)
 * GET /api/settings
 * PUT /api/settings
 */

const express   = require('express');
const { z }     = require('zod');
const { query } = require('../lib/db');

const router = express.Router();

const settingsSchema = z.object({
  name:                 z.string().min(2).max(200).optional(),
  address:              z.string().max(500).optional().or(z.literal('')).transform(v => v || null),
  phone:                z.string().max(20).optional().or(z.literal('')).transform(v => v || null),
  contactPerson:        z.string().max(150).optional().or(z.literal('')).transform(v => v || null),
  currencySymbol:       z.string().max(5).optional(),
  checkInWindowMinutes: z.number().int().min(1).max(60).optional(),
  taxRate:              z.number().min(0).max(100).optional(),
  logoBase64:           z.string().optional().or(z.literal('')).transform(v => v || null),
});

// ── GET /api/settings ──────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const { rows } = await query(
      `SELECT id, name, address, phone, "contactPerson", "currencySymbol",
              "checkInWindowMinutes", "taxRate", "logoBase64", "createdAt"
       FROM tenants WHERE id = $1`,
      [tenantId],
    );
    if (!rows[0]) return res.status(404).json({ error: 'Tenant not found.' });
    return res.json(rows[0]);
  } catch (err) {
    console.error('[settings/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch settings.' });
  }
});

// ── PUT /api/settings ──────────────────────────────────────────────────────
router.put('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = settingsSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  const sets = [`"updatedAt" = NOW()`];
  const vals = [];
  let p = 1;

  if (data.name                 !== undefined) { sets.push(`name = $${p++}`);                       vals.push(data.name); }
  if (data.address              !== undefined) { sets.push(`address = $${p++}`);                    vals.push(data.address); }
  if (data.phone                !== undefined) { sets.push(`phone = $${p++}`);                      vals.push(data.phone); }
  if (data.contactPerson        !== undefined) { sets.push(`"contactPerson" = $${p++}`);            vals.push(data.contactPerson); }
  if (data.currencySymbol       !== undefined) { sets.push(`"currencySymbol" = $${p++}`);           vals.push(data.currencySymbol); }
  if (data.checkInWindowMinutes !== undefined) { sets.push(`"checkInWindowMinutes" = $${p++}`);     vals.push(data.checkInWindowMinutes); }
  if (data.taxRate              !== undefined) { sets.push(`"taxRate" = $${p++}`);                  vals.push(data.taxRate); }
  if (data.logoBase64           !== undefined) { sets.push(`"logoBase64" = $${p++}`);               vals.push(data.logoBase64); }

  vals.push(tenantId);

  try {
    const { rows } = await query(
      `UPDATE tenants SET ${sets.join(', ')} WHERE id = $${p}
       RETURNING id, name, address, phone, "contactPerson", "currencySymbol",
                 "checkInWindowMinutes", "taxRate", "logoBase64", "updatedAt"`,
      vals,
    );
    if (!rows[0]) return res.status(404).json({ error: 'Tenant not found.' });
    return res.json(rows[0]);
  } catch (err) {
    console.error('[settings/PUT]', err);
    return res.status(500).json({ error: 'Failed to update settings.' });
  }
});

module.exports = router;
