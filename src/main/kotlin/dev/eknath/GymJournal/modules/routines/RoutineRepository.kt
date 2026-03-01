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
     * Returns routines the calling user can see:
     *   - Public routines (`isPublic = 1`) when [onlyMine] is false
     *   - Only the user's own routines when [onlyMine] is true
     *
     * In-memory name substring search is applied after the ZCQL fetch because
     * ZCQL does not support reliable full-text search.
     */
    fun findAll(
        callingUserId: String,
        onlyMine: Boolean
    ): List<Routine> {
        val where = if (onlyMine) {
            " WHERE CREATORID = '${ZcqlSanitizer.sanitize(callingUserId)}'"
        } else {
            " WHERE isPublic = 1"
        }
        return db.query("SELECT * FROM $TABLE$where ORDER BY MODIFIEDTIME DESC").map { it.toRoutine() }
    }

    fun findById(id: Long): Routine? =
        db.queryOne("SELECT * FROM $TABLE WHERE ROWID = $id")?.toRoutine()

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
        // Catalyst system columns — never written in toMap()
        createdBy        = get("CREATORID")?.toString() ?: "",
        createdAt        = get("CREATEDTIME")?.toString() ?: "",
        updatedAt        = get("MODIFIEDTIME")?.toString() ?: ""
    )

    private fun Routine.toMap(): Map<String, Any> = buildMap {
        // Only user-defined columns — CREATORID, CREATEDTIME, MODIFIEDTIME are set by Catalyst automatically
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
}
