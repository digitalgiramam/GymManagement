package com.gymmanager.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages the JWT token lifecycle using EncryptedSharedPreferences.
 * Tokens are stored AES-256-GCM encrypted — never in plain SharedPreferences.
 */
class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Persist the JWT token (called after successful login). */
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    /** Retrieve the stored JWT, or null if the user hasn't logged in. */
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** Clear the token (called on logout). */
    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    /** Returns true if a token is currently stored. */
    fun isLoggedIn(): Boolean = getToken() != null

    companion object {
        private const val PREFS_NAME = "gym_secure_prefs"
        private const val KEY_TOKEN  = "jwt_token"
    }
}
