/**
 * Onboarding route
 * POST /api/onboarding/create-gym  — create tenant + seed defaults + link user
 */

const express = require('express');
const jwt     = require('jsonwebtoken');
const { z }   = require('zod');
const { query, transaction } = require('../lib/db');
const { authenticateJWT }    = require('../middleware/auth');

const router = express.Router();

const createGymSchema = z.object({
  gymName: z.string().min(2, 'Gym name must be at least 2 characters'),
  address: z.string().optional(),
  phone: z.string().optional(),
  currencySymbol: z.string().max(3).optional().default('$'),
});

// POST /api/onboarding/create-gym
router.post('/create-gym', authenticateJWT, async (req, res) => {
  const parsed = createGymSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { gymName, address, phone, currencySymbol } = parsed.data;
  const userId = req.user.userId;

  // Prevent creating a second gym
  const { rows: userRows } = await query(`SELECT "tenantId" FROM users WHERE id = $1`, [userId]);
  if (userRows[0]?.tenantId) {
    return res.status(409).json({ error: 'Your account is already linked to a gym.', code: 'ALREADY_ONBOARDED' });
  }

  let tenant;
  try {
    tenant = await transaction(async (client) => {
      // 1. Create tenant
      const { rows: [t] } = await client.query(
        `INSERT INTO tenants (name, address, phone, "currencySymbol", "updatedAt")
         VALUES ($1, $2, $3, $4, NOW()) RETURNING *`,
        [gymName, address ?? null, phone ?? null, currencySymbol],
      );
      const tid = t.id;

      // 2. Seed default Plans
      await client.query(
        `INSERT INTO plans ("tenantId", name, "durationDays", fee) VALUES
          ($1, 'Monthly', 30, 1000), ($1, 'Quarterly', 90, 2700), ($1, 'Annual', 365, 9000)`,
        [tid],
      );

      // 3. Seed default Payment Methods
      await client.query(
        `INSERT INTO payment_methods ("tenantId", name) VALUES
          ($1, 'Cash'), ($1, 'UPI'), ($1, 'Card'), ($1, 'Bank Transfer')`,
        [tid],
      );

      // 4. Seed default Expense Categories
      await client.query(
        `INSERT INTO expense_categories ("tenantId", name) VALUES
          ($1, 'Rent'), ($1, 'Utilities'), ($1, 'Equipment'),
          ($1, 'Staff Salaries'), ($1, 'Marketing'), ($1, 'Maintenance'), ($1, 'Other')`,
        [tid],
      );

      // 5. Link user to tenant
      await client.query(`UPDATE users SET "tenantId" = $1 WHERE id = $2`, [tid, userId]);

      return t;
    });
  } catch (err) {
    console.error('[onboarding/create-gym]', err);
    return res.status(500).json({ error: 'Failed to create gym. Please try again.' });
  }

  const newToken = jwt.sign(
    { userId, tenantId: tenant.id, email: req.user.email, role: 'OWNER' },
    process.env.JWT_SECRET,
    { expiresIn: '30d' },
  );

  return res.status(201).json({
    token: newToken,
    tenant: {
      id: tenant.id, name: tenant.name,
      address: tenant.address, phone: tenant.phone,
      currencySymbol: tenant.currencySymbol,
    },
  });
});

module.exports = router;
