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
        db.queryOne("SELECT * FROM $TABLE WHERE ROWID = $id")?.toMuscleGroup()

    fun findBySlug(slug: String): MuscleGroup? =
        db.queryOne(
            "SELECT * FROM $TABLE WHERE slug = '${ZcqlSanitizer.sanitize(slug)}'"
        )?.toMuscleGroup()

    fun existsBySlug(slug: String): Boolean = findBySlug(slug) != null

    fun save(group: MuscleGroup): MuscleGroup {
        val row = db.insert(TABLE, group.toMap())
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        return if (rowId != null)
            db.queryOne("SELECT * FROM $TABLE WHERE ROWID = $rowId")?.toMuscleGroup()
                ?: group.copy(id = rowId)
        else group
    }

    fun count(): Long = db.count(TABLE)

    // --- Mappers ---

    private fun ZCRowObject.toMuscleGroup() = MuscleGroup(
        id          = get("ROWID")?.toString()?.toLongOrNull(),
        slug        = get("slug")?.toString() ?: "",
        displayName = get("displayName")?.toString() ?: "",
        shortName   = get("shortName")?.toString() ?: "",
        description = get("description")?.toString() ?: "",
        bodyRegion  = get("bodyRegion")?.toString() ?: "",
        imageUrl    = get("imageUrl")?.toString()?.takeIf { it.isNotBlank() }
    )

    private fun MuscleGroup.toMap(): Map<String, Any> = buildMap {
        put("slug", slug)
        put("displayName", displayName)
        put("shortName", shortName)
        put("description", description)
        put("bodyRegion", bodyRegion)
        put("imageUrl", imageUrl ?: "")
    }
}
