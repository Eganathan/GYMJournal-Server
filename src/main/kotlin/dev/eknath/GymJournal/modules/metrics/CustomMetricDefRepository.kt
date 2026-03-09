package dev.eknath.GymJournal.modules.metrics

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.CustomMetricDef
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import org.springframework.stereotype.Repository

private const val TABLE = "CustomMetricDefs"

@Repository
class CustomMetricDefRepository(private val db: CatalystDataStoreRepository) {

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /**
     * Returns all custom metric definitions for [userId], sorted by label ASC.
     *
     * Fetches all rows without a WHERE clause and filters in-memory.
     * ZCQL WHERE on user-created Var Char columns (userId) is unreliable
     * in AppSail — the filter is silently ignored, returning all users' data.
     */
    fun findAll(userId: Long): List<CustomMetricDef> =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,300")
            .map { it.toDef() }
            .sortedBy { it.label }

    fun findByKey(userId: Long, metricKey: String): CustomMetricDef? =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,300")
            .map { it.toDef() }
            .firstOrNull { it.metricKey == metricKey }

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
        db.getRow(TABLE, id)?.toDef()

    private fun ZCRowObject.toDef() = CustomMetricDef(
        id        = get("ROWID")?.toString()?.toLongOrNull(),
        metricKey = get("metricKey")?.toString() ?: "",
        label     = get("label")?.toString() ?: "",
        unit      = get("unit")?.toString() ?: "",
        // Prefer explicit userId column; CREATORID is unreliable in AppSail (app credentials, not user)
        createdBy = get("USER_ID")?.toString()?.toLongOrNull() ?: 0L,
        createdAt = get("CREATEDTIME")?.toString() ?: ""
    )

    private fun CustomMetricDef.toMap(): Map<String, Any> = buildMap {
        // userId stored explicitly — CREATORID is unreliable in AppSail (app credentials, not user)
        // CREATEDTIME is still auto-set by Catalyst
        put("USER_ID", createdBy)
        put("metricKey", metricKey)
        put("label", label)
        put("unit", unit)
    }
}
