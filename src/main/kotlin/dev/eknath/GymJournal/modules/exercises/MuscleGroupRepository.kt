package dev.eknath.GymJournal.modules.exercises

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.MuscleGroup
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import dev.eknath.GymJournal.util.ZcqlSanitizer
import org.springframework.stereotype.Repository

private const val TABLE = "MuscleGroups"

@Repository
class MuscleGroupRepository(private val db: CatalystDataStoreRepository) {

    fun findAll(): List<MuscleGroup> =
        db.query("SELECT * FROM $TABLE ORDER BY displayName ASC").map { it.toMuscleGroup() }

    fun findById(id: Long): MuscleGroup? =
        db.getRow(TABLE, id)?.toMuscleGroup()

    fun save(group: MuscleGroup): MuscleGroup {
        val row = db.insert(TABLE, group.toMap())
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        return if (rowId != null)
            db.getRow(TABLE, rowId)?.toMuscleGroup() ?: group.copy(id = rowId)
        else group
    }

    fun findByDisplayName(name: String): MuscleGroup? =
        db.queryOne("SELECT * FROM $TABLE WHERE displayName = '${ZcqlSanitizer.sanitize(name)}'")?.toMuscleGroup()

    fun count(): Long = db.count(TABLE)

    // --- Mappers ---

    private fun ZCRowObject.toMuscleGroup() = MuscleGroup(
        id          = get("ROWID")?.toString()?.toLongOrNull(),
        displayName = get("displayName")?.toString() ?: "",
        shortName   = get("shortName")?.toString() ?: "",
        description = get("description")?.toString() ?: "",
        bodyRegion  = get("bodyRegion")?.toString() ?: "",
        imageUrl    = get("imageUrl")?.toString()?.takeIf { it.isNotBlank() }
    )

    private fun MuscleGroup.toMap(): Map<String, Any> = buildMap {
        put("displayName", displayName)
        put("shortName", shortName)
        put("description", description)
        put("bodyRegion", bodyRegion)
        put("imageUrl", imageUrl ?: "")
    }
}
