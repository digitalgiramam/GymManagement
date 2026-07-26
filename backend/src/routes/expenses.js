/**
 * Expenses routes (multi-tenant)
 * GET    /api/expenses/categories      — list categories
 * POST   /api/expenses/categories      — create category
 * PUT    /api/expenses/categories/:id  — update category
 * GET    /api/expenses                 — list expenses
 * POST   /api/expenses                 — record expense
 * PUT    /api/expenses/:id             — update expense
 * DELETE /api/expenses/:id             — delete expense
 */

const express   = require('express');
const { z }     = require('zod');
const { query } = require('../lib/db');

const router = express.Router();

const expenseSchema = z.object({
  title:       z.string().min(1, 'Title is required').max(200),
  categoryId:  z.number().int().positive('categoryId is required'),
  amount:      z.number().positive('Amount must be positive'),
  expenseDate: z.string().datetime().optional(),
  notes:       z.string().max(500).optional(),
});

const categorySchema = z.object({ name: z.string().min(1).max(100) });

// ── GET /api/expenses/categories ──────────────────────────────────────────
router.get('/categories', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const { rows } = await query(
      `SELECT * FROM expense_categories WHERE "tenantId" = $1 ORDER BY name ASC`, [tenantId],
    );
    return res.json(rows);
  } catch (err) {
    console.error('[expenses/categories/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch categories.' });
  }
});

// ── POST /api/expenses/categories ─────────────────────────────────────────
router.post('/categories', async (req, res) => {
  const tenantId = req.user.tenantId;
  const result   = categorySchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });
  try {
    const { rows } = await query(
      `INSERT INTO expense_categories ("tenantId", name) VALUES ($1,$2) RETURNING *`,
      [tenantId, result.data.name],
    );
    return res.status(201).json(rows[0]);
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
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });
  try {
    const { rowCount } = await query(
      `UPDATE expense_categories SET name = $1 WHERE id = $2 AND "tenantId" = $3`,
      [result.data.name, id, tenantId],
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Category not found.' });
    const { rows } = await query(`SELECT * FROM expense_categories WHERE id = $1 AND "tenantId" = $2`, [id, tenantId]);
    return res.json(rows[0]);
  } catch (err) {
    console.error('[expenses/categories/PUT]', err);
    return res.status(500).json({ error: 'Failed to update category.' });
  }
});

// ── GET /api/expenses ──────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId                   = req.user.tenantId;
  const { categoryId, startDate, endDate } = req.query;

  const conditions = [`e."tenantId" = $1`];
  const vals       = [tenantId];
  let   idx        = 2;

  if (categoryId) {
    const cid = parseInt(categoryId, 10);
    if (isNaN(cid)) return res.status(400).json({ error: 'Invalid categoryId.' });
    conditions.push(`e."categoryId" = $${idx++}`); vals.push(cid);
  }
  if (startDate) {
    const d = new Date(startDate);
    if (isNaN(d.getTime())) return res.status(400).json({ error: 'Invalid startDate.' });
    conditions.push(`e."expenseDate" >= $${idx++}`); vals.push(d);
  }
  if (endDate) {
    const d = new Date(endDate);
    if (isNaN(d.getTime())) return res.status(400).json({ error: 'Invalid endDate.' });
    d.setHours(23, 59, 59, 999);
    conditions.push(`e."expenseDate" <= $${idx++}`); vals.push(d);
  }

  try {
    const { rows } = await query(
      `SELECT e.*, ec.id AS "catId_", ec.name AS "catName_"
       FROM expenses e
       JOIN expense_categories ec ON ec.id = e."categoryId"
       WHERE ${conditions.join(' AND ')}
       ORDER BY e."expenseDate" DESC`,
      vals,
    );
    const expenses = rows.map(r => {
      const { catId_: cid, catName_: cname, ...rest } = r;
      return { ...rest, category: { id: cid, name: cname } };
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
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const { title, categoryId, amount, expenseDate, notes } = result.data;

  try {
    const { rows: catRows } = await query(
      `SELECT id FROM expense_categories WHERE id = $1 AND "tenantId" = $2`, [categoryId, tenantId],
    );
    if (!catRows[0]) return res.status(400).json({ error: 'Invalid expense category.' });

    const { rows } = await query(
      `INSERT INTO expenses ("tenantId",title,"categoryId",amount,notes,"expenseDate")
       VALUES ($1,$2,$3,$4,$5,$6) RETURNING *`,
      [tenantId, title, categoryId, amount, notes ?? null, expenseDate ? new Date(expenseDate) : new Date()],
    );
    const expense = rows[0];
    const { rows: catFull } = await query(`SELECT * FROM expense_categories WHERE id = $1`, [categoryId]);
    return res.status(201).json({ ...expense, category: { id: catFull[0].id, name: catFull[0].name } });
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
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const data = result.data;
  const sets = [];
  const vals = [];
  let p = 1;

  if (data.title       !== undefined) { sets.push(`title = $${p++}`);              vals.push(data.title); }
  if (data.categoryId  !== undefined) { sets.push(`"categoryId" = $${p++}`);       vals.push(data.categoryId); }
  if (data.amount      !== undefined) { sets.push(`amount = $${p++}`);             vals.push(data.amount); }
  if (data.notes       !== undefined) { sets.push(`notes = $${p++}`);              vals.push(data.notes); }
  if (data.expenseDate !== undefined) { sets.push(`"expenseDate" = $${p++}`);      vals.push(new Date(data.expenseDate)); }

  if (sets.length === 0) return res.status(400).json({ error: 'No fields to update.' });
  vals.push(id, tenantId);

  try {
    const { rowCount } = await query(
      `UPDATE expenses SET ${sets.join(', ')} WHERE id = $${p} AND "tenantId" = $${p + 1}`, vals,
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Expense not found.' });

    const { rows } = await query(
      `SELECT e.*, ec.id AS "catId_", ec.name AS "catName_"
       FROM expenses e JOIN expense_categories ec ON ec.id = e."categoryId"
       WHERE e.id = $1 AND e."tenantId" = $2`,
      [id, tenantId],
    );
    const r = rows[0];
    const { catId_: cid, catName_: cname, ...rest } = r;
    return res.json({ ...rest, category: { id: cid, name: cname } });
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
    const { rowCount } = await query(
      `DELETE FROM expenses WHERE id = $1 AND "tenantId" = $2`, [id, tenantId],
    );
    if (rowCount === 0) return res.status(404).json({ error: 'Expense not found.' });
    return res.status(204).send();
  } catch (err) {
    console.error('[expenses/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete expense.' });
  }
});

module.exports = router;
