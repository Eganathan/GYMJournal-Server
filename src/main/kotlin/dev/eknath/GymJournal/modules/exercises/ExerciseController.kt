package dev.eknath.GymJournal.modules.exercises

import dev.eknath.GymJournal.model.dto.*
import dev.eknath.GymJournal.util.ApiResponse
import dev.eknath.GymJournal.util.currentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/exercises")
class ExerciseController(private val service: ExerciseService) {

    // ---------------------------------------------------------------------------
    // Lookup — categories and equipment
    // ---------------------------------------------------------------------------

    /**
     * GET /api/v1/exercises/categories
     * Returns all muscle groups with full metadata (displayName, bodyRegion, etc.)
     * The frontend caches this list and uses it to power category filters and body diagrams.
     */
    @GetMapping("/categories")
    fun listCategories(): ApiResponse<*> =
        ApiResponse.ok(service.getAllMuscleGroups())

    /**
     * POST /api/v1/exercises/categories
     * Add a new muscle group to the library (community contribution).
     */
    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    fun addCategory(@Valid @RequestBody request: CreateMuscleGroupRequest): ApiResponse<*> =
        ApiResponse.ok(service.addMuscleGroup(request))

    /**
     * GET /api/v1/exercises/equipment
     * Returns all equipment types with full metadata (displayName, category, etc.)
     */
    @GetMapping("/equipment")
    fun listEquipment(): ApiResponse<*> =
        ApiResponse.ok(service.getAllEquipment())

    /**
     * POST /api/v1/exercises/equipment
     * Add a new equipment type to the library (community contribution).
     */
    @PostMapping("/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    fun addEquipment(@Valid @RequestBody request: CreateEquipmentRequest): ApiResponse<*> =
        ApiResponse.ok(service.addEquipment(request))

    // ---------------------------------------------------------------------------
    // Exercise library
    // ---------------------------------------------------------------------------

    /**
     * GET /api/v1/exercises
     * Browse the exercise library. Supports:
     *   ?category=LATS        — filter by primary muscle slug
     *   ?equipment=BARBELL    — filter by equipment slug
     *   ?difficulty=INTERMEDIATE
     *   ?search=pull          — substring match on name (in-memory)
     *   ?mine=true            — only show the calling user's exercises
     *   ?page=1&pageSize=20
     */
    @GetMapping
    fun listExercises(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) equipment: String?,
        @RequestParam(required = false) difficulty: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "false") mine: Boolean,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ApiResponse<*> {
        val (items, meta) = service.listExercises(
            callingUserId = currentUserId(),
            category      = category,
            equipment     = equipment,
            difficulty    = difficulty,
            search        = search,
            onlyMine      = mine,
            page          = page,
            pageSize      = pageSize
        )
        return ApiResponse.ok(items, meta)
    }

    /**
     * GET /api/v1/exercises/{id}
     * Returns the full exercise detail. Returns 404 if the exercise is private
     * and the caller is not the creator.
     */
    @GetMapping("/{id}")
    fun getExercise(@PathVariable id: Long): ApiResponse<*> =
        ApiResponse.ok(service.getExercise(currentUserId(), id))

    /**
     * POST /api/v1/exercises
     * Create a new exercise. Any authenticated user can contribute to the library.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createExercise(@Valid @RequestBody request: CreateExerciseRequest): ApiResponse<*> =
        ApiResponse.ok(service.createExercise(currentUserId(), request))

    /**
     * PUT /api/v1/exercises/{id}
     * Update an exercise. Only the original creator can edit.
     * Returns 403 if the caller is not the creator.
     */
    @PutMapping("/{id}")
    fun updateExercise(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateExerciseRequest
    ): ApiResponse<*> =
        ApiResponse.ok(service.updateExercise(currentUserId(), id, request))

    /**
     * DELETE /api/v1/exercises/{id}
     * Delete an exercise. Only the original creator can delete.
     * Returns 403 if the caller is not the creator.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteExercise(@PathVariable id: Long) =
        service.deleteExercise(currentUserId(), id)
}
