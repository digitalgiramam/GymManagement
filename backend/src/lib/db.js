/**
 * Pure pg database client — no Prisma, no native binary, no WASM.
 * Works in any Node.js environment including Vercel Lambda.
 */
const { Pool } = require('pg');

let _pool = null;

function getPool() {
  if (!_pool) {
    _pool = new Pool({
      connectionString: process.env.DATABASE_URL,
      max: 2,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 10000,
      ssl: { rejectUnauthorized: false },
    });
    _pool.on('error', (err) => console.error('[pg] pool error', err));
  }
  return _pool;
}

/**
 * Run a single parameterised query.
 * Returns pg QueryResult ({ rows, rowCount }).
 */
async function query(text, params) {
  return getPool().query(text, params);
}

/**
 * Run work inside a BEGIN/COMMIT transaction.
 * fn receives a pg PoolClient; throw to trigger ROLLBACK.
 */
async function transaction(fn) {
  const client = await getPool().connect();
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
