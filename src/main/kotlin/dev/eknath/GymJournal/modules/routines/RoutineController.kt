package dev.eknath.GymJournal.modules.routines

import dev.eknath.GymJournal.model.dto.CreateRoutineRequest
import dev.eknath.GymJournal.model.dto.UpdateRoutineRequest
import dev.eknath.GymJournal.util.ApiResponse
import dev.eknath.GymJournal.util.currentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/routines")
class RoutineController(private val service: RoutineService) {

    /**
     * GET /api/v1/routines
     * Browse routines. By default returns all public routines.
     *   ?mine=true       — show only the calling user's routines (public + private)
     *   ?search=push     — substring match on name (in-memory)
     *   ?page=1&pageSize=20
     */
    @GetMapping
    fun listRoutines(
        @RequestParam(defaultValue = "false") mine: Boolean,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ApiResponse<*> {
        val (items, meta) = service.listRoutines(
            callingUserId = currentUserId(),
            onlyMine      = mine,
            search        = search,
            page          = page,
            pageSize      = pageSize
        )
        return ApiResponse.ok(items, meta)
    }

    /**
     * GET /api/v1/routines/{id}
     * Returns full routine detail. Private routines are only visible to their creator.
     * Returns 403 if the caller is not the creator.
     */
    @GetMapping("/{id}")
    fun getRoutine(@PathVariable id: Long): ApiResponse<*> =
        ApiResponse.ok(service.getRoutine(id, currentUserId()))

    /**
     * POST /api/v1/routines
     * Create a new routine template.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRoutine(@Valid @RequestBody request: CreateRoutineRequest): ApiResponse<*> =
        ApiResponse.ok(service.createRoutine(request, currentUserId()))

    /**
     * PUT /api/v1/routines/{id}
     * Update a routine. Only the creator can edit.
     * Returns 403 if the caller is not the creator.
     */
    @PutMapping("/{id}")
    fun updateRoutine(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateRoutineRequest
    ): ApiResponse<*> =
        ApiResponse.ok(service.updateRoutine(id, request, currentUserId()))

    /**
     * DELETE /api/v1/routines/{id}
     * Delete a routine. Only the creator can delete.
     * Returns 403 if the caller is not the creator.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRoutine(@PathVariable id: Long) =
        service.deleteRoutine(id, currentUserId())

    /**
     * POST /api/v1/routines/{id}/clone
     * Clone a routine into the caller's library (always created as private).
     * Any user can clone a public routine; only the creator can clone a private one.
     * Returns 403 if the routine is private and the caller is not the creator.
     */
    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    fun cloneRoutine(@PathVariable id: Long): ApiResponse<*> =
        ApiResponse.ok(service.cloneRoutine(id, currentUserId()))
}
