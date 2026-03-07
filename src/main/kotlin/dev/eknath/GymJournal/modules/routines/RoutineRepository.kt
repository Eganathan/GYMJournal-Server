package dev.eknath.GymJournal.modules.routines

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.Routine
import dev.eknath.GymJournal.model.domain.RoutineItem
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import org.springframework.stereotype.Repository

private const val TABLE = "Routines"

@Repository
class RoutineRepository(
    private val db: CatalystDataStoreRepository,
    private val mapper: ObjectMapper
) {

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Fetches ALL rows without a userId WHERE clause and filters in-memory.
     *
     * IMPORTANT: ZCQL WHERE conditions on user-created Var Char columns (like `userId`)
     * silently return 0 results in AppSail, even when matching rows exist.
     * Only ZCObject (getRow / insert / update / delete) is reliable for per-row access.
     * All user-based filtering must therefore happen in-memory after a full table fetch.
     *
     * Results are capped at 300 rows by ZCQL — this is a hard Catalyst DataStore limit.
     */
    private fun fetchAllRows(): List<Routine> =
        db.query("SELECT * FROM $TABLE ORDER BY MODIFIEDTIME DESC LIMIT 0,300")
            .map { it.toRoutine() }

    private fun matchesVisibility(routine: Routine, callingUserId: String, onlyMine: Boolean): Boolean =
        if (onlyMine) routine.createdBy == callingUserId else routine.isPublic == 1

    /**
     * DB-level paginated fetch — used when no search term is provided.
     * Fetches all visible rows (in-memory filter) then slices with [offset]/[limit].
     */
    fun findPaged(callingUserId: String, onlyMine: Boolean, offset: Int, limit: Int): List<Routine> =
        fetchAllRows()
            .filter { matchesVisibility(it, callingUserId, onlyMine) }
            .drop(offset)
            .take(limit)

    /**
     * Full fetch — used when a search term is provided so that
     * in-memory substring filtering runs across the entire visible dataset.
     * Results are capped at 300 by the ZCQL limit.
     */
    fun findAll(callingUserId: String, onlyMine: Boolean): List<Routine> =
        fetchAllRows().filter { matchesVisibility(it, callingUserId, onlyMine) }

    /**
     * Total count for the same visibility filter — used to populate [ApiMeta.total].
     * In-memory count from the full fetch (ZCQL COUNT with WHERE is also unreliable).
     */
    fun count(callingUserId: String, onlyMine: Boolean): Long =
        fetchAllRows().count { matchesVisibility(it, callingUserId, onlyMine) }.toLong()

    /** Looks up a routine by ROWID. Returns null if not found or row is blank (non-existent ROWID). */
    fun findById(id: Long): Routine? =
        db.getRow(TABLE, id)?.toRoutine()?.takeIf { it.createdBy.isNotBlank() }

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
        // Use explicit userId column; CREATORID is unreliable in AppSail (set to app credentials, not user)
        createdBy        = get("userId")?.toString() ?: "",
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
        put("userId", createdBy)   // explicit ownership column — CREATORID is app credentials in AppSail
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

}
