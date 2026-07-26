/**
 * Expenses routes (multi-tenant)
 * GET    /api/expenses                — list with optional ?categoryId=&startDate=&endDate=
 * POST   /api/expenses                — record an expense
 * PUT    /api/expenses/:id            — update an expense
 * DELETE /api/expenses/:id            — delete an expense
 * GET    /api/expenses/categories     — list expense categories
 * POST   /api/expenses/categories     — create a category
 * PUT    /api/expenses/categories/:id — update a category
 *
 * All queries scoped to req.user.tenantId
 */

const express = require('express');
const { z }   = require('zod');
const { PrismaClient } = require('@prisma/client');

const router = express.Router();
const prisma = new PrismaClient();

// ── Zod schemas ────────────────────────────────────────────────────────────
const expenseSchema = z.object({
  title:       z.string().min(1, 'Title is required').max(200),
  categoryId:  z.number().int().positive('categoryId is required'),
  amount:      z.number().positive('Amount must be positive'),
  expenseDate: z.string().datetime().optional(),
  notes:       z.string().max(500).optional(),
});

const categorySchema = z.object({
  name: z.string().min(1).max(100),
});

// ── GET /api/expenses/categories ──────────────────────────────────────────
router.get('/categories', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const cats = await prisma.expenseCategory.findMany({
      where:   { tenantId },
      orderBy: { name: 'asc' },
    });
    return res.json(cats);
  } catch (err) {
    console.error('[expenses/categories/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch categories.' });
  }
});

// ── POST /api/expenses/categories ─────────────────────────────────────────
router.post('/categories', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = categorySchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }
  try {
    const cat = await prisma.expenseCategory.create({
      data: { ...result.data, tenantId },
    });
    return res.status(201).json(cat);
  } catch (err) {
    console.error('[expenses/categories/POST]', err);
    return res.status(500).json({ error: 'Failed to create category.' });
  }
});

// ── PUT /api/expenses/categories/:id ──────────────────────────────────────
router.put('/categories/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid category ID.' });

  const result = categorySchema.partial().safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }
  try {
    const update = await prisma.expenseCategory.updateMany({
      where: { id, tenantId },
      data:  result.data,
    });
    if (update.count === 0) return res.status(404).json({ error: 'Category not found.' });
    const cat = await prisma.expenseCategory.findFirst({ where: { id, tenantId } });
    return res.json(cat);
  } catch (err) {
    console.error('[expenses/categories/PUT]', err);
    return res.status(500).json({ error: 'Failed to update category.' });
  }
});

// ── GET /api/expenses ──────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId                 = req.user.tenantId;
  const { categoryId, startDate, endDate } = req.query;

  const where = { tenantId };

  if (categoryId) {
    const cid = parseInt(categoryId, 10);
    if (isNaN(cid)) return res.status(400).json({ error: 'Invalid categoryId.' });
    where.categoryId = cid;
  }

  if (startDate || endDate) {
    where.expenseDate = {};
    if (startDate) {
      const d = new Date(startDate);
      if (isNaN(d.getTime())) return res.status(400).json({ error: 'Invalid startDate.' });
      where.expenseDate.gte = d;
    }
    if (endDate) {
      const d = new Date(endDate);
      if (isNaN(d.getTime())) return res.status(400).json({ error: 'Invalid endDate.' });
      d.setHours(23, 59, 59, 999);
      where.expenseDate.lte = d;
    }
  }

  try {
    const expenses = await prisma.expense.findMany({
      where,
      include: { category: { select: { id: true, name: true } } },
      orderBy: { expenseDate: 'desc' },
    });
    return res.json(expenses);
  } catch (err) {
    console.error('[expenses/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch expenses.' });
  }
});

// ── POST /api/expenses ─────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = expenseSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  const { title, categoryId, amount, expenseDate, notes } = result.data;

  try {
    // Verify category belongs to this tenant
    const cat = await prisma.expenseCategory.findFirst({
      where: { id: categoryId, tenantId },
      select: { id: true },
    });
    if (!cat) return res.status(400).json({ error: 'Invalid expense category.' });

    const expense = await prisma.expense.create({
      data: {
        tenantId,
        title,
        categoryId,
        amount,
        notes:       notes ?? null,
        expenseDate: expenseDate ? new Date(expenseDate) : new Date(),
      },
      include: { category: { select: { id: true, name: true } } },
    });
    return res.status(201).json(expense);
  } catch (err) {
    console.error('[expenses/POST]', err);
    return res.status(500).json({ error: 'Failed to record expense.' });
  }
});

// ── PUT /api/expenses/:id ──────────────────────────────────────────────────
router.put('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid expense ID.' });

  const result = expenseSchema.partial().safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  try {
    const data = result.data;
    const updateData = {
      ...(data.title       !== undefined && { title:       data.title }),
      ...(data.categoryId  !== undefined && { categoryId:  data.categoryId }),
      ...(data.amount      !== undefined && { amount:      data.amount }),
      ...(data.notes       !== undefined && { notes:       data.notes }),
      ...(data.expenseDate !== undefined && { expenseDate: new Date(data.expenseDate) }),
    };

    const update = await prisma.expense.updateMany({ where: { id, tenantId }, data: updateData });
    if (update.count === 0) return res.status(404).json({ error: 'Expense not found.' });

    const expense = await prisma.expense.findFirst({
      where:   { id, tenantId },
      include: { category: { select: { id: true, name: true } } },
    });
    return res.json(expense);
  } catch (err) {
    console.error('[expenses/PUT]', err);
    return res.status(500).json({ error: 'Failed to update expense.' });
  }
});

// ── DELETE /api/expenses/:id ───────────────────────────────────────────────
router.delete('/:id', async (req, res) => {
  const tenantId = req.user.tenantId;
  const id       = parseInt(req.params.id, 10);
  if (isNaN(id)) return res.status(400).json({ error: 'Invalid expense ID.' });

  try {
    const result = await prisma.expense.deleteMany({ where: { id, tenantId } });
    if (result.count === 0) return res.status(404).json({ error: 'Expense not found.' });
    return res.status(204).send();
  } catch (err) {
    console.error('[expenses/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete expense.' });
  }
});

module.exports = router;
