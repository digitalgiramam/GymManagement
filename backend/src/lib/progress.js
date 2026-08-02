/**
 * Shared progress-tracking helpers — used by both the staff-side routes
 * (routes/progress.js, routes/goals.js) and the member-portal self-service
 * routes (routes/member-portal.js), so the SQL/logic lives in one place.
 */

const { query } = require('./db');

/** BMI = weight(kg) / height(m)^2, rounded to 1 decimal. Null if either value is missing. */
function computeBmi(weightKg, heightCm) {
  if (!weightKg || !heightCm) return null;
  const heightM = heightCm / 100;
  return Math.round((weightKg / (heightM * heightM)) * 10) / 10;
}

async function getMemberHeight(tenantId, memberId) {
  const { rows } = await query(
    `SELECT "heightCm" FROM members WHERE id = $1 AND "tenantId" = $2`,
    [memberId, tenantId],
  );
  return rows[0]?.heightCm != null ? parseFloat(rows[0].heightCm) : null;
}

function toNum(v) {
  return v != null ? parseFloat(v) : null;
}

function shapeEntry(row, heightCm) {
  const weightKg = toNum(row.weightKg);
  return {
    ...row,
    weightKg,
    chestCm:  toNum(row.chestCm),
    waistCm:  toNum(row.waistCm),
    hipsCm:   toNum(row.hipsCm),
    armsCm:   toNum(row.armsCm),
    thighsCm: toNum(row.thighsCm),
    bmi: computeBmi(weightKg, heightCm),
  };
}

// ── Progress entries ─────────────────────────────────────────────────────────

async function listProgress(tenantId, memberId) {
  const { rows } = await query(
    `SELECT pe.*, s."fullName" AS "recordedByName"
     FROM progress_entries pe
     LEFT JOIN staff s ON s.id = pe."recordedByStaffId"
     WHERE pe."memberId" = $1 AND pe."tenantId" = $2
     ORDER BY pe."entryDate" DESC, pe.id DESC`,
    [memberId, tenantId],
  );
  const heightCm = await getMemberHeight(tenantId, memberId);
  return rows.map(row => shapeEntry(row, heightCm));
}

async function createProgress(tenantId, memberId, data, recordedByStaffId) {
  const { rows } = await query(
    `INSERT INTO progress_entries
       ("tenantId","memberId","recordedByStaffId","entryDate",
        "weightKg","chestCm","waistCm","hipsCm","armsCm","thighsCm",notes)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
     RETURNING *`,
    [
      tenantId, memberId, recordedByStaffId ?? null,
      data.entryDate ? new Date(data.entryDate) : new Date(),
      data.weightKg ?? null, data.chestCm ?? null, data.waistCm ?? null,
      data.hipsCm ?? null, data.armsCm ?? null, data.thighsCm ?? null,
      data.notes ?? null,
    ],
  );
  const heightCm = await getMemberHeight(tenantId, memberId);
  return { ...shapeEntry(rows[0], heightCm), recordedByName: null };
}

async function deleteProgress(tenantId, memberId, entryId) {
  const { rowCount } = await query(
    `DELETE FROM progress_entries WHERE id = $1 AND "memberId" = $2 AND "tenantId" = $3`,
    [entryId, memberId, tenantId],
  );
  return rowCount > 0;
}

// ── Goals ────────────────────────────────────────────────────────────────────

async function listGoals(tenantId, memberId) {
  const { rows } = await query(
    `SELECT * FROM member_goals WHERE "memberId" = $1 AND "tenantId" = $2 ORDER BY "createdAt" DESC`,
    [memberId, tenantId],
  );
  return rows.map(r => ({ ...r, targetWeightKg: toNum(r.targetWeightKg) }));
}

async function createGoal(tenantId, memberId, data) {
  const { rows } = await query(
    `INSERT INTO member_goals ("tenantId","memberId","goalType",description,"targetWeightKg","targetDate")
     VALUES ($1,$2,$3,$4,$5,$6) RETURNING *`,
    [
      tenantId, memberId, data.goalType ?? 'CUSTOM', data.description,
      data.targetWeightKg ?? null, data.targetDate ? new Date(data.targetDate) : null,
    ],
  );
  return { ...rows[0], targetWeightKg: toNum(rows[0].targetWeightKg) };
}

async function updateGoalStatus(tenantId, memberId, goalId, status) {
  const achievedAt = status === 'ACHIEVED' ? new Date() : null;
  const { rows } = await query(
    `UPDATE member_goals SET status = $1, "achievedAt" = $2
     WHERE id = $3 AND "memberId" = $4 AND "tenantId" = $5 RETURNING *`,
    [status, achievedAt, goalId, memberId, tenantId],
  );
  if (!rows[0]) return null;
  return { ...rows[0], targetWeightKg: toNum(rows[0].targetWeightKg) };
}

async function deleteGoal(tenantId, memberId, goalId) {
  const { rowCount } = await query(
    `DELETE FROM member_goals WHERE id = $1 AND "memberId" = $2 AND "tenantId" = $3`,
    [goalId, memberId, tenantId],
  );
  return rowCount > 0;
}

module.exports = {
  computeBmi, getMemberHeight,
  listProgress, createProgress, deleteProgress,
  listGoals, createGoal, updateGoalStatus, deleteGoal,
};
