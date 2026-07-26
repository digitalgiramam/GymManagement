/**
 * Onboarding route — called once per new gym owner.
 *
 * POST /api/onboarding/create-gym
 *   Headers: Authorization: Bearer <token>  (tenantId may be null)
 *   Body: { gymName: string, address?: string, phone?: string, currencySymbol?: string }
 *   Returns: { token: string, tenant: { id, name, ... } }
 *
 * Side effects:
 *   1. Creates a Tenant row
 *   2. Links the authenticated User to the Tenant (sets user.tenantId)
 *   3. Seeds default Plans, PaymentMethods, and ExpenseCategories for the tenant
 *   4. Issues a new JWT that includes the tenantId
 */

const express = require('express');
const jwt = require('jsonwebtoken');
const { z } = require('zod');
const prisma = require('../lib/prisma');
const { authenticateJWT } = require('../middleware/auth');

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
  if (!parsed.success) {
    return res.status(400).json({ error: parsed.error.errors[0].message });
  }

  const { gymName, address, phone, currencySymbol } = parsed.data;
  const userId = req.user.userId;

  // Prevent creating a second gym
  const existingUser = await prisma.user.findUnique({ where: { id: userId } });
  if (existingUser?.tenantId) {
    return res.status(409).json({
      error: 'Your account is already linked to a gym.',
      code: 'ALREADY_ONBOARDED',
    });
  }

  // Transaction: create tenant + seed defaults + link user
  let tenant;
  try {
    tenant = await prisma.$transaction(async (tx) => {
      // 1. Create tenant
      const newTenant = await tx.tenant.create({
        data: {
          name: gymName,
          address: address ?? null,
          phone: phone ?? null,
          currencySymbol,
        },
      });

      const tid = newTenant.id;

      // 2. Seed default Plans
      await tx.plan.createMany({
        data: [
          { tenantId: tid, name: 'Monthly',   durationDays: 30,  fee: 1000 },
          { tenantId: tid, name: 'Quarterly', durationDays: 90,  fee: 2700 },
          { tenantId: tid, name: 'Annual',    durationDays: 365, fee: 9000 },
        ],
      });

      // 3. Seed default Payment Methods
      await tx.paymentMethod.createMany({
        data: [
          { tenantId: tid, name: 'Cash' },
          { tenantId: tid, name: 'UPI' },
          { tenantId: tid, name: 'Card' },
          { tenantId: tid, name: 'Bank Transfer' },
        ],
      });

      // 4. Seed default Expense Categories
      await tx.expenseCategory.createMany({
        data: [
          { tenantId: tid, name: 'Rent' },
          { tenantId: tid, name: 'Utilities' },
          { tenantId: tid, name: 'Equipment' },
          { tenantId: tid, name: 'Staff Salaries' },
          { tenantId: tid, name: 'Marketing' },
          { tenantId: tid, name: 'Maintenance' },
          { tenantId: tid, name: 'Other' },
        ],
      });

      // 5. Link user to tenant
      await tx.user.update({
        where: { id: userId },
        data: { tenantId: tid },
      });

      return newTenant;
    });
  } catch (err) {
    console.error('[onboarding/create-gym] transaction error:', err);
    return res.status(500).json({ error: 'Failed to create gym. Please try again.' });
  }

  // Issue new JWT with tenantId populated
  const newToken = jwt.sign(
    { userId, tenantId: tenant.id, email: req.user.email },
    process.env.JWT_SECRET,
    { expiresIn: '30d' },
  );

  return res.status(201).json({
    token: newToken,
    tenant: {
      id: tenant.id,
      name: tenant.name,
      address: tenant.address,
      phone: tenant.phone,
      currencySymbol: tenant.currencySymbol,
    },
  });
});

module.exports = router;
