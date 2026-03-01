package dev.eknath.GymJournal.modules.metrics

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.BodyMetricEntry
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import dev.eknath.GymJournal.util.ZcqlSanitizer
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
     */
    fun findByDate(userId: String, date: String): List<BodyMetricEntry> =
        db.query(
            "SELECT * FROM $TABLE" +
            " WHERE CREATORID = '${ZcqlSanitizer.sanitize(userId)}'" +
            " AND logDate = '${ZcqlSanitizer.sanitize(date)}'" +
            " ORDER BY metricType ASC"
        ).map { it.toEntry() }

    /**
     * Returns history for a single [metricType] within a date range, sorted ASC.
     * Uses 4 WHERE conditions (CREATORID + metricType + startDate + endDate) —
     * this is at the ZCQL 5-condition limit. Do NOT add a 5th condition here.
     */
    fun findByType(
        userId: String,
        metricType: String,
        startDate: String,
        endDate: String
    ): List<BodyMetricEntry> =
        db.query(
            "SELECT * FROM $TABLE" +
            " WHERE CREATORID = '${ZcqlSanitizer.sanitize(userId)}'" +
            " AND metricType = '${ZcqlSanitizer.sanitize(metricType)}'" +
            " AND logDate >= '${ZcqlSanitizer.sanitize(startDate)}'" +
            " AND logDate <= '${ZcqlSanitizer.sanitize(endDate)}'" +
            " ORDER BY logDate ASC LIMIT 0,300"
        ).map { it.toEntry() }

    /**
     * Returns up to 300 most-recent entries for [userId], ordered by logDate DESC.
     * Used to compute the snapshot in-memory — group by metricType, take first per type.
     */
    fun findRecent(userId: String): List<BodyMetricEntry> =
        db.query(
            "SELECT * FROM $TABLE" +
            " WHERE CREATORID = '${ZcqlSanitizer.sanitize(userId)}'" +
            " ORDER BY logDate DESC LIMIT 0,300"
        ).map { it.toEntry() }

    /**
     * Returns all entries for [userId] matching [metricType].
     * Used to cascade-delete all entries when a custom metric definition is removed.
     */
    fun findAllByType(userId: String, metricType: String): List<BodyMetricEntry> =
        db.query(
            "SELECT * FROM $TABLE" +
            " WHERE CREATORID = '${ZcqlSanitizer.sanitize(userId)}'" +
            " AND metricType = '${ZcqlSanitizer.sanitize(metricType)}'" +
            " LIMIT 0,300"
        ).map { it.toEntry() }

    fun findById(id: Long): BodyMetricEntry? =
        db.queryOne("SELECT * FROM $TABLE WHERE ROWID = $id")?.toEntry()

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
        createdBy  = get("CREATORID")?.toString() ?: "",
        createdAt  = get("CREATEDTIME")?.toString() ?: "",
        updatedAt  = get("MODIFIEDTIME")?.toString() ?: ""
    )

    private fun BodyMetricEntry.toMap(): Map<String, Any> = buildMap {
        // Only user-defined columns — CREATORID, CREATEDTIME, MODIFIEDTIME are set by Catalyst automatically
        put("metricType", metricType)
        put("value", value)
        put("unit", unit)
        put("logDate", logDate)
        put("notes", notes)
    }
}
