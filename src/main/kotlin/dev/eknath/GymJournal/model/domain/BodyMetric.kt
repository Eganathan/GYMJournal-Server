package dev.eknath.GymJournal.model.domain

/**
 * Represents a single metric measurement logged by a user.
 *
 * Catalyst DataStore table: BodyMetricEntries
 * User-defined columns: metricType, value, unit, logDate, notes
 * System columns (auto-provided by Catalyst — do NOT create manually):
 *   ROWID        → id
 *   CREATORID    → createdBy
 *   CREATEDTIME  → createdAt
 *   MODIFIEDTIME → updatedAt
 *
 * Computed metrics (bmi, smiComputed) are NEVER stored — they are derived
 * server-side in the snapshot endpoint from stored weight/height/smm values.
 */
data class BodyMetricEntry(
    val id: Long? = null,
    val metricType: String,     // e.g. "weight", "bodyFat", "custom_sgpt"
    val value: Double,
    val unit: String,           // e.g. "kg", "%", "µU/mL"
    val logDate: String,        // YYYY-MM-DD stored as Text
    val notes: String = "",
    // Catalyst system columns — auto-set, never written in toMap()
    val createdBy: String = "", // CREATORID
    val createdAt: String = "", // CREATEDTIME
    val updatedAt: String = ""  // MODIFIEDTIME
)

/**
 * A user-defined custom metric definition.
 *
 * Catalyst DataStore table: CustomMetricDefs
 * User-defined columns: metricKey, label, unit
 * System columns (auto-provided by Catalyst — do NOT create manually):
 *   ROWID       → id
 *   CREATORID   → createdBy
 *   CREATEDTIME → createdAt
 */
data class CustomMetricDef(
    val id: Long? = null,
    val metricKey: String,       // e.g. "custom_sgpt" — derived from label
    val label: String,           // e.g. "SGPT" — user-supplied display name
    val unit: String,            // e.g. "U/L" — may be empty string
    // Catalyst system columns — auto-set, never written in toMap()
    val createdBy: String = "",  // CREATORID
    val createdAt: String = ""   // CREATEDTIME
)
