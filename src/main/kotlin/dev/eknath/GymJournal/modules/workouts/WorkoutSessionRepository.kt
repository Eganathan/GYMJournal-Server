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
     * Returns the calling user's sessions, ordered by [startedAt] DESC (actual workout
     * time, not insert time — correctly places backdated sessions in chronological order).
     *
     * Filters:
     *   [status]    — optional; "IN_PROGRESS" or "COMPLETED"
     *   [startDate] — optional; YYYY-MM-DD; sessions with startedAt on or after this date
     *   [endDate]   — optional; YYYY-MM-DD; sessions with startedAt on or before this date
     *
     * Pagination is applied at the ZCQL level via LIMIT [offset],[limit].
     * Max 4 WHERE conditions (userId + status + startDate + endDate) — within ZCQL limit of 5.
     */
    fun findByUser(
        userId: String,
        status: String?,
        startDate: String?,
        endDate: String?,
        offset: Int,
        limit: Int
    ): List<WorkoutSession> {
        val where = buildWhereClause(userId, status, startDate, endDate)
        return db.query(
            "SELECT * FROM $TABLE$where ORDER BY startedAt DESC LIMIT $offset,$limit"
        ).map { it.toSession() }
    }

    /**
     * Returns the total count matching the same filters as [findByUser].
     * Used to compute the [ApiMeta.total] field for paginated list responses.
     */
    fun countByUser(
        userId: String,
        status: String?,
        startDate: String?,
        endDate: String?
    ): Long {
        val condition = buildCondition(userId, status, startDate, endDate)
        return db.count(TABLE, condition)
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
        id          = get("ROWID")?.toString()?.toLongOrNull(),
        userId      = get("userId")?.toString() ?: "",
        routineId   = get("routineId")?.toString()?.toLongOrNull() ?: 0L,
        routineName = get("routineName")?.toString() ?: "",
        name        = get("name")?.toString() ?: "",
        status      = get("status")?.toString() ?: "IN_PROGRESS",
        startedAt   = get("startedAt")?.toString() ?: "",
        completedAt = get("completedAt")?.toString() ?: "",
        notes       = get("notes")?.toString() ?: "",
        createdAt   = get("CREATEDTIME")?.toString() ?: "",
        updatedAt   = get("MODIFIEDTIME")?.toString() ?: ""
    )

    private fun WorkoutSession.toMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("routineId", routineId)
        put("routineName", routineName)
        put("name", name)
        put("status", status)
        put("startedAt", startedAt)
        // completedAt is a DateTime column — omit entirely when blank
        // (Catalyst rejects empty strings for DateTime columns)
        if (completedAt.isNotBlank()) put("completedAt", completedAt)
        put("notes", notes)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Builds the " WHERE ..." clause for both findByUser and countByUser. */
    private fun buildWhereClause(
        userId: String,
        status: String?,
        startDate: String?,
        endDate: String?
    ): String = " WHERE ${buildCondition(userId, status, startDate, endDate)}"

    private fun buildCondition(
        userId: String,
        status: String?,
        startDate: String?,
        endDate: String?
    ): String {
        val conditions = mutableListOf<String>()
        conditions.add("userId = '${ZcqlSanitizer.sanitize(userId)}'")
        status?.let    { conditions.add("status = '${ZcqlSanitizer.sanitize(it)}'") }
        // startedAt is a DateTime column — compare using full datetime string
        startDate?.let { conditions.add("startedAt >= '${ZcqlSanitizer.sanitize(it)} 00:00:00'") }
        endDate?.let   { conditions.add("startedAt <= '${ZcqlSanitizer.sanitize(it)} 23:59:59'") }
        return conditions.joinToString(" AND ")
    }
}
