package com.orienteer.app.presentation.setup

/**
 * Pure helpers for the Setup “Other” custom-km field (chip-sized text entry).
 */
object CustomDistanceInput {

    /** Digits and at most one decimal point; strips other characters. */
    fun sanitize(raw: String): String {
        val sb = StringBuilder(raw.length)
        var seenDot = false
        for (ch in raw) {
            when {
                ch.isDigit() -> sb.append(ch)
                ch == '.' && !seenDot -> {
                    sb.append(ch)
                    seenDot = true
                }
            }
        }
        return sb.toString()
    }

    /** Parses sanitized km text; blank or invalid → null. */
    fun parseKm(sanitized: String): Double? {
        if (sanitized.isBlank() || sanitized == ".") return null
        return sanitized.toDoubleOrNull()
    }
}
