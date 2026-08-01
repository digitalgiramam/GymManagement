/**
 * SaaS Gym Management Platform API — Entry Point
 *
 * Works both as a classic Express server (local dev via `npm run dev`)
 * and as a Vercel serverless function (production).
 */

require('dotenv').config();

const express = require('express');
const cors    = require('cors');

// ── Route modules ──────────────────────────────────────────────────────────
const authRoutes       = require('../src/routes/auth');
const onboardingRoutes = require('../src/routes/onboarding');
const memberRoutes     = require('../src/routes/members');
const planRoutes       = require('../src/routes/plans');
const attendanceRoutes = require('../src/routes/attendance');
const paymentRoutes    = require('../src/routes/payments');
const dashboardRoutes  = require('../src/routes/dashboard');
const expenseRoutes    = require('../src/routes/expenses');
const staffRoutes      = require('../src/routes/staff');
const settingsRoutes   = require('../src/routes/settings');
const exportRoutes     = require('../src/routes/export');
const memberPortalRoutes  = require('../src/routes/member-portal');
const staffPortalRoutes   = require('../src/routes/staff-portal');

// ── Middleware ─────────────────────────────────────────────────────────────
const { authenticateJWT, requireTenant } = require('../src/middleware/auth');

// ──────────────────────────────────────────────────────────────────────────
const app = express();

// CORS — allow all origins (Android emulator, local dev, production app)
app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
}));

app.use(express.json({ limit: '1mb' }));

// ── Health check (no auth required) ───────────────────────────────────────
app.get('/api/health', (_req, res) => res.json({
  status: 'ok',
  version: 'auth-v2-phone-login',   // bump this on every deploy to verify what's live
  ts: new Date().toISOString(),
}));

// ── Public routes ──────────────────────────────────────────────────────────
app.use('/api/auth', authRoutes);

// ── Semi-protected: token required, tenant NOT required yet (onboarding) ──
app.use('/api/onboarding', authenticateJWT, onboardingRoutes);

// ── Protected routes (JWT + tenantId required) ─────────────────────────────
app.use('/api/members',    authenticateJWT, requireTenant, memberRoutes);
app.use('/api/plans',      authenticateJWT, requireTenant, planRoutes);
app.use('/api/attendance', authenticateJWT, requireTenant, attendanceRoutes);
app.use('/api/payments',   authenticateJWT, requireTenant, paymentRoutes);
app.use('/api/dashboard',  authenticateJWT, requireTenant, dashboardRoutes);
app.use('/api/expenses',   authenticateJWT, requireTenant, expenseRoutes);
app.use('/api/staff',      authenticateJWT, requireTenant, staffRoutes);
app.use('/api/settings',   authenticateJWT, requireTenant, settingsRoutes);
app.use('/api/export',        authenticateJWT, requireTenant, exportRoutes);

// ── Member portal (role = MEMBER, no requireTenant needed — tenantId in JWT) ─
app.use('/api/member-portal', authenticateJWT, memberPortalRoutes);

// ── Staff portal (TRAINER / RECEPTIONIST — tenantId + staffId in JWT) ─────
app.use('/api/staff-portal', authenticateJWT, staffPortalRoutes);

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
