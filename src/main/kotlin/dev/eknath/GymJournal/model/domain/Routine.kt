package dev.eknath.GymJournal.model.domain

enum class RoutineItemType { EXERCISE, REST, CARDIO }

/**
 * A single ordered item inside a routine template.
 *
 * Fields are nullable because each [RoutineItemType] uses a different subset:
 *   EXERCISE — exerciseId, exerciseName, sets, repsPerSet, weightKg, restAfterSeconds, notes
 *   REST     — durationSeconds
 *   CARDIO   — cardioName, durationMinutes, targetSpeedKmh
 *
 * Stored as a JSON array in the Routines DataStore table (Routines.items column).
 */
data class RoutineItem(
    val order: Int,
    val type: RoutineItemType,

    // ── EXERCISE ──────────────────────────────────────────────────────────────
    val exerciseId: Long? = null,
    val exerciseName: String? = null,       // denormalised for display without extra lookup
    val sets: Int? = null,                  // planned number of sets
    val repsPerSet: Int? = null,            // planned reps per set
    val weightKg: String? = null,           // "80.0"; null means bodyweight
    val restAfterSeconds: Int? = null,      // rest after this exercise slot
    val notes: String? = null,

    // ── REST ──────────────────────────────────────────────────────────────────
    val durationSeconds: Int? = null,

    // ── CARDIO ────────────────────────────────────────────────────────────────
    val cardioName: String? = null,         // e.g. "Treadmill", "Rowing"
    val durationMinutes: Int? = null,
    val targetSpeedKmh: String? = null      // "8.0"; null if not applicable
)

/**
 * A routine template — a named, ordered workout plan that can be shared publicly
 * and cloned by any user.
 *
 * Stored in the `Routines` DataStore table.
 * System columns ROWID → id, CREATORID → createdBy, CREATEDTIME → createdAt,
 * MODIFIEDTIME → updatedAt.
 */
data class Routine(
    val id: Long? = null,
    val name: String,
    val description: String,
    val items: List<RoutineItem>,           // JSON-serialised in DB
    val estimatedMinutes: Int,              // advisory; 0 = not set
    val tags: List<String>,                 // JSON-serialised in DB
    val isPublic: Int,                      // 1 = anyone can browse/clone, 0 = private
    val createdBy: String,                  // from CREATORID
    val createdAt: String,                  // from CREATEDTIME  (yyyy-MM-dd HH:mm:ss in DB)
    val updatedAt: String                   // from MODIFIEDTIME (yyyy-MM-dd HH:mm:ss in DB)
)
