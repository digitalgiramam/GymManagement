package com.gymmanager.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File

/**
 * Saves the gym logo as a JPEG file in the app's private filesDir.
 * This avoids storing large Base64 strings in SharedPreferences.
 */
object LogoCache {

    private const val LOGO_FILE = "gymlogo.jpg"

    /** Save a base64-encoded logo (with or without the data-URI prefix). Null/blank deletes it. */
    fun save(context: Context, base64: String?) {
        val file = File(context.filesDir, LOGO_FILE)
        if (base64.isNullOrBlank()) {
            file.delete()
            return
        }
        try {
            val pure = base64
                .removePrefix("data:image/jpeg;base64,")
                .removePrefix("data:image/png;base64,")
                .trim()
            val bytes = Base64.decode(pure, Base64.NO_WRAP)
            file.writeBytes(bytes)
        } catch (_: Exception) {
            file.delete()
        }
    }

    /** Load the cached logo as a Bitmap, or null if none saved. */
    fun load(context: Context): Bitmap? {
        val file = File(context.filesDir, LOGO_FILE)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    fun exists(context: Context): Boolean = File(context.filesDir, LOGO_FILE).exists()
}
