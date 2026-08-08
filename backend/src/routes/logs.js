/**
 * Super Admin error logs — persistent record of exceptions (SMTP failures,
 * unhandled route errors, etc.), since Vercel's own function logs are
 * ephemeral and only browsable from the Vercel dashboard.
 *
 * GET /api/admin/logs           — most recent entries as JSON (for the table view)
 * GET /api/admin/logs/download  — same data as a downloadable .txt file
 * DELETE /api/admin/logs        — clear all logged entries
 */

const express = require('express');
const { query } = require('../lib/db');

const router = express.Router();

router.get('/', async (req, res) => {
  const limit = Math.max(1, Math.min(500, parseInt(req.query.limit, 10) || 200));
  try {
    const { rows } = await query(
      `SELECT id, source, message, stack, "createdAt" FROM error_logs ORDER BY "createdAt" DESC LIMIT $1`,
      [limit],
    );
    return res.json(rows);
  } catch (err) {
    console.error('[logs/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch logs.' });
  }
});

router.get('/download', async (req, res) => {
  const limit = Math.max(1, Math.min(2000, parseInt(req.query.limit, 10) || 500));
  try {
    const { rows } = await query(
      `SELECT id, source, message, stack, "createdAt" FROM error_logs ORDER BY "createdAt" DESC LIMIT $1`,
      [limit],
    );

    const lines = rows.map(r => {
      const ts = new Date(r.createdAt).toISOString();
      const header = `[${ts}] (#${r.id}) [${r.source}] ${r.message}`;
      return r.stack ? `${header}\n${r.stack}` : header;
    });

    const body = rows.length
      ? lines.join('\n\n' + '-'.repeat(80) + '\n\n')
      : 'No errors logged.';

    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="error-log-${new Date().toISOString().slice(0, 10)}.txt"`);
    return res.send(body);
  } catch (err) {
    console.error('[logs/download]', err);
    return res.status(500).json({ error: 'Failed to export logs.' });
  }
});

router.delete('/', async (_req, res) => {
  try {
    await query(`DELETE FROM error_logs`);
    return res.json({ success: true });
  } catch (err) {
    console.error('[logs/DELETE]', err);
    return res.status(500).json({ error: 'Failed to clear logs.' });
  }
});

module.exports = router;
