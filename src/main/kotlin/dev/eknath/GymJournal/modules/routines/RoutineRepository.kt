package dev.eknath.GymJournal.modules.routines

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.Routine
import dev.eknath.GymJournal.model.domain.RoutineItem
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import dev.eknath.GymJournal.util.ZcqlSanitizer
import org.springframework.stereotype.Repository

private const val TABLE = "Routines"

@Repository
class RoutineRepository(
    private val db: CatalystDataStoreRepository,
    private val mapper: ObjectMapper
) {

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * DB-level paginated fetch — used when no search term is provided.
     * [offset] = (page - 1) * pageSize, [limit] = pageSize.
     */
    fun findPaged(callingUserId: String, onlyMine: Boolean, offset: Int, limit: Int): List<Routine> {
        val where = buildWhereClause(callingUserId, onlyMine)
        return db.query(
            "SELECT * FROM $TABLE$where ORDER BY MODIFIEDTIME DESC LIMIT $offset,$limit"
        ).map { it.toRoutine() }
    }

    /**
     * Full fetch (up to 300) — used when a search term is provided so that
     * in-memory substring filtering runs across the entire visible dataset.
     * Note: results are capped at 300 by the ZCQL limit.
     */
    fun findAll(callingUserId: String, onlyMine: Boolean): List<Routine> {
        val where = buildWhereClause(callingUserId, onlyMine)
        return db.query(
            "SELECT * FROM $TABLE$where ORDER BY MODIFIEDTIME DESC LIMIT 0,300"
        ).map { it.toRoutine() }
    }

    /**
     * Total count for the same visibility filter — used to populate [ApiMeta.total]
     * in paginated (non-search) list responses.
     */
    fun count(callingUserId: String, onlyMine: Boolean): Long =
        db.count(TABLE, buildCondition(callingUserId, onlyMine))

    fun findById(id: Long): Routine? =
        db.getRow(TABLE, id)?.toRoutine()

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun save(routine: Routine): Routine {
        val row = db.insert(TABLE, routine.toMap())
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        return if (rowId != null) findById(rowId) ?: routine.copy(id = rowId) else routine
    }

    fun update(id: Long, routine: Routine) =
        db.update(TABLE, id, routine.toMap())

    fun delete(id: Long) = db.delete(TABLE, id)

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun ZCRowObject.toRoutine() = Routine(
        id               = get("ROWID")?.toString()?.toLongOrNull(),
        name             = get("name")?.toString() ?: "",
        description      = get("description")?.toString() ?: "",
        items            = get("items")?.toString().toRoutineItems(),
        estimatedMinutes = get("estimatedMinutes")?.toString()?.toIntOrNull() ?: 0,
        tags             = get("tags")?.toString().toStringList(),
        isPublic         = get("isPublic")?.toString()?.toIntOrNull() ?: 0,
        createdBy        = get("CREATORID")?.toString() ?: "",
        createdAt        = get("CREATEDTIME")?.toString() ?: "",
        updatedAt        = get("MODIFIEDTIME")?.toString() ?: ""
    )

    private fun Routine.toMap(): Map<String, Any> = buildMap {
        put("name", name)
        put("description", description)
        put("items", items.toJson())
        put("estimatedMinutes", estimatedMinutes)
        put("tags", tags.toJson())
        put("isPublic", isPublic)
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    @JvmName("routineItemsToJson")
    private fun List<RoutineItem>.toJson(): String = mapper.writeValueAsString(this)

    @JvmName("stringsToJson")
    private fun List<String>.toJson(): String = mapper.writeValueAsString(this)

    private fun String?.toRoutineItems(): List<RoutineItem> {
        if (isNullOrBlank() || this == "null") return emptyList()
        return try {
            mapper.readValue(this, object : TypeReference<List<RoutineItem>>() {})
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun String?.toStringList(): List<String> {
        if (isNullOrBlank() || this == "null") return emptyList()
        return try {
            mapper.readValue(this, object : TypeReference<List<String>>() {})
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildWhereClause(callingUserId: String, onlyMine: Boolean) =
        " WHERE ${buildCondition(callingUserId, onlyMine)}"

    private fun buildCondition(callingUserId: String, onlyMine: Boolean) =
        if (onlyMine) "CREATORID = '${ZcqlSanitizer.sanitize(callingUserId)}'"
        else "isPublic = 1"
}
