package dev.eknath.GymJournal.model.dto

import dev.eknath.GymJournal.model.domain.Difficulty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

// ---------------------------------------------------------------------------
// Lookup requests / responses
// ---------------------------------------------------------------------------

data class CreateMuscleGroupRequest(
    @field:NotBlank val slug: String,
    @field:NotBlank val displayName: String,
    @field:NotBlank val shortName: String,
    val description: String = "",
    @field:NotBlank val bodyRegion: String,   // UPPER_BODY | LOWER_BODY | CORE | FULL_BODY | OTHER
    val imageUrl: String? = null
)

data class MuscleGroupResponse(
    val id: Long,
    val slug: String,
    val displayName: String,
    val shortName: String,
    val description: String,
    val bodyRegion: String,
    val imageUrl: String?
)

data class CreateEquipmentRequest(
    @field:NotBlank val slug: String,
    @field:NotBlank val displayName: String,
    val description: String = "",
    @field:NotBlank val category: String,     // FREE_WEIGHTS | MACHINES | BODYWEIGHT | CARDIO_MACHINES | OTHER
    val imageUrl: String? = null
)

data class EquipmentResponse(
    val id: Long,
    val slug: String,
    val displayName: String,
    val description: String,
    val category: String,
    val imageUrl: String?
)

// ---------------------------------------------------------------------------
// Exercise requests
// ---------------------------------------------------------------------------

data class CreateExerciseRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    val description: String = "",
    val primaryMuscleId: Long,
    val secondaryMuscles: List<String> = emptyList(),
    val equipmentId: Long,
    val difficulty: Difficulty = Difficulty.BEGINNER,
    @field:NotEmpty val instructions: List<String>,
    val tips: List<String> = emptyList(),
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val tags: List<String> = emptyList()
)

data class UpdateExerciseRequest(
    @field:Size(max = 100) val name: String? = null,
    val description: String? = null,
    val primaryMuscleId: Long? = null,
    val secondaryMuscles: List<String>? = null,
    val equipmentId: Long? = null,
    val difficulty: Difficulty? = null,
    val instructions: List<String>? = null,
    val tips: List<String>? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val tags: List<String>? = null
)

// ---------------------------------------------------------------------------
// Exercise responses
// ---------------------------------------------------------------------------

/** Full exercise detail — returned by GET /{id} and POST / PUT */
data class ExerciseResponse(
    val id: Long,
    val name: String,
    val description: String,
    val primaryMuscleId: Long,
    val secondaryMuscles: List<String>,
    val equipmentId: Long,
    val difficulty: String,
    val instructions: List<String>,
    val tips: List<String>,
    val imageUrl: String?,
    val videoUrl: String?,
    val tags: List<String>,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String
)

/** Compact item used in paginated list results */
data class ExerciseSummaryResponse(
    val id: Long,
    val name: String,
    val primaryMuscleId: Long,
    val equipmentId: Long,
    val difficulty: String,
    val createdBy: String
)
