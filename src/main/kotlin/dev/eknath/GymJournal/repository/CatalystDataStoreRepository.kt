package dev.eknath.GymJournal.repository

import com.zc.common.ZCProject
import com.zc.component.`object`.ZCRowObject
import com.zc.component.`object`.ZCTable
import com.zc.component.zcql.ZCQL
import org.springframework.stereotype.Repository

/**
 * Base repository wrapping the Catalyst Java SDK 2.2.0.
 *
 * Public SDK API used here:
 *   ZCQL.getInstance(ZCProject)  — execute SELECT queries; returns ArrayList<ZCRowObject>
 *   ZCTable.getInstance()        — table handle (reads from static project singleton)
 *   ZCRowObject.getInstance()    — row builder: call row.set("col", value) then insert
 *
 * Note: `object` is a Kotlin keyword and must be backtick-escaped in imports.
 * Always sanitize user-supplied strings with ZcqlSanitizer before interpolating into queries.
 */
@Repository
class CatalystDataStoreRepository {

    fun query(zcql: String): List<ZCRowObject> {
        return try {
            ZCQL.getInstance(ZCProject.getDefaultProject()).executeQuery(zcql)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun queryOne(zcql: String): ZCRowObject? = query(zcql).firstOrNull()

    fun insert(tableName: String, data: Map<String, Any>): ZCRowObject {
        val table = ZCTable.getInstance().apply { setName(tableName) }
        val row = ZCRowObject.getInstance()
        data.forEach { (k, v) -> row.set(k, v) }
        return table.insertRow(row)
    }

    fun update(tableName: String, rowId: Long, data: Map<String, Any>) {
        val table = ZCTable.getInstance().apply { setName(tableName) }
        val row = ZCRowObject.getInstance()
        row.set("ROWID", rowId)
        data.forEach { (k, v) -> row.set(k, v) }
        table.updateRows(listOf(row))
    }

    fun delete(tableName: String, rowId: Long) {
        ZCTable.getInstance().apply { setName(tableName) }.deleteRow(rowId)
    }

    fun count(tableName: String, condition: String = ""): Long {
        val where = if (condition.isNotEmpty()) " WHERE $condition" else ""
        val result = queryOne("SELECT COUNT(*) as cnt FROM $tableName$where")
        return result?.get("cnt")?.toString()?.toLongOrNull() ?: 0L
    }
}
