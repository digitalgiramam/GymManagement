/**
 * Shared payment helpers — used by both the tenant-scoped payments route
 * (src/routes/payments.js) and the Super Admin payments route (src/routes/admin.js)
 * so the expiry/partial-payment logic can't drift between the two.
 */

const PAYMENT_SELECT = `
  SELECT p.*,
         m.id               AS m_id,
         m."fullName"       AS m_name,
         m.phone            AS m_phone,
         m."membershipExpiry" AS m_expiry,
         pm.id              AS pm_id,
         pm.name            AS pm_name,
         pl.name            AS pl_name
  FROM   payments p
  JOIN   members m          ON m.id  = p."memberId"
  JOIN   payment_methods pm ON pm.id = p."methodId"
  LEFT JOIN plans pl        ON pl.id = p."planId"
`;

function formatPayment(row) {
  const { m_id, m_name, m_phone, m_expiry, pm_id, pm_name, pl_name, ...p } = row;
  const now        = new Date();
  const paidAmount = parseFloat(p.amount)  || 0;
  const planFee    = parseFloat(p.planFee) || 0;

  // "Overdue" when subscription period has lapsed.
  // "Partial" when subscription is still active but amount < plan fee.
  // "Active"  when fully paid and subscription is still active.
  const extendedTo = p.membershipExtendedTo ? new Date(p.membershipExtendedTo) : null;
  const isExpired  = !extendedTo || extendedTo <= now;

  let membershipStatus;
  if (isExpired) {
    membershipStatus = 'Overdue';
  } else if (planFee > 0 && paidAmount < planFee) {
    membershipStatus = 'Partial';
  } else {
    membershipStatus = 'Active';
  }

  // Amount outstanding (0 for fully-paid or expired records)
  const overdueAmount = isExpired ? 0 : Math.max(0, planFee - paidAmount);

  return {
    ...p,
    amount: paidAmount,
    planFee,
    overdueAmount,
    planName: pl_name ?? null,
    membershipStatus,
    member: m_id  != null ? { id: m_id,  fullName: m_name,  phone: m_phone } : null,
    method: pm_id != null ? { id: pm_id, name: pm_name }                     : null,
  };
}

/**
 * Replays every payment for `memberId` in chronological order to recompute
 * `membershipExtendedTo` on each row and update `members.membershipExpiry`.
 *
 * Expiry logic per payment:
 *   base    = MAX(previous expiry, paymentDate)   — never back-date expiry
 *   newExp  = base + planDurationDays days
 *
 * Must be called inside an active transaction (`client` is a pg.PoolClient).
 */
async function recalculateMemberExpiry(client, memberId, tenantId) {
  const { rows } = await client.query(
    `SELECT id, "paymentDate", "planDurationDays"
     FROM   payments
     WHERE  "memberId" = $1 AND "tenantId" = $2
     ORDER  BY "paymentDate" ASC, id ASC`,
    [memberId, tenantId],
  );

  let currentExpiry = null;

  for (const p of rows) {
    const payDate = new Date(p.paymentDate);
    const base    = (currentExpiry && currentExpiry > payDate) ? currentExpiry : payDate;
    currentExpiry = new Date(base);
    currentExpiry.setDate(currentExpiry.getDate() + p.planDurationDays);

    await client.query(
      `UPDATE payments SET "membershipExtendedTo" = $1 WHERE id = $2`,
      [currentExpiry, p.id],
    );
  }

  // Write final expiry (null if member has no payments)
  await client.query(
    `UPDATE members SET "membershipExpiry" = $1 WHERE id = $2 AND "tenantId" = $3`,
    [currentExpiry, memberId, tenantId],
  );

  return currentExpiry;
}

module.exports = { PAYMENT_SELECT, formatPayment, recalculateMemberExpiry };
