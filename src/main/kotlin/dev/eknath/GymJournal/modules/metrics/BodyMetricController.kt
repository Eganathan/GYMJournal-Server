package dev.eknath.GymJournal.modules.metrics

import dev.eknath.GymJournal.model.dto.*
import dev.eknath.GymJournal.util.ApiResponse
import dev.eknath.GymJournal.util.currentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/metrics")
class BodyMetricController(private val service: BodyMetricService) {

    // ---------------------------------------------------------------------------
    // Metric entries
    // ---------------------------------------------------------------------------

    /**
     * POST /api/v1/metrics/entries
     * Batch log one or more metric measurements for a date.
     * Computed metric types (bmi, smiComputed) are rejected with 400.
     */
    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    fun batchLog(@Valid @RequestBody request: BatchLogMetricRequest): ApiResponse<*> =
        ApiResponse.ok(service.batchLog(currentUserId(), request))

    /**
     * GET /api/v1/metrics/entries?date=YYYY-MM-DD
     * All entries logged by the calling user on the given date.
     * Defaults to today if `date` is not supplied.
     */
    @GetMapping("/entries")
    fun getEntriesForDate(
        @RequestParam(required = false) date: String?
    ): ApiResponse<*> =
        ApiResponse.ok(
            service.getEntriesForDate(currentUserId(), date ?: LocalDate.now().toString())
        )

    /**
     * PUT /api/v1/metrics/entries/{id}
     * Update a single metric entry. Only the creator may edit (403 otherwise).
     */
    @PutMapping("/entries/{id}")
    fun updateEntry(
        @PathVariable id: Long,
        @RequestBody request: UpdateMetricEntryRequest
    ): ApiResponse<*> =
        ApiResponse.ok(service.updateEntry(currentUserId(), id, request))

    /**
     * DELETE /api/v1/metrics/entries/{id}
     * Delete a single metric entry. Only the creator may delete (403 otherwise).
     */
    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEntry(@PathVariable id: Long) =
        service.deleteEntry(currentUserId(), id)

    // ---------------------------------------------------------------------------
    // History & snapshot
    // ---------------------------------------------------------------------------

    /**
     * GET /api/v1/metrics/{metricType}/history?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
     * All entries for one metric type sorted by logDate ASC. Drives trend charts.
     * Defaults: startDate = 90 days ago, endDate = today.
     */
    @GetMapping("/{metricType}/history")
    fun getHistory(
        @PathVariable metricType: String,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ApiResponse<*> =
        ApiResponse.ok(service.getHistory(currentUserId(), metricType, startDate, endDate))

    /**
     * GET /api/v1/metrics/snapshot
     * Latest value per metric type (dashboard).
     * Also appends server-side computed bmi and smiComputed when source data is available.
     */
    @GetMapping("/snapshot")
    fun getSnapshot(): ApiResponse<*> =
        ApiResponse.ok(service.getSnapshot(currentUserId()))

    // ---------------------------------------------------------------------------
    // Custom metric definitions
    // ---------------------------------------------------------------------------

    /**
     * GET /api/v1/metrics/custom
     * List the calling user's custom metric definitions.
     */
    @GetMapping("/custom")
    fun listCustomDefs(): ApiResponse<*> =
        ApiResponse.ok(service.listCustomDefs(currentUserId()))

    /**
     * POST /api/v1/metrics/custom
     * Create a custom metric definition.
     * The metricKey is derived server-side from the label.
     * Returns 400 if a definition with the same derived key already exists.
     */
    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    fun addCustomDef(@Valid @RequestBody request: CreateCustomMetricRequest): ApiResponse<*> =
        ApiResponse.ok(service.addCustomDef(currentUserId(), request))

    /**
     * DELETE /api/v1/metrics/custom/{key}
     * Delete a custom metric definition and cascade-delete all its entries.
     */
    @DeleteMapping("/custom/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCustomDef(@PathVariable key: String) =
        service.deleteCustomDef(currentUserId(), key)
}
