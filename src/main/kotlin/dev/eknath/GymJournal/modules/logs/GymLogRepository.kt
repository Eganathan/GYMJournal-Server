package dev.eknath.GymJournal.modules.logs

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.GymLog
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import org.springframework.stereotype.Repository

private const val TABLE = "GymLogs"

@Repository
class GymLogRepository(private val db: CatalystDataStoreRepository) {

    /** All logs for [userId] on [date] (YYYY-MM-DD), sorted by createdAt ASC. */
    fun findByDate(userId: Long, date: String): List<GymLog> =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,300")
            .map { it.toLog() }
            .filter { it.logDate == date }
            .sortedBy { it.createdAt }

    /** Most recent logs for [userId] across all dates, up to [limit] entries. */
    fun findRecent(userId: Long, limit: Int = 50): List<GymLog> =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,300")
            .map { it.toLog() }
            .sortedByDescending { it.logDate + it.createdAt }
            .take(limit)

    fun findById(id: Long): GymLog? = db.getRow(TABLE, id)?.toLog()

    fun save(log: GymLog): GymLog {
        val row = db.insert(TABLE, log.toMap())
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        return if (rowId != null) findById(rowId) ?: log.copy(id = rowId) else log
    }

    fun update(id: Long, log: GymLog) = db.update(TABLE, id, log.toMap())

    fun delete(id: Long) = db.delete(TABLE, id)

    // --- Mappers ---

    private fun ZCRowObject.toLog() = GymLog(
        id          = get("ROWID")?.toString()?.toLongOrNull(),
        userId      = get("USER_ID")?.toString()?.toLongOrNull() ?: 0L,
        logDate     = get("logDate")?.toString() ?: "",
        type        = get("type")?.toString() ?: "",
        title       = get("title")?.toString() ?: "",
        description = get("description")?.toString() ?: "",
        severity    = get("severity")?.toString() ?: "",
        createdAt   = get("CREATEDTIME")?.toString() ?: ""
    )

    private fun GymLog.toMap(): Map<String, Any> = buildMap {
        put("USER_ID", userId)
        put("logDate", logDate)
        put("type", type)
        put("title", title)
        put("description", description)
        put("severity", severity)
    }
}
