package dev.eknath.GymJournal.modules.exercises

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.zc.component.`object`.ZCRowObject
import dev.eknath.GymJournal.model.domain.Difficulty
import dev.eknath.GymJournal.model.domain.Exercise
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import dev.eknath.GymJournal.util.ZcqlSanitizer
import org.springframework.stereotype.Repository

private const val TABLE = "Exercises"

@Repository
class ExerciseRepository(
    private val db: CatalystDataStoreRepository,
    private val mapper: ObjectMapper
) {

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /**
     * Returns exercises matching the given filters.
     *
     * Numeric FK predicates (muscleId, equipmentId) are applied in ZCQL — numeric
     * WHERE clauses are reliable in AppSail.
     *
     * String-based filters (difficulty, onlyMine/userId) are applied in-memory after
     * the ZCQL fetch. ZCQL WHERE on user-created Var Char columns silently ignores
     * the condition in AppSail, returning all rows instead of filtering.
     */
    fun findAll(
        callingUserId: Long,
        muscleId: Long?,
        equipmentId: Long?,
        difficulty: String?,
        onlyMine: Boolean
    ): List<Exercise> {
        // Numeric conditions (USER_ID BigInt, muscleId, equipmentId) go into ZCQL — reliable
        val conditions = mutableListOf<String>()
        if (onlyMine) conditions.add("USER_ID = $callingUserId")
        muscleId?.let    { conditions.add("primaryMuscleId = $it") }
        equipmentId?.let { conditions.add("equipmentId = $it") }

        val where = if (conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}"
        val rows = db.query("SELECT * FROM $TABLE$where ORDER BY name ASC LIMIT 0,300")
            .map { it.toExercise() }

        // Difficulty is a string column — filter in-memory (ZCQL string WHERE is unreliable)
        return if (difficulty == null) rows
        else rows.filter { it.difficulty.name.equals(difficulty, ignoreCase = true) }
    }

    fun findById(id: Long): Exercise? =
        db.getRow(TABLE, id)?.toExercise()

    fun findByName(name: String): Exercise? =
        db.queryOne("SELECT * FROM $TABLE WHERE name = '${ZcqlSanitizer.sanitize(name)}'")?.toExercise()

    // ---------------------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------------------

    fun save(exercise: Exercise): Exercise {
        val row = db.insert(TABLE, exercise.toMap())
        val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
            ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
        return if (rowId != null) findById(rowId) ?: exercise.copy(id = rowId) else exercise
    }

    fun update(id: Long, exercise: Exercise) =
        db.update(TABLE, id, exercise.toMap())

    fun delete(id: Long) = db.delete(TABLE, id)

    // ---------------------------------------------------------------------------
    // Mappers
    // ---------------------------------------------------------------------------

    private fun ZCRowObject.toExercise() = Exercise(
        id               = get("ROWID")?.toString()?.toLongOrNull(),
        name             = get("name")?.toString() ?: "",
        description      = get("description")?.toString() ?: "",
        primaryMuscleId  = get("primaryMuscleId")?.toString()?.toLongOrNull() ?: 0L,
        secondaryMuscles = get("secondaryMuscles")?.toString().toStringList(),
        equipmentId      = get("equipmentId")?.toString()?.toLongOrNull() ?: 0L,
        difficulty       = get("difficulty")?.toString().toDifficulty(),
        instructions     = get("instructions")?.toString().toStringList(),
        tips             = get("tips")?.toString().toStringList(),
        imageUrl         = get("imageUrl")?.toString()?.takeIf { it.isNotBlank() },
        videoUrl         = get("videoUrl")?.toString()?.takeIf { it.isNotBlank() },
        tags             = get("tags")?.toString().toStringList(),
        // Catalyst system columns — auto-provided, never written in toMap()
        createdBy        = get("USER_ID")?.toString()?.toLongOrNull() ?: 0L,
        createdAt        = get("CREATEDTIME")?.toString() ?: "",
        updatedAt        = get("MODIFIEDTIME")?.toString() ?: ""
    )

    private fun Exercise.toMap(): Map<String, Any> = buildMap {
        // userId stored explicitly — CREATORID is unreliable in AppSail (app credentials, not user)
        // CREATEDTIME, MODIFIEDTIME are still auto-set by Catalyst
        put("USER_ID", createdBy)
        put("name", name)
        put("description", description)
        put("primaryMuscleId", primaryMuscleId)
        put("secondaryMuscles", secondaryMuscles.toJson())
        put("equipmentId", equipmentId)
        put("difficulty", difficulty.name)
        put("instructions", instructions.toJson())
        put("tips", tips.toJson())
        put("imageUrl", imageUrl ?: "")
        put("videoUrl", videoUrl ?: "")
        put("tags", tags.toJson())
    }

    // JSON list helpers

    private fun List<String>.toJson(): String = mapper.writeValueAsString(this)

    private fun String?.toStringList(): List<String> {
        if (isNullOrBlank() || this == "null") return emptyList()
        return try {
            mapper.readValue(this, object : TypeReference<List<String>>() {})
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun String?.toDifficulty(): Difficulty =
        try { Difficulty.valueOf(this ?: "") } catch (_: Exception) { Difficulty.BEGINNER }
}
