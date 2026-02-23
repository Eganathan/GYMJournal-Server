package dev.eknath.GymJournal.model.domain

/**
 * Represents a single water intake log entry.
 * Each time the user logs water (e.g., a glass, a bottle), one entry is created.
 * Daily totals are computed by summing entries for a given date.
 *
 * Catalyst DataStore table: WaterIntakeLogs
 * Columns: userId, logDateTime, amountMl, notes
 */
data class WaterIntakeEntry(
    val id: Long? = null,
    val userId: String,
    val logDateTime: String,   // ISO-8601: "2025-01-15T08:30:00"
    val amountMl: Int,
    val notes: String = ""
)

/**
 * Daily summary — computed from entries, not stored directly.
 */
data class DailyWaterSummary(
    val date: String,          // "2025-01-15"
    val totalMl: Int,
    val goalMl: Int,
    val progressPercent: Int,
    val entries: List<WaterIntakeEntry>
)
