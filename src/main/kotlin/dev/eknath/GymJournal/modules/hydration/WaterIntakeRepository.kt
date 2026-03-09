package dev.eknath.GymJournal.modules.hydration

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.WaterIntakeEntry
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import org.springframework.stereotype.Repository

private const val TABLE = "WaterIntakeLogs"

@Repository
class WaterIntakeRepository(private val db: CatalystDataStoreRepository) {

    /**
     * Returns all water intake entries for [userId] on [date] (YYYY-MM-DD),
     * sorted by logDateTime ASC.
     *
     * Fetches all rows without a WHERE clause and filters in-memory.
     * ZCQL WHERE on user-created Var Char columns (userId) is unreliable
     * in AppSail — the filter is silently ignored, returning all users' data.
     */
    fun findEntriesForDate(userId: Long, date: String): List<WaterIntakeEntry> =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,300")
            .map { it.toEntry() }
            .filter { it.createdAt.startsWith(date) }
            .sortedBy { it.createdAt }

    fun findEntriesForDateRange(userId: Long, startDate: String, endDate: String): List<WaterIntakeEntry> =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,300")
            .map { it.toEntry() }
            .filter {
                it.createdAt.length >= 10 &&
                it.createdAt.substring(0, 10) >= startDate &&
                it.createdAt.substring(0, 10) <= endDate
            }
            .sortedBy { it.createdAt }

    fun findById(id: Long): WaterIntakeEntry? =
        db.getRow(TABLE, id)?.toEntry()

    fun save(entry: WaterIntakeEntry): WaterIntakeEntry {
        val row = db.insert(TABLE, entry.toMap())
        // Catalyst SDK may return ROWID under either "ROWID" or the prefixed key "TableName.ROWID"
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        // Read back the authoritative row so the response reflects what is actually stored
        return if (rowId != null) findById(rowId) ?: entry.copy(id = rowId) else entry
    }

    fun update(id: Long, entry: WaterIntakeEntry) =
        db.update(TABLE, id, entry.toMap())

    fun delete(id: Long) = db.delete(TABLE, id)

    // --- Mappers ---

    private fun ZCRowObject.toEntry() = WaterIntakeEntry(
        id        = this.get("ROWID")?.toString()?.toLongOrNull(),
        userId    = this.get("USER_ID")?.toString()?.toLongOrNull() ?: 0L,
        amountMl  = this.get("amountMl")?.toString()?.toIntOrNull() ?: 0,
        notes     = this.get("notes")?.toString() ?: "",
        createdAt = this.get("CREATEDTIME")?.toString() ?: ""
    )

    private fun WaterIntakeEntry.toMap(): Map<String, Any> = buildMap {
        put("USER_ID", userId)
        put("amountMl", amountMl)
        put("notes", notes)
        // CREATEDTIME is set automatically by Catalyst on insert — never written explicitly
    }
}
