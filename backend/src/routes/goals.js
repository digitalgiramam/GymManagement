/**
 * Member goal routes (multi-tenant, staff-side)
 * Mounted at /api/members/:memberId/goals
 *
 * GET    /            — list a member's goals
 * POST   /            — set a new goal for this member
 * PUT    /:goalId     — update goal status (ACTIVE | ACHIEVED | ABANDONED)
 * DELETE /:goalId     — remove a goal
 */

const express = require('express');
const { z }   = require('zod');
const lib     = require('../lib/progress');

const router = express.Router({ mergeParams: true });

const goalSchema = z.object({
  goalType:       z.enum(['WEIGHT', 'MEASUREMENT', 'CUSTOM']).default('CUSTOM'),
  description:    z.string().min(1, 'Describe the goal').max(300),
  targetWeightKg: z.number().positive().max(500).optional(),
  targetDate:     z.string().optional(),
});

const statusSchema = z.object({
  status: z.enum(['ACTIVE', 'ACHIEVED', 'ABANDONED']),
});

// ── GET /api/members/:memberId/goals ────────────────────────────────────────
router.get('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const memberId = parseInt(req.params.memberId, 10);
  if (isNaN(memberId)) return res.status(400).json({ error: 'Invalid member ID.' });

  try {
    const goals = await lib.listGoals(tenantId, memberId);
    return res.json(goals);
  } catch (err) {
    console.error('[goals/GET]', err);
    return res.status(500).json({ error: 'Failed to fetch goals.' });
  }
});

// ── POST /api/members/:memberId/goals ───────────────────────────────────────
router.post('/', async (req, res) => {
  const tenantId = req.user.tenantId;
  const memberId = parseInt(req.params.memberId, 10);
  if (isNaN(memberId)) return res.status(400).json({ error: 'Invalid member ID.' });

  const result = goalSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  try {
    const goal = await lib.createGoal(tenantId, memberId, result.data);
    return res.status(201).json(goal);
  } catch (err) {
    if (err.code === '23503') return res.status(400).json({ error: 'Member not found.' });
    console.error('[goals/POST]', err);
    return res.status(500).json({ error: 'Failed to add goal.' });
  }
});

// ── PUT /api/members/:memberId/goals/:goalId ────────────────────────────────
router.put('/:goalId', async (req, res) => {
  const tenantId = req.user.tenantId;
  const memberId = parseInt(req.params.memberId, 10);
  const goalId   = parseInt(req.params.goalId, 10);
  if (isNaN(memberId) || isNaN(goalId)) return res.status(400).json({ error: 'Invalid ID.' });

  const result = statusSchema.safeParse(req.body);
  if (!result.success) return res.status(400).json({ error: result.error.errors[0].message });

  try {
    const goal = await lib.updateGoalStatus(tenantId, memberId, goalId, result.data.status);
    if (!goal) return res.status(404).json({ error: 'Goal not found.' });
    return res.json(goal);
  } catch (err) {
    console.error('[goals/PUT]', err);
    return res.status(500).json({ error: 'Failed to update goal.' });
  }
});

// ── DELETE /api/members/:memberId/goals/:goalId ─────────────────────────────
router.delete('/:goalId', async (req, res) => {
  const tenantId = req.user.tenantId;
  const memberId = parseInt(req.params.memberId, 10);
  const goalId   = parseInt(req.params.goalId, 10);
  if (isNaN(memberId) || isNaN(goalId)) return res.status(400).json({ error: 'Invalid ID.' });

  try {
    const ok = await lib.deleteGoal(tenantId, memberId, goalId);
    if (!ok) return res.status(404).json({ error: 'Goal not found.' });
    return res.status(204).send();
  } catch (err) {
    console.error('[goals/DELETE]', err);
    return res.status(500).json({ error: 'Failed to delete goal.' });
  }
});

module.exports = router;
