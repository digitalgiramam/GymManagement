/**
 * Prisma singleton — uses @prisma/client/edge (WASM engine) + pg adapter.
 *
 * The edge client embeds the query engine as WASM inside the @prisma/client
 * package itself, so no platform-specific native binary is needed.
 * This is the correct approach for Vercel serverless functions.
 */
const { Pool }       = require('pg');
const { PrismaPg }   = require('@prisma/adapter-pg');
// Use the edge client: WASM engine, no native binary required
const { PrismaClient } = require('@prisma/client/edge');

const pool    = new Pool({ connectionString: process.env.DATABASE_URL, max: 1 });
const adapter = new PrismaPg(pool);

module.exports = new PrismaClient({ adapter });
