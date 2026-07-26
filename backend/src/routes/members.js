/**
 * Members routes (multi-tenant)
 * GET    /api/members          — list with optional ?search=
 * POST   /api/members          — create
 * GET    /api/members/:id      — detail + attendance + payments
 * PUT    /api/members/:id      — update
 * DELETE /api/members/:id      — delete
 *
 * All queries scoped to req.user.tenantId
 */

const express = require('express');
const { z }   = require('zod');
const prisma = require('../lib/prisma');

const router = express.Router();

// ── Zod schemas ────────────────────────────────────────────────────────────
const memberSchema = z.object({
  fullName: z.string().min(1, 'Full name is required').max(150),
  phone:    z.string().min(7, 'Phone is required').max(20),
  email:    z.string().email('Invalid email').optional().or(z.literal('')).transform(v => v || null),
  location: z.string().max(200).optional().or(z.literal('')).transform(v => v || null),
  planId:   z.number().int().positive('Plan ID is required'),
  status:   z.enum(['Active', 'Inactive']).default('Active'),
  joinDate: z.string().datetime().optional(),
});

const memberUpdateSchema = memberSchema.partial();

// ── Helpers ────────────────────────────────────────────────────────────────
function enrichMember(member) {
  const lastPayment     = member._lastPayment ?? null;
  const lastPaymentDate = lastPayment?.paymentDate ?? null;
  const baseDate        = lastPaymentDate
    ? new Date(lastPaymentDate)
    : new Date(member.joinDate);
  const durationDays    = member.plan?.durationDays ?? 30;

  const expiry = new Date(baseDate);
  expiry.setDate(expiry.getDate() + durationDays);

  const daysUntilExpiry = Math.ceil(
    (expiry.getTime() - Date.now()) / (1000 * 60 * 60 * 24),
  );

  const { _lastPayment, ...rest } = member;
  return { ...rest, lastPaymentDate, membershipExpiry: expiry.toISOString(), daysUntilExpiry };
}

// ── GET /api/members ───────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const search   = req.query.search?.toString().trim() ?? '';

  try {
    const members = await prisma.member.findMany({
      where: {
        tenantId,
        ...(search && {
          OR: [
            { fullName: { contains: search, mode: 'insensitive' } },
            { phone:    { contains: search, mode: 'insensitive' } },
          ],
        }),
      },
      include: {
        plan:     true,
        payments: { orderBy: { paymentDate: 'desc' }, take: 1 },
      },
      orderBy: { createdAt: 'desc' },
    });

    const enriched = members.map(m => {
      const { payments, ...rest } = m;
      return enrichMember({ ...rest, _lastPayment: payments[0] ?? null });
    });

    return res.json(enriched);
  } catch (err) {
    console.error('[members/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch members.' });
  }
});

// ── GET /api/members/:id ───────────────────────────────────────────────────
router.get('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid member ID.' });

  try {
    const member = await prisma.member.findFirst({
      where:   { id, tenantId },
      include: {
        plan:       true,
        attendance: { orderBy: { checkedInAt: 'desc' }, take: 50 },
        payments: {
          orderBy: { paymentDate: 'desc' },
          take: 50,
          include: { method: { select: { id: true, name: true } } },
        },
      },
    });
    if (!member) return res.status(404).json({ error: 'Member not found.' });

    const { payments, ...rest } = member;
    const enriched = enrichMember({ ...rest, _lastPayment: payments[0] ?? null });
    return res.json({ ...enriched, payments });
  } catch (err) {
    console.error('[members/GET/:id]', err);
    return res.status(500).json({ error: 'Failed to fetch member.' });
  }
});

// ── POST /api/members ──────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = memberSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  try {
    const data   = result.data;
    const member = await prisma.member.create({
      data: {
        tenantId,
        fullName: data.fullName,
        phone:    data.phone,
        email:    data.email ?? null,
        location: data.location ?? null,
        planId:   data.planId,
        status:   data.status,
        joinDate: data.joinDate ? new Date(data.joinDate) : new Date(),
      },
      include: { plan: true },
    });
    return res.status(201).json(member);
  } catch (err) {
    if (err.code === 'P2002') {
      return res.status(409).json({ error: 'A member with that phone number already exists.' });
    }
    if (err.code === 'P2003') {
      return res.status(400).json({ error: 'Invalid plan ID.' });
    }
    console.error('[members/POST]', err);
    return res.status(500).json({ error: 'Failed to create member.' });
  }
});

// ── PUT /api/members/:id ───────────────────────────────────────────────────
router.put('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid member ID.' });

  const result = memberUpdateSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  try {
    const data       = result.data;
    const updateData = {
      ...(data.fullName  !== undefined && { fullName: data.fullName }),
      ...(data.phone     !== undefined && { phone:    data.phone }),
      ...(data.email     !== undefined && { email:    data.email }),
      ...(data.location  !== undefined && { location: data.location }),
      ...(data.planId    !== undefined && { planId:   data.planId }),
      ...(data.status    !== undefined && { status:   data.status }),
      ...(data.joinDate  !== undefined && { joinDate: new Date(data.joinDate) }),
    };

    const member = await prisma.member.updateMany({
      where: { id, tenantId },
      data:  updateData,
    });

    if (member.count === 0) return res.status(404).json({ error: 'Member not found.' });

    const updated = await prisma.member.findFirst({ where: { id, tenantId }, include: { plan: true } });
    return res.json(updated);
  } catch (err) {
    if (err.code === 'P2002') {
      return res.status(409).json({ error: 'Phone number already in use.' });
    }
    console.error('[members/PUT]', err);
    return res.status(500).json({ error: 'Failed to update member.' });
  }
});

// ── DELETE /api/members/:id ────────────────────────────────────────────────
router.delete('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid member ID.' });

  try {
    const result = await prisma.member.deleteMany({ where: { id, tenantId } });
    if (result.count === 0) return res.status(404).json({ error: 'Member not found.' });
    return res.status(204).send();
  } catch (err) {
    console.error('[members/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete member.' });
  }
});

module.exports = router;
