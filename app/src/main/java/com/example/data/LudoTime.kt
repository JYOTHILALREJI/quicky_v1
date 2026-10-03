package com.example.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * ISO-8601 UTC helpers for Ludo server timestamps (minSdk 24 — no java.time).
 *
 * All deadlines are stored as absolute server timestamps (PRD §29) so every
 * device computes the same remaining seconds regardless of local clock drift.
 */
object LudoTime {

    /** JS `new Date().toISOString()` — "2026-10-03T10:20:30.123Z". */
    fun nowIso(): String = formatIso(System.currentTimeMillis())

    fun formatIso(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(epochMs))

    /**
     * Lenient parser for the shapes Postgres/JS emit:
     *   "...T10:20:30.123Z", "...T10:20:30Z", "...T10:20:30.123+00:00"
     * Returns null when unparseable — callers fall back to their own clock.
     */
    fun parseIsoToEpochMs(iso: String?): Long? {
        if (iso.isNullOrBlank() || iso == "null") return null
        val normalized = iso.trim().replace("+00:00", "Z").replace("+0000", "Z")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = true }
                return fmt.parse(normalized)?.time
            } catch (_: Exception) {
                // try the next pattern
            }
        }
        return null
    }

    /** "HH:MM" display fragment from an ISO string (room chat timestamps). */
    fun isoToClock(iso: String?): String? {
        val ms = parseIsoToEpochMs(iso) ?: return null
        return SimpleDateFormat("HH:mm", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
            .format(java.util.Date(ms))
    }
}
