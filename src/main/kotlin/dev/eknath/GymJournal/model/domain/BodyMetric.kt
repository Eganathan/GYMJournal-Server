package dev.eknath.GymJournal.model.domain

/**
 * Represents a single metric measurement logged by a user.
 *
 * Catalyst DataStore table: BodyMetricEntries
 * User-defined columns: metricType, value, unit, logDate, notes, userId (explicit ownership)
 * System columns (auto-provided by Catalyst — do NOT create manually):
 *   ROWID        → id
 *   CREATEDTIME  → createdAt
 *   MODIFIEDTIME → updatedAt
 *
 * NOTE: userId is stored explicitly because CREATORID is set to app credentials
 * (not the end-user's ZID) when using ZCObject in AppSail.
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
    val createdBy: Long = 0L,   // BigInt column USER_ID — numeric WHERE is reliable in ZCQL
    val createdAt: String = "", // CREATEDTIME — auto-set by Catalyst
    val updatedAt: String = ""  // MODIFIEDTIME — auto-set by Catalyst
)

/**
 * A user-defined custom metric definition.
 *
 * Catalyst DataStore table: CustomMetricDefs
 * User-defined columns: metricKey, label, unit, userId (explicit ownership)
 * System columns (auto-provided by Catalyst — do NOT create manually):
 *   ROWID       → id
 *   CREATEDTIME → createdAt
 *
 * NOTE: userId is stored explicitly because CREATORID is set to app credentials
 * (not the end-user's ZID) when using ZCObject in AppSail.
 */
data class CustomMetricDef(
    val id: Long? = null,
    val metricKey: String,       // e.g. "custom_sgpt" — derived from label
    val label: String,           // e.g. "SGPT" — user-supplied display name
    val unit: String,            // e.g. "U/L" — may be empty string
    val createdBy: Long = 0L,    // BigInt column USER_ID — numeric WHERE is reliable in ZCQL
    val createdAt: String = ""   // CREATEDTIME — auto-set by Catalyst
)
