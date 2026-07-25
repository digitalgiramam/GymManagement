package com.gymmanager.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── View helpers ───────────────────────────────────────────────────────────

fun View.show() { visibility = View.VISIBLE }
fun View.hide() { visibility = View.GONE }

fun View.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration).show()
}

fun View.showSnackbarError(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG)
        .setBackgroundTint(context.getColor(android.R.color.holo_red_dark))
        .show()
}

// ── Date / number formatting ───────────────────────────────────────────────

private val dateFormat    = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())

fun String.toDisplayDate(): String = runCatching {
    val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    dateFormat.format(iso.parse(this) ?: Date())
}.getOrDefault(this)

fun String.toDisplayDateTime(): String = runCatching {
    val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    dateTimeFormat.format(iso.parse(this) ?: Date())
}.getOrDefault(this)

fun Double.toCurrencyString(): String = currencyFormat.format(this)
