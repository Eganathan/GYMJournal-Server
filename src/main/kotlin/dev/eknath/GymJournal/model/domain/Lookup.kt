package dev.eknath.GymJournal.model.domain

/**
 * Difficulty level for an exercise. This is a true fixed taxonomy (exactly 3 levels)
 * so a Kotlin enum is appropriate — no metadata or runtime extensibility needed.
 */
enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }

/**
 * A muscle group category stored in the MuscleGroups DataStore table.
 * Using a table (not a Kotlin enum) so new muscle groups can be added by inserting a row
 * without a code change or redeploy.
 *
 * Catalyst DataStore table: MuscleGroups
 * Columns: slug, displayName, shortName, description, bodyRegion, imageUrl
 */
data class MuscleGroup(
    val id: Long? = null,
    val slug: String,           // Unique key stored in Exercise.primaryMuscleSlug, e.g. "LATS"
    val displayName: String,    // Full anatomical name, e.g. "Latissimus Dorsi (Lats)"
    val shortName: String,      // Compact label for list views, e.g. "Lats"
    val description: String,    // What this muscle group is and what it does
    val bodyRegion: String,     // UPPER_BODY | LOWER_BODY | CORE | FULL_BODY | OTHER
    val imageUrl: String? = null
)

/**
 * A piece of equipment stored in the Equipment DataStore table.
 * Using a table so new equipment types can be added without a redeploy.
 *
 * Catalyst DataStore table: Equipment
 * Columns: slug, displayName, description, category, imageUrl
 */
data class Equipment(
    val id: Long? = null,
    val slug: String,           // Unique key stored in Exercise.equipmentSlug, e.g. "BARBELL"
    val displayName: String,    // Human-readable name, e.g. "Barbell"
    val description: String,    // What this equipment is
    val category: String,       // FREE_WEIGHTS | MACHINES | BODYWEIGHT | CARDIO_MACHINES | OTHER
    val imageUrl: String? = null
)
