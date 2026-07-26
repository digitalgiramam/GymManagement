/**
 * Payments routes (multi-tenant)
 * GET  /api/payments             — list with optional ?memberId=&startDate=&endDate=
 * POST /api/payments             — record a payment
 * GET  /api/payments/methods     — list tenant's payment methods
 * POST /api/payments/methods     — create a payment method
 * PUT  /api/payments/methods/:id — update a payment method
 *
 * Payment.method is now a FK (methodId) to the PaymentMethod table.
 * All queries scoped to req.user.tenantId
 */

const express = require('express');
const { z }   = require('zod');
const { PrismaClient } = require('@prisma/client');

const router = express.Router();
const prisma = new PrismaClient();

// ── Zod schemas ────────────────────────────────────────────────────────────
const paymentSchema = z.object({
  memberId:    z.number().int().positive('memberId is required'),
  amount:      z.number().positive('Amount must be positive'),
  methodId:    z.number().int().positive('methodId is required'),
  notes:       z.string().max(500).optional(),
  paymentDate: z.string().datetime().optional(),
});

const paymentMethodSchema = z.object({
  name:     z.string().min(1).max(50),
  isActive: z.boolean().optional(),
});

// ── GET /api/payments/methods ──────────────────────────────────────────────
router.get('/methods', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const methods = await prisma.paymentMethod.findMany({
      where:   { tenantId },
      orderBy: { id: 'asc' },
    });
    return res.json(methods);
  } catch (err) {
    console.error('[payments/methods/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch payment methods.' });
  }
});

// ── POST /api/payments/methods ─────────────────────────────────────────────
router.post('/methods', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = paymentMethodSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }
  try {
    const method = await prisma.paymentMethod.create({
      data: { ...result.data, tenantId },
    });
    return res.status(201).json(method);
  } catch (err) {
    console.error('[payments/methods/POST]', err);
    return res.status(500).json({ error: 'Failed to create payment method.' });
  }
});

// ── PUT /api/payments/methods/:id ──────────────────────────────────────────
router.put('/methods/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid method ID.' });

  const result = paymentMethodSchema.partial().safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }
  try {
    const update = await prisma.paymentMethod.updateMany({
      where: { id, tenantId },
      data:  result.data,
    });
    if (update.count === 0) return res.status(404).json({ error: 'Payment method not found.' });
    const method = await prisma.paymentMethod.findFirst({ where: { id, tenantId } });
    return res.json(method);
  } catch (err) {
    console.error('[payments/methods/PUT]', err);
    return res.status(500).json({ error: 'Failed to update payment method.' });
  }
});

// ── GET /api/payments ──────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId               = req.user.tenantId;
  const { memberId, startDate, endDate } = req.query;

  const where = { tenantId };

  if (memberId) {
    const parsedId = parseInt(memberId, 10);
    if (isNaN(parsedId)) return res.status(400).json({ error: 'Invalid memberId.' });
    where.memberId = parsedId;
  }

  if (startDate || endDate) {
    where.paymentDate = {};
    if (startDate) {
      const d = new Date(startDate);
      if (isNaN(d.getTime())) return res.status(400).json({ error: 'Invalid startDate.' });
      where.paymentDate.gte = d;
    }
    if (endDate) {
      const d = new Date(endDate);
      if (isNaN(d.getTime())) return res.status(400).json({ error: 'Invalid endDate.' });
      d.setHours(23, 59, 59, 999);
      where.paymentDate.lte = d;
    }
  }

  try {
    const payments = await prisma.payment.findMany({
      where,
      include: {
        member: { select: { id: true, fullName: true, phone: true } },
        method: { select: { id: true, name: true } },
      },
      orderBy: { paymentDate: 'desc' },
    });
    return res.json(payments);
  } catch (err) {
    console.error('[payments/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch payments.' });
  }
});

// ── POST /api/payments ─────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = paymentSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  const { memberId, amount, methodId, notes, paymentDate } = result.data;

  try {
    // Verify member belongs to this tenant
    const member = await prisma.member.findFirst({
      where: { id: memberId, tenantId },
      select: { id: true },
    });
    if (!member) return res.status(404).json({ error: 'Member not found.' });

    // Verify payment method belongs to this tenant
    const method = await prisma.paymentMethod.findFirst({
      where: { id: methodId, tenantId },
      select: { id: true },
    });
    if (!method) return res.status(400).json({ error: 'Invalid payment method.' });

    const payment = await prisma.payment.create({
      data: {
        tenantId,
        memberId,
        amount,
        methodId,
        notes:       notes ?? null,
        paymentDate: paymentDate ? new Date(paymentDate) : new Date(),
      },
      include: {
        member: { select: { id: true, fullName: true, phone: true } },
        method: { select: { id: true, name: true } },
      },
    });

    return res.status(201).json(payment);
  } catch (err) {
    console.error('[payments/POST]', err);
    return res.status(500).json({ error: 'Failed to record payment.' });
  }
});

module.exports = router;
