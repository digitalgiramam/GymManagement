/**
 * Export route (multi-tenant)
 * GET /api/export/members   — export all members as CSV
 * GET /api/export/payments  — export payments as CSV (optional ?startDate=&endDate=)
 * GET /api/export/expenses  — export expenses as CSV (optional ?startDate=&endDate=)
 *
 * All queries scoped to req.user.tenantId
 */

const express = require('express');
const { PrismaClient } = require('@prisma/client');

const router  = express.Router();
const prisma  = new PrismaClient();

/** Escape a CSV cell value (quotes any value containing a comma, quote, or newline) */
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

function dateRange(req) {
  const { startDate, endDate } = req.query;
  const filter = {};
  if (startDate) {
    const d = new Date(startDate);
    if (!isNaN(d.getTime())) filter.gte = d;
  }
  if (endDate) {
    const d = new Date(endDate);
    if (!isNaN(d.getTime())) {
      d.setHours(23, 59, 59, 999);
      filter.lte = d;
    }
  }
  return Object.keys(filter).length ? filter : undefined;
}

// ── GET /api/export/members ────────────────────────────────────────────────
router.get('/members', async (req, res) => {
  const tenantId = req.user.tenantId;
  try {
    const members = await prisma.member.findMany({
      where:   { tenantId },
      include: { plan: { select: { name: true, durationDays: true, fee: true } } },
      orderBy: { createdAt: 'desc' },
    });

    const header = ['ID', 'Full Name', 'Phone', 'Email', 'Location', 'Plan', 'Plan Fee', 'Status', 'Join Date'];
    const rows   = members.map(m => [
      m.id, m.fullName, m.phone, m.email, m.location,
      m.plan?.name, m.plan?.fee, m.status,
      m.joinDate?.toISOString().split('T')[0],
    ]);

    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename="members.csv"');
    return res.send(toCSV([header, ...rows]));
  } catch (err) {
    console.error('[export/members]', err);
    return res.status(500).json({ error: 'Failed to export members.' });
  }
});

// ── GET /api/export/payments ───────────────────────────────────────────────
router.get('/payments', async (req, res) => {
  const tenantId = req.user.tenantId;
  const dr       = dateRange(req);

  try {
    const payments = await prisma.payment.findMany({
      where:   { tenantId, ...(dr && { paymentDate: dr }) },
      include: {
        member: { select: { fullName: true, phone: true } },
        method: { select: { name: true } },
      },
      orderBy: { paymentDate: 'desc' },
    });

    const header = ['ID', 'Date', 'Member', 'Phone', 'Amount', 'Method', 'Notes'];
    const rows   = payments.map(p => [
      p.id,
      p.paymentDate?.toISOString().split('T')[0],
      p.member?.fullName, p.member?.phone,
      p.amount,
      p.method?.name,
      p.notes,
    ]);

    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename="payments.csv"');
    return res.send(toCSV([header, ...rows]));
  } catch (err) {
    console.error('[export/payments]', err);
    return res.status(500).json({ error: 'Failed to export payments.' });
  }
});

// ── GET /api/export/expenses ───────────────────────────────────────────────
router.get('/expenses', async (req, res) => {
  const tenantId = req.user.tenantId;
  const dr       = dateRange(req);

  try {
    const expenses = await prisma.expense.findMany({
      where:   { tenantId, ...(dr && { expenseDate: dr }) },
      include: { category: { select: { name: true } } },
      orderBy: { expenseDate: 'desc' },
    });

    const header = ['ID', 'Date', 'Title', 'Category', 'Amount', 'Notes'];
    const rows   = expenses.map(e => [
      e.id,
      e.expenseDate?.toISOString().split('T')[0],
      e.title,
      e.category?.name,
      e.amount,
      e.notes,
    ]);

    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename="expenses.csv"');
    return res.send(toCSV([header, ...rows]));
  } catch (err) {
    console.error('[export/expenses]', err);
    return res.status(500).json({ error: 'Failed to export expenses.' });
  }
});

module.exports = router;
