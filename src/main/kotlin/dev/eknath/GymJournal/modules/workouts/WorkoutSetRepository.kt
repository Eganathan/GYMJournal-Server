package dev.eknath.GymJournal.modules.workouts

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.WorkoutSet
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import dev.eknath.GymJournal.util.ZcqlSanitizer
import org.springframework.stereotype.Repository

private const val TABLE = "WorkoutSets"

@Repository
class WorkoutSetRepository(
    private val db: CatalystDataStoreRepository
) {

    // ── Queries ───────────────────────────────────────────────────────────────

    fun findById(id: Long): WorkoutSet? =
        db.queryOne("SELECT * FROM $TABLE WHERE ROWID = $id")?.toSet()

    /**
     * All sets belonging to a session, ordered by slot then set number.
     * This is the primary query for building the full session response.
     */
    fun findBySession(sessionId: Long, userId: String): List<WorkoutSet> {
        val sid = sessionId
        val uid = ZcqlSanitizer.sanitize(userId)
        return db.query(
            "SELECT * FROM $TABLE WHERE sessionId = $sid AND userId = '$uid'" +
            " ORDER BY orderInSession ASC"
        ).map { it.toSet() }
    }

    /**
     * All EXERCISE sets for a given exercise belonging to this user.
     * Used for history and personal best queries.
     * Only returns sets where completedAt is not empty (i.e., actually completed).
     */
    fun findExerciseHistory(userId: String, exerciseId: Long): List<WorkoutSet> {
        val uid = ZcqlSanitizer.sanitize(userId)
        return db.query(
            "SELECT * FROM $TABLE" +
            " WHERE userId = '$uid' AND exerciseId = $exerciseId AND itemType = 'EXERCISE'" +
            " ORDER BY completedAt DESC LIMIT 0,300"
        ).map { it.toSet() }.filter { it.completedAt.isNotBlank() }
    }

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
        exerciseId      = get("exerciseId")?.toString()?.toLongOrNull() ?: 0L,
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
        put("exerciseId", exerciseId)
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
