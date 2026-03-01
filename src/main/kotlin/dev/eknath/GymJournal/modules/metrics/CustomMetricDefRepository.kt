package dev.eknath.GymJournal.modules.metrics

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.CustomMetricDef
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import dev.eknath.GymJournal.util.ZcqlSanitizer
import org.springframework.stereotype.Repository

private const val TABLE = "CustomMetricDefs"

@Repository
class CustomMetricDefRepository(private val db: CatalystDataStoreRepository) {

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /** Returns all custom metric definitions for [userId], sorted by label ASC. */
    fun findAll(userId: String): List<CustomMetricDef> =
        db.query(
            "SELECT * FROM $TABLE" +
            " WHERE CREATORID = '${ZcqlSanitizer.sanitize(userId)}'" +
            " ORDER BY label ASC"
        ).map { it.toDef() }

    /** Returns the definition matching [metricKey] for [userId], or null if not found. */
    fun findByKey(userId: String, metricKey: String): CustomMetricDef? =
        db.queryOne(
            "SELECT * FROM $TABLE" +
            " WHERE CREATORID = '${ZcqlSanitizer.sanitize(userId)}'" +
            " AND metricKey = '${ZcqlSanitizer.sanitize(metricKey)}'"
        )?.toDef()

    // ---------------------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------------------

    fun save(def: CustomMetricDef): CustomMetricDef {
        val row = db.insert(TABLE, def.toMap())
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        return if (rowId != null) findById(rowId) ?: def.copy(id = rowId) else def
    }

    fun delete(id: Long) = db.delete(TABLE, id)

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun findById(id: Long): CustomMetricDef? =
        db.queryOne("SELECT * FROM $TABLE WHERE ROWID = $id")?.toDef()

    private fun ZCRowObject.toDef() = CustomMetricDef(
        id        = get("ROWID")?.toString()?.toLongOrNull(),
        metricKey = get("metricKey")?.toString() ?: "",
        label     = get("label")?.toString() ?: "",
        unit      = get("unit")?.toString() ?: "",
        // Catalyst system columns — read-only
        createdBy = get("CREATORID")?.toString() ?: "",
        createdAt = get("CREATEDTIME")?.toString() ?: ""
    )

    private fun CustomMetricDef.toMap(): Map<String, Any> = buildMap {
        // Only user-defined columns — CREATORID and CREATEDTIME are set by Catalyst automatically
        put("metricKey", metricKey)
        put("label", label)
        put("unit", unit)
    }
}
