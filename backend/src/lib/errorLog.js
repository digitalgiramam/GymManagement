/**
 * Persistent error logging — console.error() alone only shows up in Vercel's
 * own (ephemeral, hard-to-browse) function logs. This also writes each error
 * to the `error_logs` table so it can be listed and downloaded as a .txt
 * file from the Super Admin portal, without needing Vercel dashboard access.
 *
 * Deliberately fire-and-forget: a failure to log an error must never itself
 * throw or block the request that triggered it.
 */

const { query } = require('./db');

/**
 * @param {string} source  - short tag identifying where the error came from, e.g. "mailer", "unhandled"
 * @param {Error|string} err
 */
function logError(source, err) {
  const message = err instanceof Error ? err.message : String(err);
  const stack    = err instanceof Error ? err.stack : null;

  // Always keep the existing console output — this is unchanged behavior.
  console.error(`[${source}]`, err);

  // Best-effort persistence. Swallow any failure so logging can never crash
  // the caller (including the case where the error_logs table/migration
  // hasn't been created yet).
  query(
    `INSERT INTO error_logs (source, message, stack) VALUES ($1, $2, $3)`,
    [source, message, stack],
  ).catch((dbErr) => {
    console.error('[errorLog] Failed to persist error log:', dbErr.message);
  });
}

module.exports = { logError };
