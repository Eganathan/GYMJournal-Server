package dev.eknath.GymJournal.modules.workouts

import dev.eknath.GymJournal.model.dto.*
import dev.eknath.GymJournal.util.ApiResponse
import dev.eknath.GymJournal.util.currentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

// Note: This controller intentionally uses full path strings on each mapping rather than
// a class-level @RequestMapping because it spans two path prefixes:
//   /api/v1/workouts/...     — session and set endpoints
//   /api/v1/exercises/...    — history and PB endpoints

@RestController
class WorkoutController(private val service: WorkoutService) {

    // ── Session endpoints ─────────────────────────────────────────────────────

    /**
     * POST /api/v1/workouts
     * Start a new workout session.
     * Body: { routineId?: Long, name?: String, startedAt?: String (ISO-8601) }
     * If routineId is provided, sets are pre-populated from the routine's items.
     */
    @PostMapping("/api/v1/workouts")
    @ResponseStatus(HttpStatus.CREATED)
    fun startSession(@Valid @RequestBody request: StartWorkoutRequest): ApiResponse<*> =
        ApiResponse.ok(service.startSession(request, currentUserId()))

    /**
     * GET /api/v1/workouts
     * List the calling user's sessions, ordered by startedAt DESC.
     * ?status=IN_PROGRESS|COMPLETED   — filter by status
     * ?startDate=YYYY-MM-DD           — only sessions on or after this date
     * ?endDate=YYYY-MM-DD             — only sessions on or before this date
     * ?page=1&pageSize=20
     */
    @GetMapping("/api/v1/workouts")
    fun listSessions(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ApiResponse<*> {
        val (items, meta) = service.listSessions(
            userId    = currentUserId(),
            status    = status,
            startDate = startDate,
            endDate   = endDate,
            page      = page,
            pageSize  = pageSize
        )
        return ApiResponse.ok(items, meta)
    }

    /**
     * GET /api/v1/workouts/{id}
     * Returns the full session detail with sets grouped by orderInSession.
     * Returns 403 if the session belongs to a different user.
     */
    @GetMapping("/api/v1/workouts/{id}")
    fun getSession(@PathVariable id: Long): ApiResponse<*> =
        ApiResponse.ok(service.getSession(id, currentUserId()))

    /**
     * PATCH /api/v1/workouts/{id}
     * Update session name and/or notes.
     * Returns 403 if the caller is not the session owner.
     */
    @PatchMapping("/api/v1/workouts/{id}")
    fun patchSession(
        @PathVariable id: Long,
        @Valid @RequestBody request: PatchWorkoutRequest
    ): ApiResponse<*> =
        ApiResponse.ok(service.patchSession(id, request, currentUserId()))

    /**
     * POST /api/v1/workouts/{id}/complete
     * Mark the session as COMPLETED and run personal best detection.
     * Returns 403 if the caller is not the session owner.
     * Idempotent — calling on an already-completed session returns the session unchanged.
     *
     * ?force=true  — bypass the "no sets logged" guard (useful for REST-only / CARDIO-only sessions).
     */
    @PostMapping("/api/v1/workouts/{id}/complete")
    fun completeSession(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") force: Boolean
    ): ApiResponse<*> =
        ApiResponse.ok(service.completeSession(id, currentUserId(), force))

    /**
     * DELETE /api/v1/workouts/{id}
     * Delete the session and all its sets.
     * Returns 403 if the caller is not the session owner.
     */
    @DeleteMapping("/api/v1/workouts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSession(@PathVariable id: Long) =
        service.deleteSession(id, currentUserId())

    // ── Set endpoints ─────────────────────────────────────────────────────────

    /**
     * POST /api/v1/workouts/{sessionId}/sets
     * Add a set to an in-progress session.
     * Use itemType = "EXERCISE" | "REST" | "CARDIO" to describe the set type.
     * Returns 403 if the session belongs to a different user.
     * Returns 409 if the session is already COMPLETED.
     */
    @PostMapping("/api/v1/workouts/{sessionId}/sets")
    @ResponseStatus(HttpStatus.CREATED)
    fun addSet(
        @PathVariable sessionId: Long,
        @Valid @RequestBody request: AddSetRequest
    ): ApiResponse<*> =
        ApiResponse.ok(service.addSet(sessionId, request, currentUserId()))

    /**
     * PATCH /api/v1/workouts/{sessionId}/sets/{setId}
     * Update a set's actual reps, weight, RPE, notes, or completion timestamp.
     * All fields are optional — only supplied fields are overwritten.
     * Returns 403 if the session belongs to a different user.
     * Returns 409 if the session is already COMPLETED.
     */
    @PatchMapping("/api/v1/workouts/{sessionId}/sets/{setId}")
    fun updateSet(
        @PathVariable sessionId: Long,
        @PathVariable setId: Long,
        @Valid @RequestBody request: UpdateSetRequest
    ): ApiResponse<*> =
        ApiResponse.ok(service.updateSet(sessionId, setId, request, currentUserId()))

    /**
     * DELETE /api/v1/workouts/{sessionId}/sets/{setId}
     * Remove a set from a session.
     * Returns 403 if the session belongs to a different user.
     * Returns 409 if the session is already COMPLETED.
     */
    @DeleteMapping("/api/v1/workouts/{sessionId}/sets/{setId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSet(
        @PathVariable sessionId: Long,
        @PathVariable setId: Long
    ) = service.deleteSet(sessionId, setId, currentUserId())

    // ── Exercise history & PBs ────────────────────────────────────────────────

    /**
     * GET /api/v1/exercises/{exerciseId}/history
     * Returns completed EXERCISE sets for the calling user for this exercise.
     * Most recent first. Paginated at the DB level.
     * ?page=1&pageSize=50
     */
    @GetMapping("/api/v1/exercises/{exerciseId}/history")
    fun getExerciseHistory(
        @PathVariable exerciseId: Long,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int
    ): ApiResponse<*> {
        val (items, meta) = service.getExerciseHistory(exerciseId, currentUserId(), page, pageSize)
        return ApiResponse.ok(items, meta)
    }

    /**
     * GET /api/v1/exercises/{exerciseId}/pbs
     * Returns personal best sets for the calling user for this exercise.
     * One best-weight record per distinct rep count, sorted by reps descending.
     */
    @GetMapping("/api/v1/exercises/{exerciseId}/pbs")
    fun getPersonalBests(@PathVariable exerciseId: Long): ApiResponse<*> =
        ApiResponse.ok(service.getPersonalBests(exerciseId, currentUserId()))
}
