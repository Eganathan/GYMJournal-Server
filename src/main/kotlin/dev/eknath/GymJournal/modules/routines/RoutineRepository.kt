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
     * Fetches the calling user's own routines via ZCQL BigInt WHERE (reliable).
     * USER_ID is BigInt so numeric comparison works correctly in AppSail.
     */
    private fun fetchForUser(userId: Long): List<Routine> =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId ORDER BY MODIFIEDTIME DESC LIMIT 0,300")
            .map { it.toRoutine() }

    /**
     * Fetches all public routines via ZCQL numeric WHERE on isPublic (BigInt column, reliable).
     */
    private fun fetchPublic(): List<Routine> =
        db.query("SELECT * FROM $TABLE WHERE isPublic = 1 ORDER BY MODIFIEDTIME DESC LIMIT 0,300")
            .map { it.toRoutine() }

    fun findPaged(callingUserId: Long, onlyMine: Boolean, offset: Int, limit: Int): List<Routine> =
        (if (onlyMine) fetchForUser(callingUserId) else fetchPublic())
            .drop(offset)
            .take(limit)

    fun findAll(callingUserId: Long, onlyMine: Boolean): List<Routine> =
        if (onlyMine) fetchForUser(callingUserId) else fetchPublic()

    fun count(callingUserId: Long, onlyMine: Boolean): Long =
        (if (onlyMine) fetchForUser(callingUserId) else fetchPublic()).size.toLong()

    /** Looks up a routine by ROWID. Returns null if not found or row is blank (non-existent ROWID). */
    fun findById(id: Long): Routine? =
        db.getRow(TABLE, id)?.toRoutine()?.takeIf { it.createdBy != 0L }

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
        createdBy        = get("USER_ID")?.toString()?.toLongOrNull() ?: 0L,
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
        put("USER_ID", createdBy)
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
