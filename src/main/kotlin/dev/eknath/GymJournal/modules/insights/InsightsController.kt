package dev.eknath.GymJournal.modules.insights

import dev.eknath.GymJournal.util.ApiResponse
import dev.eknath.GymJournal.util.currentUserId
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/metrics")
class InsightsController(private val service: InsightsService) {

    /**
     * GET /api/v1/metrics/insights
     *
     * Returns health insights derived from the calling user's most-recent
     * metric values. Each insight carries a status (OK / BORDERLINE / WARNING /
     * DANGER), a human-readable message, and the reference range used.
     *
     * The optional `gender` parameter (MALE | FEMALE) enables gender-aware
     * thresholds for body fat % and SMI. Omitting it falls back to
     * gender-neutral (conservative) ranges.
     *
     * Returns an empty list if the user has no metric data yet.
     */
    @GetMapping("/insights")
    fun getInsights(
        @RequestParam(required = false) gender: String?
    ): ApiResponse<*> {
        val parsedGender = gender?.uppercase()?.let {
            runCatching { Gender.valueOf(it) }.getOrNull()
        }
        return ApiResponse.ok(service.getInsights(currentUserId(), parsedGender))
    }
}
