package dev.eknath.GymJournal.model.domain

/**
 * A workout session — always linked to a routine template.
 * Every session must reference a valid [Routine.id]; there is no standalone/free session.
 * This allows clone/share flows and keeps workout history tied to the programme that produced it.
 *
 * Stored in the `WorkoutSessions` DataStore table.
 * The DataStore [routineId] column is a BigInt FK → Routines.ROWID (always a real routine ID).
 */
data class WorkoutSession(
    val id: Long? = null,
    val userId: String,                  // explicit column for ZCQL queries; mirrors CREATORID
    val routineId: Long,                 // always set — every session belongs to a routine
    val routineName: String,             // denormalised for display
    val name: String,
    val status: String,                  // "IN_PROGRESS" | "COMPLETED"
    val startedAt: String,               // yyyy-MM-dd HH:mm:ss
    val completedAt: String,             // "" until COMPLETED
    val notes: String,
    val createdAt: String,               // CREATEDTIME
    val updatedAt: String                // MODIFIEDTIME
)

/**
 * A single set record within a [WorkoutSession].
 *
 * Stored flat in the `WorkoutSets` DataStore table.
 * [itemType] determines which fields are meaningful:
 *   EXERCISE — exerciseId, exerciseName, plannedReps, plannedWeightKg, actualReps, actualWeightKg, rpe, isPersonalBest
 *   REST     — durationSeconds
 *   CARDIO   — exerciseName (as the cardio activity name), durationSeconds, distanceKm
 *
 * [orderInSession] groups all sets that belong to the same exercise slot (e.g., sets 1, 2, 3
 * of the same exercise all share the same orderInSession value).
 */
data class WorkoutSet(
    val id: Long? = null,
    val sessionId: Long,
    val userId: String,                  // denormalised for direct ZCQL queries
    val exerciseId: Long?,               // null for REST / CARDIO (FK col is non-mandatory)
    val exerciseName: String,            // "" for REST; activity name for CARDIO
    val itemType: String,                // "EXERCISE" | "REST" | "CARDIO"
    val orderInSession: Int,             // groups same exercise slot together
    val setNumber: Int,                  // 1, 2, 3… (always 1 for REST/CARDIO)
    val plannedReps: Int,                // 0 if N/A
    val plannedWeightKg: String,         // "0" if N/A
    val actualReps: Int,                 // 0 = not done yet
    val actualWeightKg: String,          // "0" if N/A
    val durationSeconds: Int,            // REST duration or CARDIO timed duration; 0 if N/A
    val distanceKm: String,              // CARDIO only; "0" otherwise
    val rpe: Int,                        // 1–10; 0 = not set
    val isPersonalBest: Int,             // 0/1
    val notes: String,
    val completedAt: String              // "" = not done yet; yyyy-MM-dd HH:mm:ss when done
)
