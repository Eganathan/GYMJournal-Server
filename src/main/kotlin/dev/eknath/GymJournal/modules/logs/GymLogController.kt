package dev.eknath.GymJournal.modules.logs

import dev.eknath.GymJournal.model.dto.CreateGymLogRequest
import dev.eknath.GymJournal.model.dto.UpdateGymLogRequest
import dev.eknath.GymJournal.util.ApiResponse
import dev.eknath.GymJournal.util.currentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/logs")
class GymLogController(private val service: GymLogService) {

    /**
     * POST /api/v1/logs
     * Create a new gym log entry (injury, medication, or general note).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateGymLogRequest): ApiResponse<*> =
        ApiResponse.ok(service.create(currentUserId(), request))

    /**
     * GET /api/v1/logs?date=YYYY-MM-DD
     * Get all log entries for a specific gym day.
     */
    @GetMapping
    fun getByDate(@RequestParam date: String): ApiResponse<*> =
        ApiResponse.ok(service.getByDate(currentUserId(), date))

    /**
     * GET /api/v1/logs/recent
     * Get the 50 most recent log entries across all dates.
     */
    @GetMapping("/recent")
    fun getRecent(): ApiResponse<*> =
        ApiResponse.ok(service.getRecent(currentUserId()))

    /**
     * PUT /api/v1/logs/{id}
     * Update an existing log entry (title, description, severity).
     */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateGymLogRequest
    ): ApiResponse<*> = ApiResponse.ok(service.update(currentUserId(), id, request))

    /**
     * DELETE /api/v1/logs/{id}
     * Delete a log entry.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) {
        service.delete(currentUserId(), id)
    }
}
