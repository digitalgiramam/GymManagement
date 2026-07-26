package com.gymmanager.data.local

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages JWT token lifecycle using EncryptedSharedPreferences (AES-256-GCM).
 * Also stores tenantId so the app can check onboarding status without decoding the JWT.
 *
 * Never store tokens in plain SharedPreferences.
 */
class TokenManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = try {
        createEncryptedPrefs()
    } catch (e: Exception) {
        // Corrupted prefs (e.g. from a previous install with a different keystore).
        // Wipe and recreate so the app can still launch — the user will just need to log in again.
        Log.w(TAG, "EncryptedSharedPreferences corrupted; wiping and recreating. ${e.message}")
        context.deleteSharedPreferences(PREFS_NAME)
        createEncryptedPrefs()
    }

    private fun createEncryptedPrefs() = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // ── Token ─────────────────────────────────────────────────────────────────

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun isLoggedIn(): Boolean = getToken() != null

    // ── Tenant ────────────────────────────────────────────────────────────────

    /**
     * Saves tenantId after onboarding or sign-in (null = onboarding not yet completed).
     * -1 is used as a sentinel for "explicitly null / no tenant".
     */
    fun saveTenantId(tenantId: Int?) {
        prefs.edit().putInt(KEY_TENANT_ID, tenantId ?: SENTINEL_NULL).apply()
    }

    /** Returns null if no tenant is set (onboarding not complete), or the tenantId. */
    fun getTenantId(): Int? {
        val stored = prefs.getInt(KEY_TENANT_ID, SENTINEL_NULL)
        return if (stored == SENTINEL_NULL) null else stored
    }

    fun hasCompletedOnboarding(): Boolean = getTenantId() != null

    // ── Role ──────────────────────────────────────────────────────────────────

    fun saveRole(role: String) {
        prefs.edit().putString(KEY_ROLE, role).apply()
    }

    /** Returns "OWNER" if no role stored (backwards compat). */
    fun getRole(): String = prefs.getString(KEY_ROLE, "OWNER") ?: "OWNER"

    fun isOwner()  = getRole() == "OWNER"
    fun isStaff()  = getRole() == "STAFF"
    fun isMember() = getRole() == "MEMBER"

    // ── Currency ──────────────────────────────────────────────────────────────

    fun saveCurrencySymbol(symbol: String) {
        prefs.edit().putString(KEY_CURRENCY, symbol).apply()
    }

    fun getCurrencySymbol(): String = prefs.getString(KEY_CURRENCY, "$") ?: "$"

    // ── User display info (non-sensitive, cached for UI) ───────────────────────

    fun saveUserInfo(name: String, email: String, avatarUrl: String?) {
        prefs.edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_AVATAR, avatarUrl)
            .apply()
    }

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)
    fun getUserAvatar(): String? = prefs.getString(KEY_USER_AVATAR, null)

    // ── Full clear (logout) ────────────────────────────────────────────────────

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG           = "TokenManager"
        private const val PREFS_NAME    = "gym_secure_prefs"
        private const val KEY_TOKEN     = "jwt_token"
        private const val KEY_TENANT_ID = "tenant_id"
        private const val KEY_USER_NAME  = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val KEY_ROLE       = "user_role"
        private const val KEY_CURRENCY   = "currency_symbol"
        private const val SENTINEL_NULL = -1
    }
}
