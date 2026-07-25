/**
 * Gym Management API — Entry Point
 *
 * Works both as a classic Express server (local dev via `npm run dev`)
 * and as a Vercel serverless function (production).
 */

require('dotenv').config();

const express    = require('express');
const cors       = require('cors');

// ── Route modules ──────────────────────────────────────────────────────────
const authRoutes       = require('../src/routes/auth');
const memberRoutes     = require('../src/routes/members');
const planRoutes       = require('../src/routes/plans');
const attendanceRoutes = require('../src/routes/attendance');
const paymentRoutes    = require('../src/routes/payments');
const dashboardRoutes  = require('../src/routes/dashboard');

// ── Middleware ─────────────────────────────────────────────────────────────
const { authenticate } = require('../src/middleware/auth');

// ─────────────────────────────────────────────────────────────────────────
const app = express();

// CORS — allow all origins (Android emulator, local dev, production app)
app.use(cors({
  origin:  '*',
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
}));

app.use(express.json({ limit: '1mb' }));

// ── Public routes ──────────────────────────────────────────────────────────
app.use('/api/auth', authRoutes);

// ── Health check (no auth required) ───────────────────────────────────────
app.get('/api/health', (_req, res) => res.json({ status: 'ok', ts: new Date().toISOString() }));

// ── Protected routes (JWT required) ───────────────────────────────────────
app.use('/api/members',    authenticate, memberRoutes);
app.use('/api/plans',      authenticate, planRoutes);
app.use('/api/attendance', authenticate, attendanceRoutes);
app.use('/api/payments',   authenticate, paymentRoutes);
app.use('/api/dashboard',  authenticate, dashboardRoutes);

// ── 404 catch-all ─────────────────────────────────────────────────────────
app.use((_req, res) => res.status(404).json({ error: 'Route not found.' }));

// ── Global error handler ───────────────────────────────────────────────────
app.use((err, _req, res, _next) => {
  console.error('[unhandled]', err);
  res.status(500).json({ error: 'Internal server error.' });
});

// ── Local dev server ───────────────────────────────────────────────────────
if (require.main === module) {
  const PORT = process.env.PORT || 3000;
  app.listen(PORT, () => console.log(`🚀  API listening on http://localhost:${PORT}`));
}

// Vercel serverless export
module.exports = app;
