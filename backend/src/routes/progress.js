/**
 * Progress entry routes (multi-tenant, staff-side — records on behalf of a member)
 * Mounted at /api/members/:memberId/progress
 *
 * GET    /            — list a member's progress history (weight/BMI/measurements)
 * POST   /             — record a new entry for this member
 * DELETE /:entryId     — remove an entry
 */

const express = require('express');
const { z }   = require('zod');
const lib     = require('../lib/progress');

const router = express.Router({ mergeParams: true });

const entrySchema = z.object({
  entryDate: z.string().datetime().optional(),
  weightKg:  z.number().positive().max(500).optional(),
  chestCm:   z.number().positive().max(300).optional(),
  waistCm:   z.number().positive().max(300).optional(),
  hipsCm:    z.number().positive().max(300).optional(),
  armsCm:    z.number().positive().max(300).optional(),
  thighsCm:  z.number().positive().max(300).optional(),
  notes:     z.string().max(500).optional(),
});

// ── GET /api/members/:memberId/progress ────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const memberId = parseInt(req.params.memberId, 10);
  if (isNaN(memberId)) return res.status(400).json({ error: 'Invalid member ID.' });

  try {
    const entries = await lib.listProgress(tenantId, memberId);
    return res.json(entries);
  } catch (err) {
    console.error('[progress/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch progress history.' });
  }
});

// ── POST /api/members/:memberId/progress ───────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const memberId = parseInt(req.params.memberId, 10);
  if (isNaN(memberId)) return res.status(400).json({ error: 'Invalid member ID.' });

  const result = entrySchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  const hasAnyValue = ['weightKg', 'chestCm', 'waistCm', 'hipsCm', 'armsCm', 'thighsCm']
    .some(k => result.data[k] !== undefined);
  if (!hasAnyValue) return res.status(400).json({ error: 'Enter at least one measurement.' });

  try {
    // req.user.staffId is present for staff/trainer tokens — records who logged it.
    const entry = await lib.createProgress(tenantId, memberId, result.data, req.user.staffId ?? null);
    return res.status(201).json(entry);
  } catch (err) {
    if (err.code === '23503') return res.status(400).json({ error: 'Member not found.' });
    console.error('[progress/POST]', err);
    return res.status(500).json({ error: 'Failed to add progress entry.' });
  }
});

// ── DELETE /api/members/:memberId/progress/:entryId ────────────────────────
router.delete('/:entryId', async (req, res) => {
  const tenantId = req.user.tenantId;
  const memberId = parseInt(req.params.memberId, 10);
  const entryId  = parseInt(req.params.entryId, 10);
  if (isNaN(memberId) || isNaN(entryId)) return res.status(400).json({ error: 'Invalid ID.' });

  try {
    const ok = await lib.deleteProgress(tenantId, memberId, entryId);
    if (!ok) return res.status(404).json({ error: 'Progress entry not found.' });
    return res.status(204).send();
  } catch (err) {
    console.error('[progress/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete progress entry.' });
  }
});

module.exports = router;
