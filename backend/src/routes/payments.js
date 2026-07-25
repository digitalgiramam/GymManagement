/**
 * Payments routes
 * GET  /api/payments   — list with optional ?memberId=&startDate=&endDate=
 * POST /api/payments   — record a payment
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
  method:      z.enum(['Cash', 'Card', 'Transfer']),
  notes:       z.string().max(500).optional(),
  paymentDate: z.string().datetime().optional(),
});

// ── GET /api/payments ──────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const { memberId, startDate, endDate } = req.query;

  const where = {};

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
      // Include the full end day
      d.setHours(23, 59, 59, 999);
      where.paymentDate.lte = d;
    }
  }

  try {
    const payments = await prisma.payment.findMany({
      where,
      include: { member: { select: { id: true, fullName: true, phone: true } } },
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
  const result = paymentSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  const { memberId, amount, method, notes, paymentDate } = result.data;

  try {
    // Verify member exists
    const member = await prisma.member.findUnique({ where: { id: memberId }, select: { id: true } });
    if (!member) return res.status(404).json({ error: 'Member not found.' });

    const payment = await prisma.payment.create({
      data: {
        memberId,
        amount,
        method,
        notes:       notes ?? null,
        paymentDate: paymentDate ? new Date(paymentDate) : new Date(),
      },
      include: { member: { select: { id: true, fullName: true, phone: true } } },
    });

    return res.status(201).json(payment);
  } catch (err) {
    console.error('[payments/POST]', err);
    return res.status(500).json({ error: 'Failed to record payment.' });
  }
});

module.exports = router;
