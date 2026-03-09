package dev.eknath.GymJournal.repository

import com.zc.common.ZCProject
import com.zc.common.ZCProjectConfig
import com.zc.component.`object`.ZCObject
import com.zc.component.`object`.ZCRowObject
import com.zc.component.zcql.ZCQL
import org.springframework.stereotype.Repository
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Base repository wrapping the Catalyst Java SDK 2.2.0.
 *
 * SDK entry points used:
 *   ZCQL.getInstance()               — execute SELECT queries
 *   ZCObject.getInstance()           — DataStore handle
 *   ZCObject.getTableInstance(name)  — table handle for insert/update/delete/getRow
 *   ZCRowObject.getInstance()        — row builder: call row.set("col", value) then insert
 *
 * ZCProject.initProject(devConfig) is called at the start of every operation
 * (confirmed production pattern from croptor-catalyst-app). This ensures each
 * DataStore operation has fresh project context with the Development environment,
 * since getDefaultProjectConfig() defaults to Production.
 *
 * Single-row lookup: always use getRow(tableName, rowId) — never ZCQL for findById.
 * Note: `object` is a Kotlin keyword and must be backtick-escaped in imports.
 * Always sanitize user-supplied strings with ZcqlSanitizer before interpolating into queries.
 */
@Repository
class CatalystDataStoreRepository {

    companion object {
        private val LOGGER = Logger.getLogger(CatalystDataStoreRepository::class.java.name)
        private const val ENV_DEVELOPMENT = "Development"
    }

    /**
     * Initialises ZCProject with the Development environment before each DataStore operation.
     * Called at the start of every public method — matches the Croptor production pattern
     * of per-operation project init to ensure correct SDK context.
     */
    private fun initProject() {
        val defaultConfig = ZCProject.getDefaultProjectConfig() ?: return
        val devConfig = ZCProjectConfig.newBuilder()
            .setProjectId(defaultConfig.projectId)
            .setProjectKey(defaultConfig.projectKey)
            .setProjectDomain(defaultConfig.projectDomain)
            .setZcAuth(defaultConfig.zcAuth)
            .setEnvironment(ENV_DEVELOPMENT)
            .build()
        ZCProject.initProject(devConfig)
    }

    fun query(zcql: String): List<ZCRowObject> {
        LOGGER.log(Level.INFO, "[DataStore] Query → $zcql")
        return try {
            initProject()
            val result = ZCQL.getInstance().executeQuery(zcql)
            LOGGER.log(Level.INFO, "[DataStore] Query returned ${result.size} row(s)")
            result
        } catch (e: Throwable) {
            LOGGER.log(Level.SEVERE, "[DataStore] Query failed: ${e.message}", e)
            emptyList()
        }
    }

    fun queryOne(zcql: String): ZCRowObject? = query(zcql).firstOrNull()

    fun insert(tableName: String, data: Map<String, Any>): ZCRowObject {
        LOGGER.log(Level.INFO, "[DataStore] Insert → $tableName fields=${data.keys}")
        return try {
            initProject()
            val table = ZCObject.getInstance().getTableInstance(tableName)
            val row = ZCRowObject.getInstance()
            data.forEach { (k, v) -> row.set(k, v) }
            val result = table.insertRow(row)
            LOGGER.log(Level.INFO, "[DataStore] Inserted into $tableName")
            result
        } catch (e: Throwable) {
            LOGGER.log(Level.SEVERE, "[DataStore] Insert into $tableName failed: ${e.message}", e)
            throw e
        }
    }

    fun update(tableName: String, rowId: Long, data: Map<String, Any>) {
        LOGGER.log(Level.INFO, "[DataStore] Update → $tableName ROWID=$rowId fields=${data.keys}")
        try {
            initProject()
            val table = ZCObject.getInstance().getTableInstance(tableName)
            val row = ZCRowObject.getInstance()
            row.set("ROWID", rowId)
            data.forEach { (k, v) -> row.set(k, v) }
            table.updateRows(listOf(row))
            LOGGER.log(Level.INFO, "[DataStore] Updated $tableName ROWID=$rowId")
        } catch (e: Throwable) {
            LOGGER.log(Level.SEVERE, "[DataStore] Update $tableName ROWID=$rowId failed: ${e.message}", e)
            throw e
        }
    }

    fun delete(tableName: String, rowId: Long) {
        LOGGER.log(Level.INFO, "[DataStore] Delete → $tableName ROWID=$rowId")
        try {
            initProject()
            ZCObject.getInstance().getTableInstance(tableName).deleteRow(rowId)
            LOGGER.log(Level.INFO, "[DataStore] Deleted $tableName ROWID=$rowId")
        } catch (e: Throwable) {
            LOGGER.log(Level.SEVERE, "[DataStore] Delete $tableName ROWID=$rowId failed: ${e.message}", e)
            throw e
        }
    }

    fun count(tableName: String, condition: String = ""): Long {
        val where = if (condition.isNotEmpty()) " WHERE $condition" else ""
        // ZCQL V2 does not support COUNT(*) — must use a specific column name. ROWID is always present.
        return queryOne("SELECT COUNT(ROWID) as cnt FROM $tableName$where")
            ?.get("cnt")?.toString()?.toLongOrNull() ?: 0L
    }

    /**
     * Fetches a single row by ROWID using ZCObject (same SDK path as insert/update/delete).
     * Preferred over ZCQL for single-row lookups — bypasses ZCQL entirely and is more reliable.
     * Returns null if the row does not exist or an SDK error occurs.
     */
    fun getRow(tableName: String, rowId: Long): ZCRowObject? {
        LOGGER.log(Level.INFO, "[DataStore] GetRow → $tableName ROWID=$rowId")
        return try {
            initProject()
            val result = ZCObject.getInstance().getTableInstance(tableName).getRow(rowId)
            LOGGER.log(Level.INFO, "[DataStore] GetRow found in $tableName ROWID=$rowId")
            result
        } catch (e: Throwable) {
            LOGGER.log(Level.WARNING, "[DataStore] GetRow failed for $tableName ROWID=$rowId: ${e.message}")
            null
        }
    }
}
