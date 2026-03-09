package dev.eknath.GymJournal.modules.metrics

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.BodyMetricEntry
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import org.springframework.stereotype.Repository

private const val TABLE = "BodyMetricEntries"

@Repository
class BodyMetricRepository(private val db: CatalystDataStoreRepository) {

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /**
     * Returns all entries logged by [userId] on [date] (YYYY-MM-DD),
     * sorted by metricType ASC. Used to pre-fill the log form on revisit.
     *
     * Fetches all rows without a WHERE clause and filters in-memory.
     * ZCQL WHERE on user-created Var Char columns (userId) is unreliable
     * in AppSail — the filter is silently ignored, returning all users' data.
     */
    fun findByDate(userId: Long, date: String): List<BodyMetricEntry> =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,300")
            .map { it.toEntry() }
            .filter { it.logDate == date }
            .sortedBy { it.metricType }

    fun findByType(
        userId: Long,
        metricType: String,
        startDate: String,
        endDate: String
    ): List<BodyMetricEntry> =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,300")
            .map { it.toEntry() }
            .filter { it.metricType == metricType && it.logDate >= startDate && it.logDate <= endDate }
            .sortedBy { it.logDate }
            .take(300)

    fun findRecent(userId: Long): List<BodyMetricEntry> =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,300")
            .map { it.toEntry() }
            .sortedByDescending { it.logDate }
            .take(300)

    /**
     * Pages through all entries for [userId] matching [metricType].
     * USER_ID is BigInt so the ZCQL WHERE is reliable; metricType is filtered in-memory.
     * Used to cascade-delete all entries when a custom metric definition is removed.
     */
    fun findAllByType(userId: Long, metricType: String): List<BodyMetricEntry> {
        val all = mutableListOf<BodyMetricEntry>()
        var offset = 0
        while (true) {
            val batch = db.query(
                "SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT $offset,300"
            ).map { it.toEntry() }
            all.addAll(batch.filter { it.metricType == metricType })
            if (batch.size < 300) break
            offset += 300
        }
        return all
    }

    fun findById(id: Long): BodyMetricEntry? =
        db.getRow(TABLE, id)?.toEntry()

    // ---------------------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------------------

    fun save(entry: BodyMetricEntry): BodyMetricEntry {
        val row = db.insert(TABLE, entry.toMap())
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        return if (rowId != null) findById(rowId) ?: entry.copy(id = rowId) else entry
    }

    fun update(id: Long, entry: BodyMetricEntry) = db.update(TABLE, id, entry.toMap())

    fun delete(id: Long) = db.delete(TABLE, id)

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    // ---------------------------------------------------------------------------
    // Mappers
    // ---------------------------------------------------------------------------

    private fun ZCRowObject.toEntry() = BodyMetricEntry(
        id         = get("ROWID")?.toString()?.toLongOrNull(),
        metricType = get("metricType")?.toString() ?: "",
        value      = get("value")?.toString()?.toDoubleOrNull() ?: 0.0,
        unit       = get("unit")?.toString() ?: "",
        logDate    = get("logDate")?.toString() ?: "",
        notes      = get("notes")?.toString() ?: "",
        // Catalyst system columns — read-only
        createdBy  = get("USER_ID")?.toString()?.toLongOrNull() ?: 0L,
        createdAt  = get("CREATEDTIME")?.toString() ?: "",
        updatedAt  = get("MODIFIEDTIME")?.toString() ?: ""
    )

    private fun BodyMetricEntry.toMap(): Map<String, Any> = buildMap {
        // userId stored explicitly — CREATORID is unreliable in AppSail (app credentials, not user)
        // CREATEDTIME, MODIFIEDTIME still auto-set by Catalyst
        put("USER_ID", createdBy)
        put("metricType", metricType)
        put("value", value)
        put("unit", unit)
        put("logDate", logDate)
        put("notes", notes)
    }
}
