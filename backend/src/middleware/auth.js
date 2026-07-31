/**
 * JWT Authentication middleware (multi-tenant SaaS)
 *
 * authenticateJWT  — verifies Bearer token, attaches req.user = { userId, tenantId, email }
 * requireTenant    — ensures the user has completed onboarding (tenantId is not null)
 */

const jwt = require('jsonwebtoken');

function authenticateJWT(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Missing or invalid Authorization header.' });
  }

  const token = authHeader.substring(7);
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = decoded; // { userId, tenantId, email }
    next();
  } catch (err) {
    if (err.name === 'TokenExpiredError') {
      return res.status(401).json({ error: 'Token expired. Please sign in again.', code: 'TOKEN_EXPIRED' });
    }
    return res.status(401).json({ error: 'Invalid token.', code: 'INVALID_TOKEN' });
  }
}

function requireTenant(req, res, next) {
  if (!req.user?.tenantId) {
    return res.status(403).json({
      error: 'No gym associated with your account. Please complete gym setup.',
      code: 'NO_TENANT',
    });
  }
  next();
}

module.exports = { authenticateJWT, requireTenant };
