/**
 * Seed script — run once to create the default owner and sample plans.
 * Usage: node src/seed.js
 */

require('dotenv').config();
const { PrismaClient } = require('@prisma/client');
const bcrypt = require('bcryptjs');

const prisma = new PrismaClient();

async function main() {
  console.log('🌱 Seeding database...');

  // ── Default owner ──────────────────────────────────────────────────────────
  const existingOwner = await prisma.owner.findUnique({
    where: { username: 'admin' },
  });

  if (!existingOwner) {
    const passwordHash = await bcrypt.hash('password123', 12);
    await prisma.owner.create({
      data: { username: 'admin', passwordHash },
    });
    console.log('✅ Default owner created  (username: admin  password: password123)');
  } else {
    console.log('ℹ️  Default owner already exists — skipping.');
  }

  // ── Sample plans ───────────────────────────────────────────────────────────
  const planCount = await prisma.plan.count();
  if (planCount === 0) {
    await prisma.plan.createMany({
      data: [
        { name: 'Monthly',   durationDays: 30,  fee: 30.0  },
        { name: 'Quarterly', durationDays: 90,  fee: 80.0  },
        { name: 'Annual',    durationDays: 365, fee: 280.0 },
      ],
    });
    console.log('✅ Sample plans created.');
  } else {
    console.log('ℹ️  Plans already exist — skipping.');
  }

  console.log('🎉 Seeding complete.');
}

main()
  .catch((e) => {
    console.error('❌ Seed failed:', e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
