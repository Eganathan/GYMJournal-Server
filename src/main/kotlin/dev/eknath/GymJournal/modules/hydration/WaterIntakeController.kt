package dev.eknath.GymJournal.modules.hydration

import dev.eknath.GymJournal.model.dto.LogWaterRequest
import dev.eknath.GymJournal.model.dto.UpdateWaterEntryRequest
import dev.eknath.GymJournal.util.ApiResponse
import dev.eknath.GymJournal.util.currentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/water")
class WaterIntakeController(private val service: WaterIntakeService) {

    /**
     * POST /api/v1/water
     * Log a water entry. amountMl is required; logDateTime defaults to now.
     *
     * Body: { "amountMl": 250, "logDateTime": "2025-01-15T08:30:00", "notes": "Morning glass" }
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun logWater(@Valid @RequestBody request: LogWaterRequest): ApiResponse<*> {
        val entry = service.logWater(currentUserId(), request)
        return ApiResponse.ok(entry)
    }

    /**
     * GET /api/v1/water/today
     * Shortcut for today's summary.
     */
    @GetMapping("/today")
    fun getToday(): ApiResponse<*> {
        val today = LocalDate.now().toString()
        return ApiResponse.ok(service.getDailySummary(currentUserId(), today))
    }

    /**
     * GET /api/v1/water/daily?date=2025-01-15
     * Get daily summary + all entries for a specific date.
     */
    @GetMapping("/daily")
    fun getDaily(@RequestParam date: String): ApiResponse<*> {
        return ApiResponse.ok(service.getDailySummary(currentUserId(), date))
    }

    /**
     * GET /api/v1/water/history?startDate=2025-01-01&endDate=2025-01-31
     * Get daily totals for a date range (for trend charts).
     */
    @GetMapping("/history")
    fun getHistory(
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ApiResponse<*> {
        return ApiResponse.ok(service.getHistory(currentUserId(), startDate, endDate))
    }

    /**
     * PUT /api/v1/water/{id}
     * Update an existing entry (fix a wrong amount etc.)
     */
    @PutMapping("/{id}")
    fun updateEntry(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateWaterEntryRequest
    ): ApiResponse<*> {
        return ApiResponse.ok(service.updateEntry(currentUserId(), id, request))
    }

    /**
     * DELETE /api/v1/water/{id}
     * Remove a logged entry.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEntry(@PathVariable id: Long) {
        service.deleteEntry(currentUserId(), id)
    }
}
