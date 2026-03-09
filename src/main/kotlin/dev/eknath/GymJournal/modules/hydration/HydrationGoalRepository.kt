package dev.eknath.GymJournal.modules.hydration

import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import org.springframework.stereotype.Repository

private const val TABLE = "HydrationGoals"

@Repository
class HydrationGoalRepository(private val db: CatalystDataStoreRepository) {

    /** Returns the stored goal for [userId], or null if not set. */
    fun findByUser(userId: Long): Int? =
        db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,1")
            .firstOrNull()
            ?.let { it.get("goalMl")?.toString()?.toIntOrNull() }

    /**
     * Sets the goal for [userId]. Inserts a new row if none exists, updates in-place otherwise.
     * USER_ID is BigInt — the ZCQL WHERE is reliable for numeric columns.
     */
    fun upsert(userId: Long, goalMl: Int) {
        val existing = db.query("SELECT * FROM $TABLE WHERE USER_ID = $userId LIMIT 0,1").firstOrNull()
        if (existing != null) {
            val rowId = existing.get("ROWID")?.toString()?.toLongOrNull()
                ?: existing.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
            if (rowId != null) {
                db.update(TABLE, rowId, mapOf("USER_ID" to userId, "goalMl" to goalMl))
                return
            }
        }
        db.insert(TABLE, mapOf("USER_ID" to userId, "goalMl" to goalMl))
    }
}
