package dev.eknath.GymJournal.modules.workouts

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.WorkoutSession
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import org.springframework.stereotype.Repository

private const val TABLE = "WorkoutSessions"

@Repository
class WorkoutSessionRepository(
    private val db: CatalystDataStoreRepository
) {

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Looks up a session by ROWID using ZCObject.getRow (reliable ZCObject path).
     * Returns null if the row doesn't exist (ZCObject returns an empty row for non-existent
     * ROWIDs — takeIf filters those out via blank userId check).
     *
     * Previously we observed FORBIDDEN errors due to JS BigInt precision loss: the client
     * was sending a rounded ID (e.g. 11585000000717124 instead of 11585000000717125).
     * That root cause is now fixed — all response IDs are serialised as JSON strings.
     */
    fun findById(id: Long): WorkoutSession? =
        db.getRow(TABLE, id)?.toSession()?.takeIf { it.userId != 0L }

    /**
     * Fetches sessions for [userId] using a ZCQL BigInt WHERE — reliable because USER_ID is BigInt.
     * Status and date-range filters are applied in-memory (string columns, unreliable in ZCQL).
     */
    fun findByUser(
        userId: Long,
        status: String?,
        startDate: String?,
        endDate: String?,
        offset: Int,
        limit: Int
    ): List<WorkoutSession> =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId ORDER BY startedAt DESC LIMIT 0,300")
            .map { it.toSession() }
            .filter { matchesStringFilters(it, status, startDate, endDate) }
            .drop(offset)
            .take(limit)

    fun countByUser(
        userId: Long,
        status: String?,
        startDate: String?,
        endDate: String?
    ): Long =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,300")
            .map { it.toSession() }
            .count { matchesStringFilters(it, status, startDate, endDate) }
            .toLong()

    private fun matchesStringFilters(
        s: WorkoutSession,
        status: String?,
        startDate: String?,
        endDate: String?
    ): Boolean {
        if (status != null && s.status != status) return false
        if (startDate != null && s.startedAt.isNotBlank() && s.startedAt.take(10) < startDate) return false
        if (endDate   != null && s.startedAt.isNotBlank() && s.startedAt.take(10) > endDate)   return false
        return true
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
        userId      = get("USER_ID")?.toString()?.toLongOrNull() ?: 0L,
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
        put("USER_ID", userId)
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

}
