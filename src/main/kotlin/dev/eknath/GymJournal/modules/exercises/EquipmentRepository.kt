package dev.eknath.GymJournal.modules.exercises

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.Equipment
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import dev.eknath.GymJournal.util.ZcqlSanitizer
import org.springframework.stereotype.Repository

private const val TABLE = "Equipment"

@Repository
class EquipmentRepository(private val db: CatalystDataStoreRepository) {

    fun findAll(): List<Equipment> =
        db.query("SELECT * FROM $TABLE ORDER BY displayName ASC").map { it.toEquipment() }

    fun findById(id: Long): Equipment? =
        db.getRow(TABLE, id)?.toEquipment()

    fun save(equipment: Equipment): Equipment {
        val row = db.insert(TABLE, equipment.toMap())
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        return if (rowId != null)
            db.getRow(TABLE, rowId)?.toEquipment() ?: equipment.copy(id = rowId)
        else equipment
    }

    fun findByDisplayName(name: String): Equipment? =
        db.queryOne("SELECT * FROM $TABLE WHERE displayName = '${ZcqlSanitizer.sanitize(name)}'")?.toEquipment()

    fun count(): Long = db.count(TABLE)

    // --- Mappers ---

    private fun ZCRowObject.toEquipment() = Equipment(
        id          = get("ROWID")?.toString()?.toLongOrNull(),
        displayName = get("displayName")?.toString() ?: "",
        description = get("description")?.toString() ?: "",
        category    = get("category")?.toString() ?: "",
        imageUrl    = get("imageUrl")?.toString()?.takeIf { it.isNotBlank() }
    )

    private fun Equipment.toMap(): Map<String, Any> = buildMap {
        put("displayName", displayName)
        put("description", description)
        put("category", category)
        put("imageUrl", imageUrl ?: "")
    }
}
