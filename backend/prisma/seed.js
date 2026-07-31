/**
 * Prisma seed — SaaS version
 *
 * In the multi-tenant SaaS model there is NO global admin seed.
 * Default data (plans, payment methods, expense categories) is
 * seeded per-tenant automatically during the onboarding flow
 * (POST /api/onboarding/create-gym).
 *
 * This file exists only to satisfy `prisma db seed` if invoked,
 * and to document what the onboarding route seeds.
 */
console.log('✅  SaaS seed: no global data needed.');
console.log('   Default plans / payment methods / expense categories');
console.log('   are created automatically when a gym owner completes');
console.log('   onboarding via POST /api/onboarding/create-gym.');
