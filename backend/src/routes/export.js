/**
 * Export route (multi-tenant)
 * GET /api/export/members
 * GET /api/export/payments
 * GET /api/export/expenses
 */

const express   = require('express');
const { query } = require('../lib/db');

const router = express.Router();

function csvCell(value) {
  if (value === null || value === undefined) return '';
  const str = String(value);
  if (str.includes(',') || str.includes('"') || str.includes('\n')) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

function toCSV(rows) {
  return rows.map(row => row.map(csvCell).join(',')).join('\n');
}

// ── GET /api/export/members ────────────────────────────────────────────────
router.get('/members', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const { rows } = await query(
      `SELECT m.id, m."fullName", m.phone, m.email, m.location,
              p.name AS plan_name, p.fee AS plan_fee, m.status, m."joinDate"
       FROM members m JOIN plans p ON p.id = m."planId"
       WHERE m."tenantId" = $1
       ORDER BY m."createdAt" DESC`,
      [tenantId],
    );

    const header = ['ID', 'Full Name', 'Phone', 'Email', 'Location', 'Plan', 'Plan Fee', 'Status', 'Join Date'];
    const csvRows = rows.map(m => [
      m.id, m.fullName, m.phone, m.email, m.location,
      m.plan_name, m.plan_fee, m.status,
      m.joinDate ? new Date(m.joinDate).toISOString().split('T')[0] : '',
    ]);

    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename="members.csv"');
    return res.send(toCSV([header, ...csvRows]));
  } catch (err) {
    console.error('[export/members]', err);
    return res.status(500).json({ error: 'Failed to export members.' });
  }
});

// ── GET /api/export/payments ───────────────────────────────────────────────
router.get('/payments', async (req, res) => {
  const tenantId          = req.user.tenantId;
  const { startDate, endDate } = req.query;

  const conditions = [`p."tenantId" = $1`];
  const vals       = [tenantId];
  let   idx        = 2;

  if (startDate) {
    const d = new Date(startDate);
    if (!isNaN(d.getTime())) { conditions.push(`p."paymentDate" >= $${idx++}`); vals.push(d); }
  }
  if (endDate) {
    const d = new Date(endDate);
    if (!isNaN(d.getTime())) {
      d.setHours(23, 59, 59, 999);
      conditions.push(`p."paymentDate" <= $${idx++}`); vals.push(d);
    }
  }

  try {
    const { rows } = await query(
      `SELECT p.id, p."paymentDate", m."fullName", m.phone, p.amount, pm.name AS method_name, p.notes
       FROM payments p
       JOIN members m ON m.id = p."memberId"
       JOIN payment_methods pm ON pm.id = p."methodId"
       WHERE ${conditions.join(' AND ')}
       ORDER BY p."paymentDate" DESC`,
      vals,
    );

    const header = ['ID', 'Date', 'Member', 'Phone', 'Amount', 'Method', 'Notes'];
    const csvRows = rows.map(p => [
      p.id,
      p.paymentDate ? new Date(p.paymentDate).toISOString().split('T')[0] : '',
      p.fullName, p.phone, p.amount, p.method_name, p.notes,
    ]);

    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename="payments.csv"');
    return res.send(toCSV([header, ...csvRows]));
  } catch (err) {
    console.error('[export/payments]', err);
    return res.status(500).json({ error: 'Failed to export payments.' });
  }
});

// ── GET /api/export/expenses ───────────────────────────────────────────────
router.get('/expenses', async (req, res) => {
  const tenantId          = req.user.tenantId;
  const { startDate, endDate } = req.query;

  const conditions = [`e."tenantId" = $1`];
  const vals       = [tenantId];
  let   idx        = 2;

  if (startDate) {
    const d = new Date(startDate);
    if (!isNaN(d.getTime())) { conditions.push(`e."expenseDate" >= $${idx++}`); vals.push(d); }
  }
  if (endDate) {
    const d = new Date(endDate);
    if (!isNaN(d.getTime())) {
      d.setHours(23, 59, 59, 999);
      conditions.push(`e."expenseDate" <= $${idx++}`); vals.push(d);
    }
  }

  try {
    const { rows } = await query(
      `SELECT e.id, e."expenseDate", e.title, ec.name AS cat_name, e.amount, e.notes
       FROM expenses e
       JOIN expense_categories ec ON ec.id = e."categoryId"
       WHERE ${conditions.join(' AND ')}
       ORDER BY e."expenseDate" DESC`,
      vals,
    );

    const header = ['ID', 'Date', 'Title', 'Category', 'Amount', 'Notes'];
    const csvRows = rows.map(e => [
      e.id,
      e.expenseDate ? new Date(e.expenseDate).toISOString().split('T')[0] : '',
      e.title, e.cat_name, e.amount, e.notes,
    ]);

    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename="expenses.csv"');
    return res.send(toCSV([header, ...csvRows]));
  } catch (err) {
    console.error('[export/expenses]', err);
    return res.status(500).json({ error: 'Failed to export expenses.' });
  }
});

module.exports = router;
