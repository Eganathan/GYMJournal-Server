package dev.eknath.GymJournal.model.domain

/**
 * Represents a single exercise in the community exercise library.
 *
 * Ownership: any authenticated user can create; only the creator can edit/delete.
 * All exercises are publicly visible (no private/draft state).
 *
 * List fields (secondaryMuscles, instructions, tips, tags) have no array type in DataStore
 * and are stored as JSON strings. The ExerciseRepository handles serialisation/deserialisation
 * via Jackson ObjectMapper.
 *
 * Catalyst DataStore table: Exercises
 * User-defined columns: name, description, primaryMuscleId, secondaryMuscles,
 *                       equipmentId, difficulty, instructions, tips, imageUrl, videoUrl, tags
 * System columns (auto-provided by Catalyst — do NOT create manually):
 *   ROWID       → id
 *   CREATORID   → createdBy
 *   CREATEDTIME → createdAt
 *   MODIFIEDTIME → updatedAt
 */
data class Exercise(
    val id: Long? = null,
    val name: String,
    val description: String,
    val primaryMuscleId: Long,              // FK → MuscleGroups.ROWID
    val secondaryMuscles: List<String>,     // named muscles e.g. ["Rhomboids", "Biceps"]
    val equipmentId: Long,                  // FK → Equipment.ROWID
    val difficulty: Difficulty,
    val instructions: List<String>,         // ordered how-to steps
    val tips: List<String>,                 // coaching cues / common mistakes
    val imageUrl: String?,
    val videoUrl: String?,
    val tags: List<String>,                 // e.g. ["compound", "pull", "bodyweight"]
    val createdBy: String,                  // from CREATORID (Catalyst system column)
    val createdAt: String,                  // from CREATEDTIME (Catalyst system column)
    val updatedAt: String                   // from MODIFIEDTIME (Catalyst system column)
)
