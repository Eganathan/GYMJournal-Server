package dev.eknath.GymJournal.modules.routines

import dev.eknath.GymJournal.model.domain.Routine
import dev.eknath.GymJournal.model.dto.*
import dev.eknath.GymJournal.util.ApiMeta
import org.springframework.stereotype.Service

@Service
class RoutineService(
    private val routineRepo: RoutineRepository
) {

    // ── List / Browse ─────────────────────────────────────────────────────────

    /**
     * Returns paginated routines visible to [callingUserId].
     *
     * When [onlyMine] is true returns only the caller's routines (public + private).
     * Otherwise returns all public routines.
     * An optional [search] keyword filters by name substring (case-insensitive, in-memory).
     */
    fun listRoutines(
        callingUserId: String,
        onlyMine: Boolean,
        search: String?,
        page: Int,
        pageSize: Int
    ): Pair<List<RoutineSummaryResponse>, ApiMeta> {
        var routines = routineRepo.findAll(callingUserId, onlyMine)

        if (!search.isNullOrBlank()) {
            val term = search.trim()
            routines = routines.filter { it.name.contains(term, ignoreCase = true) }
        }

        val total = routines.size.toLong()
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        val paged = routines
            .drop((safePage - 1) * safeSize)
            .take(safeSize)
            .map { it.toSummaryResponse() }

        return paged to ApiMeta(page = safePage, pageSize = safeSize, total = total)
    }

    // ── Get Single ────────────────────────────────────────────────────────────

    /**
     * Returns a full routine if the caller is allowed to view it:
     *   - Public routines are visible to everyone
     *   - Private routines are only visible to their creator
     */
    fun getRoutine(id: Long, callingUserId: String): RoutineResponse {
        val routine = routineRepo.findById(id)
            ?: throw NoSuchElementException("Routine with id '$id' not found")

        if (routine.isPublic != 1 && routine.createdBy != callingUserId) {
            throw IllegalAccessException("Routine $id is private")
        }

        return routine.toResponse()
    }

    // ── Create ────────────────────────────────────────────────────────────────

    fun createRoutine(request: CreateRoutineRequest, userId: String): RoutineResponse {
        val now = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val routine = Routine(
            name             = request.name.trim(),
            description      = request.description.trim(),
            items            = request.items,
            estimatedMinutes = request.estimatedMinutes,
            tags             = request.tags,
            isPublic         = if (request.isPublic) 1 else 0,
            createdBy        = userId,
            createdAt        = now,
            updatedAt        = now
        )
        return routineRepo.save(routine).toResponse()
    }

    // ── Update ────────────────────────────────────────────────────────────────

    fun updateRoutine(id: Long, request: UpdateRoutineRequest, userId: String): RoutineResponse {
        val existing = routineRepo.findById(id)
            ?: throw NoSuchElementException("Routine with id '$id' not found")

        if (existing.createdBy != userId) {
            throw IllegalAccessException("Routine $id does not belong to this user")
        }

        val updated = existing.copy(
            name             = request.name?.trim() ?: existing.name,
            description      = request.description?.trim() ?: existing.description,
            items            = request.items ?: existing.items,
            estimatedMinutes = request.estimatedMinutes ?: existing.estimatedMinutes,
            tags             = request.tags ?: existing.tags,
            isPublic         = request.isPublic?.let { if (it) 1 else 0 } ?: existing.isPublic
        )

        routineRepo.update(id, updated)
        return (routineRepo.findById(id) ?: updated).toResponse()
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun deleteRoutine(id: Long, userId: String) {
        val existing = routineRepo.findById(id)
            ?: throw NoSuchElementException("Routine with id '$id' not found")

        if (existing.createdBy != userId) {
            throw IllegalAccessException("Routine $id does not belong to this user")
        }

        routineRepo.delete(id)
    }

    // ── Clone ─────────────────────────────────────────────────────────────────

    /**
     * Creates a private copy of [id] owned by [userId].
     *
     * The caller can clone:
     *   - Any public routine
     *   - Their own routines (public or private)
     */
    fun cloneRoutine(id: Long, userId: String): RoutineResponse {
        val source = routineRepo.findById(id)
            ?: throw NoSuchElementException("Routine with id '$id' not found")

        if (source.isPublic != 1 && source.createdBy != userId) {
            throw IllegalAccessException("Routine $id is private and cannot be cloned")
        }

        val now = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val clone = Routine(
            name             = source.name,
            description      = source.description,
            items            = source.items,
            estimatedMinutes = source.estimatedMinutes,
            tags             = source.tags,
            isPublic         = 0,       // clones are always private by default
            createdBy        = userId,
            createdAt        = now,
            updatedAt        = now
        )
        return routineRepo.save(clone).toResponse()
    }

    // ── Internal: fetch by id (used by WorkoutService) ─────────────────────────

    fun findById(id: Long): Routine? = routineRepo.findById(id)

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun Routine.toResponse() = RoutineResponse(
        id               = id!!,
        name             = name,
        description      = description,
        items            = items,
        estimatedMinutes = estimatedMinutes,
        tags             = tags,
        isPublic         = isPublic == 1,
        createdBy        = createdBy,
        createdAt        = createdAt.replace(" ", "T"),
        updatedAt        = updatedAt.replace(" ", "T")
    )

    private fun Routine.toSummaryResponse() = RoutineSummaryResponse(
        id               = id!!,
        name             = name,
        description      = description,
        estimatedMinutes = estimatedMinutes,
        tags             = tags,
        itemCount        = items.size,
        isPublic         = isPublic == 1,
        createdBy        = createdBy,
        updatedAt        = updatedAt.replace(" ", "T")
    )
}
