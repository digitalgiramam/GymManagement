/**
 * Settings routes (multi-tenant)
 * GET   /api/settings  — get current tenant settings
 * PUT   /api/settings  — update tenant settings
 *
 * Editable fields: name, address, phone, currencySymbol, checkInWindowMinutes, taxRate
 * All queries scoped to req.user.tenantId
 */

const express = require('express');
const { z }   = require('zod');
const { PrismaClient } = require('@prisma/client');

const router = express.Router();
const prisma = new PrismaClient();

const settingsSchema = z.object({
  name:                 z.string().min(2).max(200).optional(),
  address:              z.string().max(500).optional().or(z.literal('')).transform(v => v || null),
  phone:                z.string().max(20).optional().or(z.literal('')).transform(v => v || null),
  currencySymbol:       z.string().max(3).optional(),
  checkInWindowMinutes: z.number().int().min(1).max(60).optional(),
  taxRate:              z.number().min(0).max(100).optional(),
});

// ── GET /api/settings ──────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const tenant = await prisma.tenant.findUnique({
      where:  { id: tenantId },
      select: {
        id: true, name: true, address: true, phone: true,
        currencySymbol: true, checkInWindowMinutes: true, taxRate: true,
        createdAt: true,
      },
    });
    if (!tenant) return res.status(404).json({ error: 'Tenant not found.' });
    return res.json(tenant);
  } catch (err) {
    console.error('[settings/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch settings.' });
  }
});

// ── PUT /api/settings ──────────────────────────────────────────────────────
router.put('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = settingsSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  try {
    const tenant = await prisma.tenant.update({
      where: { id: tenantId },
      data:  result.data,
      select: {
        id: true, name: true, address: true, phone: true,
        currencySymbol: true, checkInWindowMinutes: true, taxRate: true,
        updatedAt: true,
      },
    });
    return res.json(tenant);
  } catch (err) {
    if (err.code === 'P2025') return res.status(404).json({ error: 'Tenant not found.' });
    console.error('[settings/PUT]', err);
    return res.status(500).json({ error: 'Failed to update settings.' });
  }
});

module.exports = router;
