package dev.eknath.GymJournal.model.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate

// ---------------------------------------------------------------------------
// Metric entry requests / responses
// ---------------------------------------------------------------------------

/**
 * A single metric entry within a batch log request.
 * `logDate` defaults to today if not supplied.
 */
data class LogMetricEntryRequest(
    @field:NotBlank val metricType: String,
    val value: Double,
    @field:NotBlank val unit: String,
    val logDate: String = LocalDate.now().toString(), // YYYY-MM-DD
    val notes: String = ""
)

/**
 * Batch log request — the frontend submits all filled-in fields for one date
 * in a single call.
 */
data class BatchLogMetricRequest(
    @field:NotEmpty @field:Valid val entries: List<LogMetricEntryRequest>
)

/**
 * Partial update for a single metric entry — all fields are optional.
 * Only non-null fields will be applied.
 */
data class UpdateMetricEntryRequest(
    val value: Double? = null,
    val unit: String? = null,
    val logDate: String? = null,   // YYYY-MM-DD
    val notes: String? = null
)

/** Full metric entry, returned from create, update, and list-by-date calls. */
data class MetricEntryResponse(
    val id: Long,
    val metricType: String,
    val value: Double,
    val unit: String,
    val logDate: String,
    val notes: String,
    val createdAt: String,
    val updatedAt: String
)

/**
 * One item in the dashboard snapshot — the most-recent value for a single metric type.
 * Also used for server-side computed metrics (bmi, smiComputed).
 */
data class MetricSnapshotItem(
    val metricType: String,
    val value: Double,
    val unit: String,
    val logDate: String   // date of most-recent measurement (or derived date for computed)
)

// ---------------------------------------------------------------------------
// Custom metric definition requests / responses
// ---------------------------------------------------------------------------

data class CreateCustomMetricRequest(
    @field:NotBlank val label: String,
    val unit: String = ""
)

data class CustomMetricDefResponse(
    val id: Long,
    val metricKey: String,
    val label: String,
    val unit: String
)
