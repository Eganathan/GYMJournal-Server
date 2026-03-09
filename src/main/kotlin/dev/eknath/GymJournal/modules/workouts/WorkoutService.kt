package dev.eknath.GymJournal.modules.workouts

import dev.eknath.GymJournal.model.domain.WorkoutSession
import dev.eknath.GymJournal.model.domain.WorkoutSet
import dev.eknath.GymJournal.model.domain.RoutineItemType
import dev.eknath.GymJournal.model.dto.*
import dev.eknath.GymJournal.modules.exercises.ExerciseRepository
import dev.eknath.GymJournal.modules.routines.RoutineService
import dev.eknath.GymJournal.util.ApiMeta
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val DB_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * Regex for ISO-8601 datetimes accepted by the API.
 * Accepts:
 *   - "2026-03-01T09:00:00"           (bare, no timezone)
 *   - "2026-03-01T09:00:00.176Z"      (JS toISOString() format — ms + Z)
 *   - "2026-03-01T09:00:00Z"          (Z only, no ms)
 *   - "2026-03-01T09:00:00+05:30"     (explicit offset)
 */
private val ISO_DT_REGEX = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})?$""")

/** Accepted values for WorkoutSet.itemType. */
private val VALID_ITEM_TYPES = setOf("EXERCISE", "REST", "CARDIO")

@Service
class WorkoutService(
    private val sessionRepo: WorkoutSessionRepository,
    private val setRepo: WorkoutSetRepository,
    private val routineService: RoutineService,
    private val exerciseRepo: ExerciseRepository
) {

    // ── Start Session ─────────────────────────────────────────────────────────

    /**
     * Creates a new workout session from a routine template.
     *
     * Every session must reference a valid routine — there is no standalone/free session.
     * This keeps workout history tied to the programme that produced it and allows clone/share flows.
     *
     * Steps:
     *   1. Fetch the routine (must be public or owned by the caller)
     *   2. Pre-populate WorkoutSets from the routine's items
     */
    fun startSession(request: StartWorkoutRequest, userId: Long): WorkoutSessionResponse {
        // Validate caller-supplied startedAt before touching the DB
        request.startedAt?.let { validateDatetimeFormat(it, "startedAt") }

        val now     = LocalDateTime.now()
        val nowStr  = now.format(DB_FMT)
        val dateStr = now.format(DATE_FMT)

        val routineId = request.routineId.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid routineId: '${request.routineId}'")

        val routine = routineService.findById(routineId)?.also { r ->
            if (r.isPublic != 1 && r.createdBy != userId) {
                throw IllegalAccessException("Routine $routineId is private")
            }
        } ?: throw NoSuchElementException("Routine with id '$routineId' not found")

        val startedAt = request.startedAt
            ?.let { normalizeToDbDatetime(it) }
            ?: nowStr

        val sessionName = request.name?.trim() ?: "${routine.name} - $dateStr"

        val session = WorkoutSession(
            userId      = userId,
            routineId   = routine.id!!,
            routineName = routine.name,
            name        = sessionName,
            status      = "IN_PROGRESS",
            startedAt   = startedAt,
            completedAt = "",
            notes       = "",
            createdAt   = nowStr,
            updatedAt   = nowStr
        )

        val saved = sessionRepo.save(session)

        // Pre-populate sets from routine items
        if (saved.id != null) {
            // Resolve current exercise names for all EXERCISE items in one pass.
            // This ensures renamed exercises show their current name in new sessions
            // while completed historical sets retain the name they were logged under.
            val exerciseNameCache = routine.items
                .filter { it.type == RoutineItemType.EXERCISE && it.exerciseId != null }
                .mapNotNull { it.exerciseId }
                .distinct()
                .associateWith { eid -> exerciseRepo.findById(eid)?.name }

            routine.items.forEachIndexed { idx, item ->
                val order = item.order.takeIf { it > 0 } ?: (idx + 1)
                when (item.type) {
                    RoutineItemType.EXERCISE -> {
                        val resolvedName = exerciseNameCache[item.exerciseId]
                            ?: item.exerciseName
                            ?: ""
                        // Only pass exerciseId if the exercise was found in the DB.
                        // Catalyst enforces a FK constraint on WorkoutSets.exerciseId → Exercises.ROWID;
                        // passing a stale/non-existent ID causes ZCServerException: Invalid Foreign key value.
                        // Falling back to null (with exerciseName intact) is safe — the FK column is non-mandatory.
                        val resolvedExerciseId = item.exerciseId?.takeIf { exerciseNameCache[it] != null }
                        val setCount = item.sets?.coerceAtLeast(1) ?: 1
                        repeat(setCount) { setIdx ->
                            setRepo.save(
                                WorkoutSet(
                                    sessionId       = saved.id,
                                    userId          = userId,
                                    exerciseId      = resolvedExerciseId,  // null when exercise not in DB
                                    exerciseName    = resolvedName,
                                    itemType        = "EXERCISE",
                                    orderInSession  = order,
                                    setNumber       = setIdx + 1,
                                    plannedReps     = item.repsPerSet ?: 0,
                                    plannedWeightKg = item.weightKg ?: "0",
                                    actualReps      = 0,
                                    actualWeightKg  = "0",
                                    durationSeconds = 0,
                                    distanceKm      = "0",
                                    rpe             = 0,
                                    isPersonalBest  = 0,
                                    notes           = item.notes ?: "",
                                    completedAt     = ""
                                )
                            )
                        }
                    }
                    RoutineItemType.REST -> {
                        setRepo.save(
                            WorkoutSet(
                                sessionId       = saved.id,
                                userId          = userId,
                                exerciseId      = null,   // REST has no exercise; FK col is non-mandatory
                                exerciseName    = "",
                                itemType        = "REST",
                                orderInSession  = order,
                                setNumber       = 1,
                                plannedReps     = 0,
                                plannedWeightKg = "0",
                                actualReps      = 0,
                                actualWeightKg  = "0",
                                durationSeconds = item.durationSeconds ?: 0,
                                distanceKm      = "0",
                                rpe             = 0,
                                isPersonalBest  = 0,
                                notes           = "",
                                completedAt     = ""
                            )
                        )
                    }
                    RoutineItemType.CARDIO -> {
                        setRepo.save(
                            WorkoutSet(
                                sessionId       = saved.id,
                                userId          = userId,
                                exerciseId      = null,   // CARDIO has no FK exercise; FK col is non-mandatory
                                exerciseName    = item.cardioName ?: "Cardio",
                                itemType        = "CARDIO",
                                orderInSession  = order,
                                setNumber       = 1,
                                plannedReps     = 0,
                                plannedWeightKg = "0",
                                actualReps      = 0,
                                actualWeightKg  = "0",
                                durationSeconds = (item.durationMinutes ?: 0) * 60,
                                distanceKm      = "0",
                                rpe             = 0,
                                isPersonalBest  = 0,
                                notes           = "",
                                completedAt     = ""
                            )
                        )
                    }
                }
            }
        }

        return buildSessionResponse(saved)
    }

    // ── List Sessions ─────────────────────────────────────────────────────────

    fun listSessions(
        userId: Long,
        status: String?,
        startDate: String?,
        endDate: String?,
        page: Int,
        pageSize: Int
    ): Pair<List<WorkoutSessionSummaryResponse>, ApiMeta> {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        val offset   = (safePage - 1) * safeSize

        val total   = sessionRepo.countByUser(userId, status, startDate, endDate)
        val sessions = sessionRepo.findByUser(userId, status, startDate, endDate, offset, safeSize)

        return sessions.map { it.toSummaryResponse() } to
               ApiMeta(page = safePage, pageSize = safeSize, total = total)
    }

    // ── Get Full Session ──────────────────────────────────────────────────────

    fun getSession(id: Long, userId: Long): WorkoutSessionResponse {
        val session = sessionRepo.findById(id)
            ?: throw NoSuchElementException("Workout session with id '$id' not found")
        if (session.userId != userId) throw IllegalAccessException("Session $id does not belong to this user")
        return buildSessionResponse(session)
    }

    // ── Patch Session ─────────────────────────────────────────────────────────

    fun patchSession(id: Long, request: PatchWorkoutRequest, userId: Long): WorkoutSessionResponse {
        val existing = sessionRepo.findById(id)
            ?: throw NoSuchElementException("Workout session with id '$id' not found")
        if (existing.userId != userId) throw IllegalAccessException("Session $id does not belong to this user")

        val updated = existing.copy(
            name  = request.name?.trim() ?: existing.name,
            notes = request.notes ?: existing.notes
        )
        sessionRepo.update(id, updated)
        return buildSessionResponse(sessionRepo.findById(id) ?: updated)
    }

    // ── Complete Session ──────────────────────────────────────────────────────

    /**
     * Marks the session as COMPLETED and runs personal best detection for all
     * EXERCISE sets that have actualReps > 0.
     *
     * [force] = false (default): rejects completion if the session has EXERCISE items
     * but none have been logged (actualReps > 0). This prevents accidental empty completions.
     * [force] = true: skips the check — useful for REST-only / CARDIO-only / stretching sessions.
     */
    fun completeSession(id: Long, userId: Long, force: Boolean = false): WorkoutSessionResponse {
        val existing = sessionRepo.findById(id)
            ?: throw NoSuchElementException("Workout session with id '$id' not found")
        if (existing.userId != userId) throw IllegalAccessException("Session $id does not belong to this user")
        if (existing.status == "COMPLETED") return buildSessionResponse(existing)

        // Fetch all sets once — reused for the guard check and PB detection
        val allSets = setRepo.findBySession(id, userId)

        // Guard: require at least one logged exercise set unless force=true
        if (!force) {
            val exerciseSetsForGuard = allSets.filter { it.itemType == "EXERCISE" }
            if (exerciseSetsForGuard.isNotEmpty() && exerciseSetsForGuard.none { it.actualReps > 0 }) {
                throw IllegalArgumentException(
                    "No exercise sets have been logged for this session. " +
                    "Log at least one set or pass ?force=true to complete anyway."
                )
            }
        }

        val nowStr    = LocalDateTime.now().format(DB_FMT)
        val completed = existing.copy(status = "COMPLETED", completedAt = nowStr)
        sessionRepo.update(id, completed)

        // PB detection — use the pre-fetched set list (snapshot taken before mark-complete)
        val exerciseSets  = allSets.filter { it.itemType == "EXERCISE" && it.actualReps > 0 }

        exerciseSets.groupBy { it.exerciseId }.forEach { (exerciseId, sessionExerciseSets) ->
            if (exerciseId == null) return@forEach   // shouldn't happen for EXERCISE sets, but guard anyway
            val history = setRepo.findExerciseHistory(userId, exerciseId)
                .filter { it.sessionId != id }

            sessionExerciseSets.forEach { sessionSet ->
                val isPb = detectPb(sessionSet, history)
                if (isPb) {
                    sessionSet.id?.let {
                        setRepo.update(it, sessionSet.copy(isPersonalBest = 1))
                    }
                }
            }
        }

        return buildSessionResponse(sessionRepo.findById(id) ?: completed)
    }

    /**
     * Returns true if [candidate] is a strict personal best compared to [history].
     *
     * PB logic: for sets with the same or more reps, check if the new weight
     * strictly exceeds all historical weights. Ties are NOT flagged as PBs.
     */
    private fun detectPb(candidate: WorkoutSet, history: List<WorkoutSet>): Boolean {
        val newWeight = candidate.actualWeightKg.toDoubleOrNull() ?: return false
        if (newWeight <= 0.0) return false

        val comparable = history.filter { it.actualReps >= candidate.actualReps }
        if (comparable.isEmpty()) return true  // first time performing this rep range

        val bestHistoricalWeight = comparable
            .mapNotNull { it.actualWeightKg.toDoubleOrNull() }
            .maxOrNull() ?: return true

        return newWeight > bestHistoricalWeight   // strict improvement only; ties are not flagged
    }

    // ── Delete Session ────────────────────────────────────────────────────────

    fun deleteSession(id: Long, userId: Long) {
        val existing = sessionRepo.findById(id)
            ?: throw NoSuchElementException("Workout session with id '$id' not found")
        if (existing.userId != userId) throw IllegalAccessException("Session $id does not belong to this user")

        setRepo.deleteAllBySession(id, userId)
        sessionRepo.delete(id)
    }

    // ── Set CRUD ──────────────────────────────────────────────────────────────

    fun addSet(sessionId: Long, request: AddSetRequest, userId: Long): WorkoutSetResponse {
        val session = sessionRepo.findById(sessionId)
            ?: throw NoSuchElementException("Workout session with id '$sessionId' not found")
        if (session.userId != userId) throw IllegalAccessException("Session $sessionId does not belong to this user")
        if (session.status == "COMPLETED")
            throw IllegalStateException("Cannot add sets to a completed session")

        // Validate inputs
        val itemType = request.itemType.uppercase()
        require(itemType in VALID_ITEM_TYPES) { "itemType must be one of: EXERCISE, REST, CARDIO" }
        request.completedAt?.let { validateDatetimeFormat(it, "completedAt") }
        validateWeightFormat(request.plannedWeightKg, "plannedWeightKg")
        validateWeightFormat(request.actualWeightKg, "actualWeightKg")

        val completedAtDb = request.completedAt?.let { normalizeToDbDatetime(it) } ?: ""

        val set = WorkoutSet(
            sessionId       = sessionId,
            userId          = userId,
            exerciseId      = request.exerciseId.takeIf { it != 0L },  // 0 means "none" → null (FK non-mandatory)
            exerciseName    = request.exerciseName.trim(),
            itemType        = itemType,
            orderInSession  = request.orderInSession,
            setNumber       = request.setNumber,
            plannedReps     = request.plannedReps,
            plannedWeightKg = request.plannedWeightKg,
            actualReps      = request.actualReps,
            actualWeightKg  = request.actualWeightKg,
            durationSeconds = request.durationSeconds,
            distanceKm      = request.distanceKm,
            rpe             = request.rpe,
            isPersonalBest  = 0,
            notes           = request.notes,
            completedAt     = completedAtDb
        )

        return setRepo.save(set).toResponse()
    }

    fun updateSet(sessionId: Long, setId: Long, request: UpdateSetRequest, userId: Long): WorkoutSetResponse {
        val session = sessionRepo.findById(sessionId)
            ?: throw NoSuchElementException("Workout session with id '$sessionId' not found")
        if (session.userId != userId) throw IllegalAccessException("Session $sessionId does not belong to this user")
        if (session.status == "COMPLETED")
            throw IllegalStateException("Cannot modify sets on a completed session")

        val existing = setRepo.findById(setId)
            ?: throw NoSuchElementException("Set with id '$setId' not found")
        if (existing.sessionId != sessionId) throw NoSuchElementException("Set $setId does not belong to session $sessionId")

        // Validate inputs
        request.completedAt?.let { validateDatetimeFormat(it, "completedAt") }
        request.plannedWeightKg?.let { validateWeightFormat(it, "plannedWeightKg") }
        request.actualWeightKg?.let  { validateWeightFormat(it, "actualWeightKg") }

        val completedAtDb = request.completedAt?.let { normalizeToDbDatetime(it) } ?: existing.completedAt

        val updated = existing.copy(
            actualReps      = request.actualReps      ?: existing.actualReps,
            actualWeightKg  = request.actualWeightKg  ?: existing.actualWeightKg,
            plannedReps     = request.plannedReps      ?: existing.plannedReps,
            plannedWeightKg = request.plannedWeightKg  ?: existing.plannedWeightKg,
            durationSeconds = request.durationSeconds  ?: existing.durationSeconds,
            distanceKm      = request.distanceKm       ?: existing.distanceKm,
            rpe             = request.rpe              ?: existing.rpe,
            notes           = request.notes            ?: existing.notes,
            completedAt     = completedAtDb
        )

        setRepo.update(setId, updated)
        return (setRepo.findById(setId) ?: updated).toResponse()
    }

    fun deleteSet(sessionId: Long, setId: Long, userId: Long) {
        val session = sessionRepo.findById(sessionId)
            ?: throw NoSuchElementException("Workout session with id '$sessionId' not found")
        if (session.userId != userId) throw IllegalAccessException("Session $sessionId does not belong to this user")
        if (session.status == "COMPLETED")
            throw IllegalStateException("Cannot delete sets from a completed session")

        val existing = setRepo.findById(setId)
            ?: throw NoSuchElementException("Set with id '$setId' not found")
        if (existing.sessionId != sessionId) throw NoSuchElementException("Set $setId does not belong to session $sessionId")

        setRepo.delete(setId)
    }

    // ── Exercise History & PBs ────────────────────────────────────────────────

    /**
     * Returns completed EXERCISE sets for [exerciseId] belonging to [userId],
     * paginated. Most recent first. Uses DB-level pagination.
     */
    fun getExerciseHistory(
        exerciseId: Long,
        userId: Long,
        page: Int,
        pageSize: Int
    ): Pair<List<WorkoutSetResponse>, ApiMeta> {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 100)
        val offset   = (safePage - 1) * safeSize

        val total   = setRepo.countExerciseHistory(userId, exerciseId)
        val history = setRepo.findExerciseHistory(userId, exerciseId, offset, safeSize)

        return history.map { it.toResponse() } to
               ApiMeta(page = safePage, pageSize = safeSize, total = total)
    }

    /**
     * Returns personal best sets for [exerciseId] belonging to [userId].
     * One record per distinct rep count (highest weight), sorted by reps descending.
     */
    fun getPersonalBests(exerciseId: Long, userId: Long): List<WorkoutSetResponse> {
        val history = setRepo.findExerciseHistory(userId, exerciseId)
        if (history.isEmpty()) return emptyList()

        return history
            .filter { it.actualReps > 0 }
            .groupBy { it.actualReps }
            .map { (_, sets) ->
                sets.maxByOrNull { it.actualWeightKg.toDoubleOrNull() ?: 0.0 }!!
            }
            .sortedByDescending { it.actualReps }
            .map { it.toResponse() }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun buildSessionResponse(session: WorkoutSession): WorkoutSessionResponse {
        val sets = session.id?.let { setRepo.findBySession(it, session.userId) } ?: emptyList()

        val groups = sets
            .groupBy { it.orderInSession }
            .map { (order, setsInSlot) ->
                val first = setsInSlot.first()
                SessionItemGroup(
                    orderInSession = order,
                    itemType       = first.itemType,
                    exerciseId     = first.exerciseId?.toString(),
                    exerciseName   = first.exerciseName,
                    sets           = setsInSlot.sortedBy { it.setNumber }.map { it.toResponse() }
                )
            }
            .sortedBy { it.orderInSession }

        return WorkoutSessionResponse(
            id          = session.id!!.toString(),
            name        = session.name,
            routineId   = session.routineId.toString(),
            routineName = session.routineName,
            status      = session.status,
            startedAt   = session.startedAt.replace(" ", "T"),
            completedAt = session.completedAt.takeIf { it.isNotBlank() }?.replace(" ", "T"),
            notes       = session.notes,
            createdAt   = session.createdAt.replace(" ", "T"),
            updatedAt   = session.updatedAt.replace(" ", "T"),
            exercises   = groups
        )
    }

    private fun WorkoutSession.toSummaryResponse() = WorkoutSessionSummaryResponse(
        id              = id!!.toString(),
        name            = name,
        routineId       = routineId.toString(),
        routineName     = routineName,
        status          = status,
        startedAt       = startedAt.replace(" ", "T"),
        completedAt     = completedAt.takeIf { it.isNotBlank() }?.replace(" ", "T"),
        durationMinutes = computeDuration(startedAt, completedAt),
        updatedAt       = updatedAt.replace(" ", "T")
    )

    /** Computes whole-minute duration between two DB datetime strings. Returns null if either is blank. */
    private fun computeDuration(startedAt: String, completedAt: String): Int? {
        if (startedAt.isBlank() || completedAt.isBlank()) return null
        return try {
            val start = LocalDateTime.parse(startedAt.replace(" ", "T"))
            val end   = LocalDateTime.parse(completedAt.replace(" ", "T"))
            Duration.between(start, end).toMinutes().toInt().takeIf { it >= 0 }
        } catch (_: DateTimeParseException) { null }
    }

    private fun WorkoutSet.toResponse() = WorkoutSetResponse(
        id              = id!!.toString(),
        sessionId       = sessionId.toString(),
        exerciseId      = exerciseId?.toString(),
        exerciseName    = exerciseName,
        itemType        = itemType,
        orderInSession  = orderInSession,
        setNumber       = setNumber,
        plannedReps     = plannedReps,
        plannedWeightKg = plannedWeightKg,
        actualReps      = actualReps.takeIf { it > 0 },
        actualWeightKg  = actualWeightKg.takeIf { it != "0" && it.isNotBlank() },
        durationSeconds = durationSeconds,
        distanceKm      = distanceKm,
        rpe             = rpe.takeIf { it > 0 },
        isPersonalBest  = isPersonalBest == 1,
        notes           = notes,
        completedAt     = completedAt.takeIf { it.isNotBlank() }?.replace(" ", "T")
    )

    // ── Validation helpers ────────────────────────────────────────────────────

    /** Throws [IllegalArgumentException] if [value] is not in an accepted ISO-8601 datetime format. */
    private fun validateDatetimeFormat(value: String, fieldName: String) {
        if (!ISO_DT_REGEX.matches(value)) {
            throw IllegalArgumentException(
                "'$fieldName' must be in ISO-8601 format: YYYY-MM-DDTHH:MM:SS (e.g. 2026-03-01T09:00:00). " +
                "Milliseconds and timezone suffix (Z / +HH:MM) are also accepted."
            )
        }
    }

    /**
     * Normalises an accepted ISO-8601 datetime string to the DB storage format `YYYY-MM-DD HH:MM:SS`.
     * Strips optional milliseconds and timezone suffix before replacing 'T' with a space.
     * Examples:
     *   "2026-03-07T10:25:49.176Z" → "2026-03-07 10:25:49"
     *   "2026-03-07T10:25:49Z"     → "2026-03-07 10:25:49"
     *   "2026-03-07T10:25:49"      → "2026-03-07 10:25:49"
     */
    private fun normalizeToDbDatetime(isoStr: String): String =
        isoStr.take(19).replace("T", " ")

    /** Throws [IllegalArgumentException] if [value] cannot be parsed as a non-negative decimal number. */
    private fun validateWeightFormat(value: String, fieldName: String) {
        if (value.toDoubleOrNull() == null) {
            throw IllegalArgumentException(
                "'$fieldName' must be a numeric value (e.g. '80.0' or '0'). Got: '$value'"
            )
        }
    }
}
