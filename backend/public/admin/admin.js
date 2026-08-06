/**
 * Super Admin Portal — shared client-side helpers.
 * Auth is stored in localStorage (JWT, 12h expiry — see admin-auth.js).
 */
const ADMIN_TOKEN_KEY = 'gymsaas_admin_token';
const ADMIN_INFO_KEY  = 'gymsaas_admin_info';

function getAdminToken() {
  return localStorage.getItem(ADMIN_TOKEN_KEY);
}

function getAdminInfo() {
  try { return JSON.parse(localStorage.getItem(ADMIN_INFO_KEY) || 'null'); }
  catch { return null; }
}

function setAdminAuth(token, admin) {
  localStorage.setItem(ADMIN_TOKEN_KEY, token);
  localStorage.setItem(ADMIN_INFO_KEY, JSON.stringify(admin));
}

function clearAdminAuth() {
  localStorage.removeItem(ADMIN_TOKEN_KEY);
  localStorage.removeItem(ADMIN_INFO_KEY);
}

/** Call at the top of every protected page. Redirects to login if not authenticated. */
function requireAdminAuth() {
  if (!getAdminToken()) {
    window.location.href = 'login.html';
    return false;
  }
  return true;
}

function logoutAdmin() {
  clearAdminAuth();
  window.location.href = 'login.html';
}

/**
 * Fetch wrapper that attaches the admin JWT and handles 401/403 by
 * bouncing back to the login page.
 */
async function adminFetch(path, opts = {}) {
  const token = getAdminToken();
  const res = await fetch(path, {
    ...opts,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(opts.headers || {}),
    },
  });

  if (res.status === 401) {
    clearAdminAuth();
    window.location.href = 'login.html';
    throw new Error('Session expired.');
  }

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.error || `Request failed (${res.status})`);
  }
  return data;
}

/**
 * Downloads a file from an authenticated admin API endpoint (e.g. a CSV
 * export) by fetching it as a blob and triggering a client-side download,
 * since a plain <a href> link can't carry the Authorization header.
 */
async function adminDownload(path, filename) {
  const token = getAdminToken();
  const res = await fetch(path, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || `Download failed (${res.status})`);
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function fmtCurrency(n, symbol = '$') {
  const v = Number(n || 0);
  return symbol + v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '—';
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

function fmtMonth(ym) {
  // ym like "2026-03"
  const [y, m] = (ym || '').split('-');
  if (!y || !m) return ym || '—';
  const d = new Date(Number(y), Number(m) - 1, 1);
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short' });
}

function escapeHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
