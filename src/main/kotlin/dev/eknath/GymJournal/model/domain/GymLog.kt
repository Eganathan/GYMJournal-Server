package dev.eknath.GymJournal.model.domain

/**
 * A gym log entry — an incident, note, or health event associated with a specific gym day.
 *
 * Types:
 *   INJURY      — physical injury (e.g. "pulled hamstring on leg day")
 *   MEDICATION  — medication or supplement note (e.g. "took ibuprofen before session")
 *   NOTE        — general free-text note for the day
 *
 * Severity is optional and most relevant for INJURY / MEDICATION types.
 *   LOW | MEDIUM | HIGH
 *
 * Catalyst DataStore table: GymLogs
 * Columns: USER_ID (BigInt), logDate, type, title, description, severity
 */
data class GymLog(
    val id: Long? = null,
    val userId: Long,
    val logDate: String,       // YYYY-MM-DD
    val type: String,          // INJURY | MEDICATION | NOTE
    val title: String,
    val description: String = "",
    val severity: String = "",  // LOW | MEDIUM | HIGH — empty means not set
    val createdAt: String = ""  // Catalyst CREATEDTIME — auto-set, read-only
)
