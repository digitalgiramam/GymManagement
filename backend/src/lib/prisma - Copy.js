/**
 * Prisma singleton using the pg driver adapter.
 *
 * Using @prisma/adapter-pg means Prisma uses a WASM-based query engine
 * instead of a platform-specific native binary — required for Vercel
 * serverless functions where native binaries aren't bundled.
 *
 * The singleton is reused across warm Lambda invocations.
 */

const { Pool }      = require('pg');
const { PrismaPg }  = require('@prisma/adapter-pg');
const { PrismaClient } = require('@prisma/client');

let prisma;

function createClient() {
  const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    max: 1, // keep connection pool small for serverless
  });
  const adapter = new PrismaPg(pool);
  return new PrismaClient({ adapter });
}

// Reuse across warm invocations; create fresh on cold start
if (!prisma) {
  prisma = createClient();
}

module.exports = prisma;
