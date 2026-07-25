/**
 * Members routes
 * GET    /api/members          — list with optional ?search=
 * POST   /api/members          — create
 * GET    /api/members/:id      — detail + attendance + payments
 * PUT    /api/members/:id      — update
 * DELETE /api/members/:id      — delete
 */

const express = require('express');
const { z }   = require('zod');
const { PrismaClient } = require('@prisma/client');

const router = express.Router();
const prisma = new PrismaClient();

// ── Zod schemas ────────────────────────────────────────────────────────────
const memberSchema = z.object({
  fullName: z.string().min(1, 'Full name is required').max(150),
  phone:    z.string().min(7,  'Phone is required').max(20),
  email:    z.string().email('Invalid email').optional().or(z.literal('')).transform(v => v || null),
  planId:   z.number().int().positive('Plan ID is required'),
  status:   z.enum(['Active', 'Inactive']).default('Active'),
  joinDate: z.string().datetime().optional(),
});

const memberUpdateSchema = memberSchema.partial();

// ── GET /api/members ───────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const search = req.query.search?.toString().trim() ?? '';

  try {
    const members = await prisma.member.findMany({
      where: search
        ? {
            OR: [
              { fullName: { contains: search, mode: 'insensitive' } },
              { phone:    { contains: search, mode: 'insensitive' } },
            ],
          }
        : undefined,
      include: { plan: true },
      orderBy: { createdAt: 'desc' },
    });
    return res.json(members);
  } catch (err) {
    console.error('[members/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch members.' });
  }
});

// ── GET /api/members/:id ───────────────────────────────────────────────────
router.get('/:id', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid member ID.' });

  try {
    const member = await prisma.member.findUnique({
      where:   { id },
      include: {
        plan:       true,
        attendance: { orderBy: { checkedInAt: 'desc' }, take: 50 },
        payments:   { orderBy: { paymentDate: 'desc' }, take: 50 },
      },
    });
    if (!member) return res.status(404).json({ error: 'Member not found.' });
    return res.json(member);
  } catch (err) {
    console.error('[members/GET/:id]', err);
    return res.status(500).json({ error: 'Failed to fetch member.' });
  }
});

// ── POST /api/members ──────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const result = memberSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  try {
    const data = result.data;
    const member = await prisma.member.create({
      data: {
        fullName: data.fullName,
        phone:    data.phone,
        email:    data.email ?? null,
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
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid member ID.' });

  const result = memberUpdateSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  try {
    const data = result.data;
    const updateData = {
      ...(data.fullName  !== undefined && { fullName: data.fullName }),
      ...(data.phone     !== undefined && { phone:    data.phone }),
      ...(data.email     !== undefined && { email:    data.email }),
      ...(data.planId    !== undefined && { planId:   data.planId }),
      ...(data.status    !== undefined && { status:   data.status }),
      ...(data.joinDate  !== undefined && { joinDate: new Date(data.joinDate) }),
    };

    const member = await prisma.member.update({
      where:   { id },
      data:    updateData,
      include: { plan: true },
    });
    return res.json(member);
  } catch (err) {
    if (err.code === 'P2025') return res.status(404).json({ error: 'Member not found.' });
    if (err.code === 'P2002') {
      return res.status(409).json({ error: 'Phone number already in use.' });
    }
    console.error('[members/PUT]', err);
    return res.status(500).json({ error: 'Failed to update member.' });
  }
});

// ── DELETE /api/members/:id ────────────────────────────────────────────────
router.delete('/:id', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid member ID.' });

  try {
    await prisma.member.delete({ where: { id } });
    return res.status(204).send();
  } catch (err) {
    if (err.code === 'P2025') return res.status(404).json({ error: 'Member not found.' });
    console.error('[members/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete member.' });
  }
});

module.exports = router;
