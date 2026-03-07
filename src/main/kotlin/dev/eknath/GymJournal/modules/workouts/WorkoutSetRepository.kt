package dev.eknath.GymJournal.modules.workouts

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.WorkoutSet
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import org.springframework.stereotype.Repository

private const val TABLE = "WorkoutSets"

@Repository
class WorkoutSetRepository(
    private val db: CatalystDataStoreRepository
) {

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Looks up a set by ROWID. Returns null if not found or row is blank (non-existent ROWID). */
    fun findById(id: Long): WorkoutSet? =
        db.getRow(TABLE, id)?.toSet()?.takeIf { it.userId.isNotBlank() }

    /**
     * All sets belonging to a session, ordered by slot then set number.
     *
     * Uses only a numeric sessionId predicate in ZCQL (numeric comparisons are reliable);
     * the userId check is applied in-memory as a safety guard.
     * ZCQL cap is 300 rows — hard limit of 300 sets per session.
     */
    fun findBySession(sessionId: Long, userId: String): List<WorkoutSet> =
        db.query(
            "SELECT * FROM $TABLE WHERE sessionId = $sessionId" +
            " ORDER BY orderInSession ASC, setNumber ASC LIMIT 0,300"
        ).map { it.toSet() }.filter { it.userId == userId }

    /**
     * Completed EXERCISE sets for [exerciseId] belonging to [userId], most recent first.
     *
     * ZCQL WHERE on user-created string columns is unreliable in AppSail — fetch by exerciseId
     * (numeric comparison, reliable) and filter userId + completedAt in-memory.
     * [offset] / [limit] applied after in-memory filtering.
     */
    fun findExerciseHistory(userId: String, exerciseId: Long, offset: Int = 0, limit: Int = 100): List<WorkoutSet> =
        db.query(
            "SELECT * FROM $TABLE WHERE exerciseId = $exerciseId" +
            " AND itemType = 'EXERCISE'" +
            " ORDER BY completedAt DESC LIMIT 0,300"
        ).map { it.toSet() }
         .filter { it.userId == userId && it.completedAt.isNotBlank() }
         .drop(offset)
         .take(limit)

    fun countExerciseHistory(userId: String, exerciseId: Long): Long =
        db.query(
            "SELECT * FROM $TABLE WHERE exerciseId = $exerciseId AND itemType = 'EXERCISE' LIMIT 0,300"
        ).map { it.toSet() }
         .count { it.userId == userId && it.completedAt.isNotBlank() }
         .toLong()

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun save(set: WorkoutSet): WorkoutSet {
        val row = db.insert(TABLE, set.toMap())
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        return if (rowId != null) findById(rowId) ?: set.copy(id = rowId) else set
    }

    fun update(id: Long, set: WorkoutSet) =
        db.update(TABLE, id, set.toMap())

    fun delete(id: Long) = db.delete(TABLE, id)

    /**
     * Deletes all sets belonging to a session.
     * Called when a session itself is deleted.
     */
    fun deleteAllBySession(sessionId: Long, userId: String) {
        val sets = findBySession(sessionId, userId)
        sets.forEach { set -> set.id?.let { db.delete(TABLE, it) } }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun ZCRowObject.toSet() = WorkoutSet(
        id              = get("ROWID")?.toString()?.toLongOrNull(),
        sessionId       = get("sessionId")?.toString()?.toLongOrNull() ?: 0L,
        userId          = get("userId")?.toString() ?: "",
        exerciseId      = get("exerciseId")?.toString()?.toLongOrNull(),  // null for REST/CARDIO
        exerciseName    = get("exerciseName")?.toString() ?: "",
        itemType        = get("itemType")?.toString() ?: "EXERCISE",
        orderInSession  = get("orderInSession")?.toString()?.toIntOrNull() ?: 1,
        setNumber       = get("setNumber")?.toString()?.toIntOrNull() ?: 1,
        plannedReps     = get("plannedReps")?.toString()?.toIntOrNull() ?: 0,
        plannedWeightKg = get("plannedWeightKg")?.toString() ?: "0",
        actualReps      = get("actualReps")?.toString()?.toIntOrNull() ?: 0,
        actualWeightKg  = get("actualWeightKg")?.toString() ?: "0",
        durationSeconds = get("durationSeconds")?.toString()?.toIntOrNull() ?: 0,
        distanceKm      = get("distanceKm")?.toString() ?: "0",
        rpe             = get("rpe")?.toString()?.toIntOrNull() ?: 0,
        isPersonalBest  = get("isPersonalBest")?.toString()?.toIntOrNull() ?: 0,
        notes           = get("notes")?.toString() ?: "",
        completedAt     = get("completedAt")?.toString() ?: ""
    )

    private fun WorkoutSet.toMap(): Map<String, Any> = buildMap {
        // Only user-defined columns — CREATORID, CREATEDTIME, MODIFIEDTIME are set by Catalyst automatically
        put("sessionId", sessionId)
        put("userId", userId)
        exerciseId?.let { put("exerciseId", it) }   // Omit for REST/CARDIO — FK col is non-mandatory; 0 is invalid
        put("exerciseName", exerciseName)
        put("itemType", itemType)
        put("orderInSession", orderInSession)
        put("setNumber", setNumber)
        put("plannedReps", plannedReps)
        put("plannedWeightKg", plannedWeightKg)
        put("actualReps", actualReps)
        put("actualWeightKg", actualWeightKg)
        put("durationSeconds", durationSeconds)
        put("distanceKm", distanceKm)
        put("rpe", rpe)
        put("isPersonalBest", isPersonalBest)
        put("notes", notes)
        put("completedAt", completedAt)
    }
}
