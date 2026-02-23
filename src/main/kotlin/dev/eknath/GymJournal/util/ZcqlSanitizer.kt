package dev.eknath.GymJournal.util

/**
 * ZCQL does not support bind parameters (PreparedStatement-style).
 * Always sanitize user-supplied string inputs before interpolating into ZCQL queries.
 */
object ZcqlSanitizer {
    fun sanitize(input: String): String =
        input.replace("'", "''")   // escape single quotes
             .replace(";", "")     // strip statement terminators
}
