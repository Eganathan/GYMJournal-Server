package dev.eknath.GymJournal.repository

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
 *   ZCQL.getInstance()               — execute SELECT queries (reads project from SDK state)
 *   ZCObject.getInstance()           — DataStore handle (reads project from SDK state)
 *   ZCObject.getTableInstance(name)  — table handle for insert/update/delete (no network call)
 *   ZCRowObject.getInstance()        — row builder: call row.set("col", value) then insert
 *
 * Note: `object` is a Kotlin keyword and must be backtick-escaped in imports.
 * Always sanitize user-supplied strings with ZcqlSanitizer before interpolating into queries.
 * Both SDK entry points read from the CatalystSDK state initialised by CatalystAuthFilter per-request.
 */
@Repository
class CatalystDataStoreRepository {

    companion object {
        private val LOGGER = Logger.getLogger(CatalystDataStoreRepository::class.java.name)
    }

    fun query(zcql: String): List<ZCRowObject> {
        LOGGER.log(Level.INFO, "[DataStore] Query → $zcql")
        return try {
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
            ZCObject.getInstance().getTableInstance(tableName).deleteRow(rowId)
            LOGGER.log(Level.INFO, "[DataStore] Deleted $tableName ROWID=$rowId")
        } catch (e: Throwable) {
            LOGGER.log(Level.SEVERE, "[DataStore] Delete $tableName ROWID=$rowId failed: ${e.message}", e)
            throw e
        }
    }

    fun count(tableName: String, condition: String = ""): Long {
        val where = if (condition.isNotEmpty()) " WHERE $condition" else ""
        return queryOne("SELECT COUNT(*) as cnt FROM $tableName$where")
            ?.get("cnt")?.toString()?.toLongOrNull() ?: 0L
    }
}
