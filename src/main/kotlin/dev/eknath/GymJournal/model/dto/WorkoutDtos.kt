package dev.eknath.GymJournal.model.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

// ── Session request DTOs ──────────────────────────────────────────────────────

/**
 * Body for POST /api/v1/workouts — starts a new session.
 *
 * [routineId] = null → standalone free workout (no template).
 * [name] = null → auto-generated as "<routineName> - <date>" or "Free Workout - <date>".
 * [startedAt] = null → server uses current time.
 */
data class StartWorkoutRequest(
    val routineId: Long? = null,
    @field:Size(max = 100)
    val name: String? = null,
    val startedAt: String? = null       // ISO-8601; null = now
)

data class PatchWorkoutRequest(
    @field:Size(max = 100)
    val name: String? = null,
    val notes: String? = null
)

// ── Set request DTOs ──────────────────────────────────────────────────────────

/**
 * Body for POST /api/v1/workouts/{sessionId}/sets — adds a set to the session.
 *
 * For REST: set itemType = "REST", durationSeconds = N, leave everything else 0/"0".
 * For CARDIO: itemType = "CARDIO", exerciseName = activity, durationSeconds / distanceKm.
 * For EXERCISE: itemType = "EXERCISE", exerciseId, exerciseName, plannedReps, actualReps, etc.
 */
data class AddSetRequest(
    val exerciseId: Long = 0,
    @field:Size(max = 100)
    val exerciseName: String = "",
    val itemType: String = "EXERCISE",  // "EXERCISE" | "REST" | "CARDIO"
    val orderInSession: Int = 1,
    val setNumber: Int = 1,
    val plannedReps: Int = 0,
    val plannedWeightKg: String = "0",
    val actualReps: Int = 0,
    val actualWeightKg: String = "0",
    val durationSeconds: Int = 0,
    val distanceKm: String = "0",
    @field:Min(0) @field:Max(10)
    val rpe: Int = 0,
    val notes: String = "",
    val completedAt: String? = null     // ISO-8601; null = not done yet
)

data class UpdateSetRequest(
    val actualReps: Int? = null,
    val actualWeightKg: String? = null,
    val plannedReps: Int? = null,
    val plannedWeightKg: String? = null,
    val durationSeconds: Int? = null,
    val distanceKm: String? = null,
    @field:Min(0) @field:Max(10)
    val rpe: Int? = null,
    val notes: String? = null,
    val completedAt: String? = null     // ISO-8601
)

// ── Session response DTOs ─────────────────────────────────────────────────────

/**
 * A grouped block of sets for one exercise (or a REST/CARDIO slot) within a session.
 * Used in [WorkoutSessionResponse.exercises].
 */
data class SessionItemGroup(
    val orderInSession: Int,
    val itemType: String,
    val exerciseId: Long,
    val exerciseName: String,
    val sets: List<WorkoutSetResponse>
)

/**
 * Full session detail — returned by GET /api/v1/workouts/{id}.
 * Sets are grouped by [orderInSession] into [exercises].
 */
data class WorkoutSessionResponse(
    val id: Long,
    val name: String,
    val routineId: Long,
    val routineName: String,
    val status: String,
    val startedAt: String,
    val completedAt: String?,           // null until COMPLETED
    val notes: String,
    val createdAt: String,
    val updatedAt: String,
    val exercises: List<SessionItemGroup>
)

/**
 * Compact session item for list responses (GET /api/v1/workouts).
 */
data class WorkoutSessionSummaryResponse(
    val id: Long,
    val name: String,
    val routineId: Long,
    val routineName: String,
    val status: String,
    val startedAt: String,
    val completedAt: String?,
    val updatedAt: String
)

/**
 * A single set record — used inside [SessionItemGroup.sets] and in history/PBs.
 */
data class WorkoutSetResponse(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val itemType: String,
    val orderInSession: Int,
    val setNumber: Int,
    val plannedReps: Int,
    val plannedWeightKg: String,
    val actualReps: Int?,               // null = not done
    val actualWeightKg: String?,        // null = not done
    val durationSeconds: Int,
    val distanceKm: String,
    val rpe: Int?,                      // null = not set
    val isPersonalBest: Boolean,
    val notes: String,
    val completedAt: String?            // null = not done
)
