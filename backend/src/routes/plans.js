/**
 * Plans routes (multi-tenant)
 * GET    /api/plans
 * POST   /api/plans
 * PUT    /api/plans/:id
 * DELETE /api/plans/:id
 *
 * All queries scoped to req.user.tenantId
 */

const express = require('express');
const { z }   = require('zod');
const { PrismaClient } = require('@prisma/client');

const router = express.Router();
const prisma = new PrismaClient();

const planSchema = z.object({
  name:         z.string().min(1, 'Name is required').max(100),
  durationDays: z.number().int().positive('Duration must be a positive integer'),
  fee:          z.number().positive('Fee must be positive'),
  isActive:     z.boolean().optional(),
});

const planUpdateSchema = planSchema.partial();

// ── GET /api/plans ─────────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const plans = await prisma.plan.findMany({
      where:   { tenantId },
      orderBy: { durationDays: 'asc' },
      include: { _count: { select: { members: true } } },
    });
    return res.json(plans);
  } catch (err) {
    console.error('[plans/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch plans.' });
  }
});

// ── POST /api/plans ────────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = planSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  try {
    const plan = await prisma.plan.create({ data: { ...result.data, tenantId } });
    return res.status(201).json(plan);
  } catch (err) {
    console.error('[plans/POST]', err);
    return res.status(500).json({ error: 'Failed to create plan.' });
  }
});

// ── PUT /api/plans/:id ─────────────────────────────────────────────────────
router.put('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid plan ID.' });

  const result = planUpdateSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  try {
    const update = await prisma.plan.updateMany({ where: { id, tenantId }, data: result.data });
    if (update.count === 0) return res.status(404).json({ error: 'Plan not found.' });
    const plan = await prisma.plan.findFirst({ where: { id, tenantId } });
    return res.json(plan);
  } catch (err) {
    console.error('[plans/PUT]', err);
    return res.status(500).json({ error: 'Failed to update plan.' });
  }
});

// ── DELETE /api/plans/:id ──────────────────────────────────────────────────
router.delete('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid plan ID.' });

  try {
    const result = await prisma.plan.deleteMany({ where: { id, tenantId } });
    if (result.count === 0) return res.status(404).json({ error: 'Plan not found.' });
    return res.status(204).send();
  } catch (err) {
    if (err.code === 'P2003') {
      return res.status(409).json({ error: 'Cannot delete a plan that has active members.' });
    }
    console.error('[plans/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete plan.' });
  }
});

module.exports = router;
