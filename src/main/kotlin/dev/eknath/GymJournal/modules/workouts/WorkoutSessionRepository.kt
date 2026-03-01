package dev.eknath.GymJournal.modules.workouts

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.WorkoutSession
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import dev.eknath.GymJournal.util.ZcqlSanitizer
import org.springframework.stereotype.Repository

private const val TABLE = "WorkoutSessions"

@Repository
class WorkoutSessionRepository(
    private val db: CatalystDataStoreRepository
) {

    // ── Queries ───────────────────────────────────────────────────────────────

    fun findById(id: Long): WorkoutSession? =
        db.queryOne("SELECT * FROM $TABLE WHERE ROWID = $id")?.toSession()

    /**
     * Returns the calling user's sessions, most recent first.
     * Optionally filtered by [status] ("IN_PROGRESS" or "COMPLETED").
     */
    fun findByUser(userId: String, status: String?): List<WorkoutSession> {
        val conditions = mutableListOf<String>()
        conditions.add("userId = '${ZcqlSanitizer.sanitize(userId)}'")
        status?.let {
            conditions.add("status = '${ZcqlSanitizer.sanitize(it)}'")
        }
        val where = " WHERE ${conditions.joinToString(" AND ")}"
        return db.query("SELECT * FROM $TABLE$where ORDER BY startedAt DESC").map { it.toSession() }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun save(session: WorkoutSession): WorkoutSession {
        val row = db.insert(TABLE, session.toMap())
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        return if (rowId != null) findById(rowId) ?: session.copy(id = rowId) else session
    }

    fun update(id: Long, session: WorkoutSession) =
        db.update(TABLE, id, session.toMap())

    fun delete(id: Long) = db.delete(TABLE, id)

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun ZCRowObject.toSession() = WorkoutSession(
        id           = get("ROWID")?.toString()?.toLongOrNull(),
        userId       = get("userId")?.toString() ?: "",
        routineId    = get("routineId")?.toString()?.toLongOrNull() ?: 0L,
        routineName  = get("routineName")?.toString() ?: "",
        name         = get("name")?.toString() ?: "",
        status       = get("status")?.toString() ?: "IN_PROGRESS",
        startedAt    = get("startedAt")?.toString() ?: "",
        completedAt  = get("completedAt")?.toString() ?: "",
        notes        = get("notes")?.toString() ?: "",
        createdAt    = get("CREATEDTIME")?.toString() ?: "",
        updatedAt    = get("MODIFIEDTIME")?.toString() ?: ""
    )

    private fun WorkoutSession.toMap(): Map<String, Any> = buildMap {
        // Only user-defined columns — CREATORID, CREATEDTIME, MODIFIEDTIME are set by Catalyst automatically
        put("userId", userId)
        put("routineId", routineId)
        put("routineName", routineName)
        put("name", name)
        put("status", status)
        put("startedAt", startedAt)
        // completedAt is a DateTime column — omit entirely when blank (Catalyst rejects empty strings for DateTime)
        if (completedAt.isNotBlank()) put("completedAt", completedAt)
        put("notes", notes)
    }
}
