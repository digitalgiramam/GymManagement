/**
 * Pure pg database client — no Prisma, no native binary, no WASM.
 * Works in any Node.js environment including Vercel Lambda.
 *
 * Neon free tier autosuspends after 5 min of inactivity; resuming can
 * take 5-15 s. We handle this with:
 *   - connectionTimeoutMillis: 20 s  (give Neon time to wake up)
 *   - Retry logic: up to 3 attempts with 1 s back-off before giving up
 */
const { Pool } = require('pg');

let _pool = null;

function getPool() {
  if (!_pool) {
    _pool = new Pool({
      connectionString: process.env.DATABASE_URL,
      max: 2,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 20000,   // Neon can take 10-15 s to resume
      ssl: { rejectUnauthorized: false },
    });
    _pool.on('error', (err) => console.error('[pg] pool error', err));
  }
  return _pool;
}

/** Sleep helper for retry back-off. */
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Run a single parameterised query with automatic retry on connection failure.
 * Retries handle Neon cold-start "connection refused / timeout" errors.
 */
async function query(text, params, retries = 3) {
  let lastErr;
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      return await getPool().query(text, params);
    } catch (err) {
      lastErr = err;
      const isConnErr = err.code === 'ECONNREFUSED'
        || err.code === 'ENOTFOUND'
        || err.message?.includes('connect')
        || err.message?.includes('timeout');

      if (isConnErr && attempt < retries) {
        console.warn(`[pg] query attempt ${attempt} failed (${err.message}), retrying…`);
        await sleep(1000 * attempt);   // 1 s, then 2 s
      } else {
        throw err;
      }
    }
  }
  throw lastErr;
}

/**
 * Run work inside a BEGIN/COMMIT transaction.
 * fn receives a pg PoolClient; throw to trigger ROLLBACK.
 */
async function transaction(fn) {
  let client;
  let lastErr;
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      client = await getPool().connect();
      break;
    } catch (err) {
      lastErr = err;
      const isConnErr = err.code === 'ECONNREFUSED'
        || err.code === 'ENOTFOUND'
        || err.message?.includes('connect')
        || err.message?.includes('timeout');
      if (isConnErr && attempt < 3) {
        console.warn(`[pg] connect attempt ${attempt} failed, retrying…`);
        await sleep(1000 * attempt);
      } else {
        throw err;
      }
    }
  }
  if (!client) throw lastErr;

  try {
    await client.query('BEGIN');
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

module.exports = { query, transaction };
