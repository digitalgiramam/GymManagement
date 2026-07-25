/**
 * Auth routes
 * POST /api/auth/login — returns a 24-hour JWT
 */

const express  = require('express');
const bcrypt   = require('bcryptjs');
const jwt      = require('jsonwebtoken');
const { z }    = require('zod');
const { PrismaClient } = require('@prisma/client');

const router = express.Router();
const prisma = new PrismaClient();

const loginSchema = z.object({
  username: z.string().min(1, 'Username is required'),
  password: z.string().min(1, 'Password is required'),
});

router.post('/login', async (req, res) => {
  // ── Validate input ─────────────────────────────────────────────────────────
  const result = loginSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ error: result.error.errors[0].message });
  }

  const { username, password } = result.data;

  try {
    // ── Look up owner ──────────────────────────────────────────────────────
    const owner = await prisma.owner.findUnique({ where: { username } });
    if (!owner) {
      // Use generic message to avoid username enumeration
      return res.status(401).json({ error: 'Invalid credentials.' });
    }

    const passwordMatch = await bcrypt.compare(password, owner.passwordHash);
    if (!passwordMatch) {
      return res.status(401).json({ error: 'Invalid credentials.' });
    }

    // ── Issue token ────────────────────────────────────────────────────────
    const token = jwt.sign(
      { ownerId: owner.id, username: owner.username },
      process.env.JWT_SECRET,
      { expiresIn: '24h' }
    );

    return res.json({ token, username: owner.username });
  } catch (err) {
    console.error('[auth/login]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

module.exports = router;
