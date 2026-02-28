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
     * Returns exercises matching the given filters. Search by name is done in-memory
     * after the ZCQL fetch because ZCQL does not support reliable full-text search.
     *
     * All exercises are public. `onlyMine` filters by CREATORID to show just the
     * calling user's own contributions.
     */
    fun findAll(
        callingUserId: String,
        muscleId: Long?,
        equipmentId: Long?,
        difficulty: String?,
        onlyMine: Boolean
    ): List<Exercise> {
        val conditions = mutableListOf<String>()

        if (onlyMine) {
            conditions.add("CREATORID = '${ZcqlSanitizer.sanitize(callingUserId)}'")
        }
        muscleId?.let {
            conditions.add("primaryMuscleId = $it")
        }
        equipmentId?.let {
            conditions.add("equipmentId = $it")
        }
        difficulty?.let {
            conditions.add("difficulty = '${ZcqlSanitizer.sanitize(it)}'")
        }

        val where = if (conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}"
        return db.query("SELECT * FROM $TABLE$where ORDER BY name ASC").map { it.toExercise() }
    }

    fun findById(id: Long): Exercise? =
        db.queryOne("SELECT * FROM $TABLE WHERE ROWID = $id")?.toExercise()

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
        createdBy        = get("CREATORID")?.toString() ?: "",
        createdAt        = get("CREATEDTIME")?.toString() ?: "",
        updatedAt        = get("MODIFIEDTIME")?.toString() ?: ""
    )

    private fun Exercise.toMap(): Map<String, Any> = buildMap {
        // Only user-defined columns — CREATORID, CREATEDTIME, MODIFIEDTIME are set by Catalyst automatically
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
