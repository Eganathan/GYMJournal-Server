package dev.eknath.GymJournal.modules.hydration

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.WaterIntakeEntry
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import dev.eknath.GymJournal.util.ZcqlSanitizer
import org.springframework.stereotype.Repository

private const val TABLE = "WaterIntakeLogs"

@Repository
class WaterIntakeRepository(private val db: CatalystDataStoreRepository) {

    fun findEntriesForDate(userId: String, date: String): List<WaterIntakeEntry> =
        db.query(
            "SELECT * FROM $TABLE " +
            "WHERE userId = '${ZcqlSanitizer.sanitize(userId)}' " +
            "AND logDateTime LIKE '${ZcqlSanitizer.sanitize(date)}%' " +
            "ORDER BY logDateTime ASC"
        ).map { it.toEntry() }

    fun findEntriesForDateRange(userId: String, startDate: String, endDate: String): List<WaterIntakeEntry> =
        db.query(
            "SELECT * FROM $TABLE " +
            "WHERE userId = '${ZcqlSanitizer.sanitize(userId)}' " +
            "AND logDateTime >= '${ZcqlSanitizer.sanitize(startDate)}' " +
            "AND logDateTime <= '${ZcqlSanitizer.sanitize(endDate)}T23:59:59' " +
            "ORDER BY logDateTime ASC"
        ).map { it.toEntry() }

    fun findById(id: Long): WaterIntakeEntry? =
        db.queryOne("SELECT * FROM $TABLE WHERE ROWID = $id")?.toEntry()

    fun save(entry: WaterIntakeEntry): WaterIntakeEntry {
        val row = db.insert(TABLE, entry.toMap())
        return entry.copy(id = row.get("ROWID")?.toString()?.toLongOrNull())
    }

    fun update(id: Long, entry: WaterIntakeEntry) =
        db.update(TABLE, id, entry.toMap())

    fun delete(id: Long) = db.delete(TABLE, id)

    // --- Mappers ---

    private fun ZCRowObject.toEntry() = WaterIntakeEntry(
        id = this.get("ROWID")?.toString()?.toLongOrNull(),
        userId = this.get("userId")?.toString() ?: "",
        logDateTime = this.get("logDateTime")?.toString() ?: "",
        amountMl = this.get("amountMl")?.toString()?.toIntOrNull() ?: 0,
        notes = this.get("notes")?.toString() ?: ""
    )

    private fun WaterIntakeEntry.toMap(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("logDateTime", logDateTime)
        put("amountMl", amountMl)
        put("notes", notes)
    }
}
