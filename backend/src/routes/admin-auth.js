/**
 * Super Admin auth — platform-level login, separate from tenant users.
 * POST /api/admin/auth/login
 */

const express = require('express');
const jwt     = require('jsonwebtoken');
const bcrypt  = require('bcryptjs');
const { z }   = require('zod');
const { query } = require('../lib/db');

const router = express.Router();

const loginSchema = z.object({
  email:    z.string().email('Valid email required'),
  password: z.string().min(1, 'Password is required'),
});

router.post('/login', async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.errors[0].message });

  const { email, password } = parsed.data;

  try {
    const { rows } = await query(`SELECT * FROM super_admins WHERE email = $1 LIMIT 1`, [email]);
    const admin = rows[0];
    if (!admin) return res.status(401).json({ error: 'Invalid email or password.' });

    const valid = await bcrypt.compare(password, admin.passwordHash);
    if (!valid) return res.status(401).json({ error: 'Invalid email or password.' });

    const token = jwt.sign(
      { adminId: admin.id, email: admin.email, role: 'SUPER_ADMIN' },
      process.env.JWT_SECRET,
      { expiresIn: '12h' },   // shorter-lived than tenant tokens — platform-level access
    );

    return res.json({
      token,
      admin: { id: admin.id, email: admin.email, fullName: admin.fullName },
    });
  } catch (err) {
    console.error('[admin-auth/login]', err);
    return res.status(500).json({ error: 'Internal server error.' });
  }
});

module.exports = router;
