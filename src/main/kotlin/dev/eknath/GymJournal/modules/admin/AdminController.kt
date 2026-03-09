package dev.eknath.GymJournal.modules.admin

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import dev.eknath.GymJournal.model.domain.Difficulty
import dev.eknath.GymJournal.model.domain.Equipment
import dev.eknath.GymJournal.model.domain.Exercise
import dev.eknath.GymJournal.model.domain.MuscleGroup
import dev.eknath.GymJournal.modules.exercises.EquipmentRepository
import dev.eknath.GymJournal.modules.exercises.ExerciseRepository
import dev.eknath.GymJournal.modules.exercises.MuscleGroupRepository
import dev.eknath.GymJournal.repository.CatalystDataStoreRepository
import dev.eknath.GymJournal.util.ApiResponse
import dev.eknath.GymJournal.util.currentUserId
import org.springframework.core.io.ClassPathResource
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Seed endpoint to populate lookup tables (MuscleGroups, Equipment) and the
 * community Exercise library with full data sourced from Hevy (scraped by data/scrape_hevy.py).
 *
 *   POST /api/v1/admin/seed
 *
 * Fully idempotent at the row level — each item is only inserted if an entry
 * with the same name does not already exist. Safe to call multiple times; only
 * new/missing rows will be inserted on subsequent calls.
 *
 * Requires standard API auth (zcauthtoken cookie or Bearer token validated by ZGS).
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val muscleGroupRepo: MuscleGroupRepository,
    private val equipmentRepo: EquipmentRepository,
    private val exerciseRepo: ExerciseRepository,
    private val db: CatalystDataStoreRepository,
    private val mapper: ObjectMapper
) {

    @PostMapping("/seed")
    fun seed(): ApiResponse<*> {
        // Fast-path: if all three tables already have data, return without touching the DB again.
        // This prevents re-running expensive seeding logic on repeated accidental calls.
        val muscleCount   = muscleGroupRepo.findAll().size
        val equipCount    = equipmentRepo.findAll().size
        val exerciseCount = exerciseRepo.findAll(0L, null, null, null, false).size
        if (muscleCount > 0 && equipCount > 0 && exerciseCount > 0) {
            return ApiResponse.ok(
                mapOf(
                    "muscleGroupsInserted" to 0,
                    "equipmentInserted"    to 0,
                    "exercisesInserted"    to 0,
                    "message"              to "Already seeded — no changes made"
                )
            )
        }

        val musclesSeeded   = seedMuscleGroups()
        val equipmentSeeded = seedEquipment()
        val exercisesSeeded = seedExercises()
        return ApiResponse.ok(
            mapOf(
                "muscleGroupsInserted" to musclesSeeded,
                "equipmentInserted"    to equipmentSeeded,
                "exercisesInserted"    to exercisesSeeded,
                "message"              to "Seed complete"
            )
        )
    }

    /**
     * Force-seed: identical to [seed] but skips the fast-path guard so it always
     * runs even if tables appear non-empty. Exercises are attributed to [ownerId]
     * (defaults to "10119736618") so they show up under "My Exercises" for that user.
     *
     * Safe to call multiple times — the per-exercise name-based idempotency check
     * in [seedExercises] prevents duplicate inserts.
     *
     *   POST /api/v1/admin/force-seed
     */
    @PostMapping("/force-seed")
    fun forceSeed(): ApiResponse<*> {
        val ownerId = 10119736618L
        val musclesSeeded   = seedMuscleGroups()
        val equipmentSeeded = seedEquipment()
        val exercisesSeeded = seedExercises(creatorId = ownerId)
        return ApiResponse.ok(
            mapOf(
                "muscleGroupsInserted" to musclesSeeded,
                "equipmentInserted"    to equipmentSeeded,
                "exercisesInserted"    to exercisesSeeded,
                "ownerId"              to ownerId,
                "message"              to "Force seed complete — exercises attributed to user $ownerId"
            )
        )
    }

    // ---------------------------------------------------------------------------
    // One-time migration: claim orphaned rows (userId = "") for the calling user
    // ---------------------------------------------------------------------------

    /**
     * Claims all DataStore rows that have an empty `userId` column by setting
     * their `userId` to the calling user's ZID.
     *
     * This is needed for data created before the explicit `userId` column was
     * added to each table. Without this, those rows return 403 on GET / PUT / DELETE
     * because the ownership check (`createdBy != callingUserId`) always fails.
     *
     * Safe to call multiple times — subsequent calls will find 0 orphaned rows
     * and return immediately without touching the DB.
     *
     *   POST /api/v1/admin/migrate/claim-orphaned
     */
    @PostMapping("/migrate/claim-orphaned")
    fun claimOrphanedRows(): ApiResponse<*> {
        val userId = currentUserId()

        // Tables that store per-user data with an explicit userId column.
        // WorkoutSets do not have a userId column (they belong to a session) — excluded.
        // Exercises are shared / public — excluded.
        val userDataTables = listOf(
            "Routines",
            "WorkoutSessions",
            "BodyMetricEntries",
            "WaterIntakeLogs",
            "CustomMetricDefs"
        )

        val counts = mutableMapOf<String, Int>()

        userDataTables.forEach { table ->
            // Fetch all rows and filter in-memory.
            // ZCQL  WHERE userId = ''  does NOT match rows where the column value is NULL
            // (which is what Catalyst DataStore stores when a column is added after insert).
            // In-memory filtering catches both NULL and empty-string cases.
            val allRows = db.query("SELECT * FROM $table LIMIT 0,300")
            val orphaned = allRows.filter { row ->
                val uid = row.get("USER_ID")?.toString()
                uid.isNullOrBlank() || uid == "null"
            }
            var updated = 0
            orphaned.forEach { row ->
                val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
                if (rowId != null) {
                    try {
                        db.update(table, rowId, mapOf("USER_ID" to userId))
                        updated++
                    } catch (_: Exception) {
                        // log and continue — don't abort entire migration for one row
                    }
                }
            }
            counts[table] = updated
        }

        val totalUpdated = counts.values.sum()
        return ApiResponse.ok(
            mapOf(
                "updatedByTable" to counts,
                "totalUpdated"   to totalUpdated,
                "claimedBy"      to userId,
                "message"        to if (totalUpdated > 0)
                    "Claimed $totalUpdated orphaned row(s) for user $userId"
                else
                    "No orphaned rows found — nothing to migrate"
            )
        )
    }

    // ---------------------------------------------------------------------------
    // Per-row-idempotent seeders
    // ---------------------------------------------------------------------------

    /** Inserts any muscle groups from [defaultMuscleGroups] that don't already exist by displayName. */
    private fun seedMuscleGroups(): Int {
        val existing = muscleGroupRepo.findAll().map { it.displayName }.toSet()
        val toInsert = defaultMuscleGroups().filter { it.displayName !in existing }
        toInsert.forEach { muscleGroupRepo.save(it) }
        return toInsert.size
    }

    /** Inserts any equipment types from [defaultEquipment] that don't already exist by displayName. */
    private fun seedEquipment(): Int {
        val existing = equipmentRepo.findAll().map { it.displayName }.toSet()
        val toInsert = defaultEquipment().filter { it.displayName !in existing }
        toInsert.forEach { equipmentRepo.save(it) }
        return toInsert.size
    }

    /**
     * Inserts exercises from data/hevy_exercises_merged.json (bundled in the classpath).
     * Each exercise is skipped if an entry with the same name already exists.
     *
     * Full data is used: instructions, tips, secondaryMuscles, imageUrls, tags.
     * Resolves muscle group and equipment IDs from the current DB state — so
     * [seedMuscleGroups] and [seedEquipment] must have been called (or pre-seeded)
     * before this runs. Any exercise whose muscle group or equipment cannot be
     * resolved is skipped silently.
     *
     * @param creatorId the userId to store as the exercise owner. Defaults to "system"
     *   for shared library exercises; pass a real user ZID via [forceSeed] to attribute
     *   exercises to a specific user.
     */
    private fun seedExercises(creatorId: Long = 0L): Int {
        // Build name → id lookup maps from whatever is in the DB right now
        val muscleNameToId = muscleGroupRepo.findAll().mapNotNull { g ->
            g.id?.let { g.displayName to it }
        }.toMap()
        val equipNameToId = equipmentRepo.findAll().mapNotNull { e ->
            e.id?.let { e.displayName to it }
        }.toMap()

        // Existing exercise names — skip any that are already present
        val existingNames = exerciseRepo.findAll(0L, null, null, null, false)
            .map { it.name }.toSet()

        // Load the full merged exercise data from classpath
        val hevyData = loadHevyExercises()

        var inserted = 0
        hevyData.forEach { ex ->
            if (ex.name in existingNames) return@forEach

            val ourMuscleName = HEVY_MUSCLE_MAP[ex.muscleGroup] ?: return@forEach
            val ourEquipName  = HEVY_EQUIP_MAP[ex.equipment ?: "None"] ?: "Other"

            val muscleId = muscleNameToId[ourMuscleName] ?: return@forEach
            val equipId  = equipNameToId[ourEquipName]
                ?: equipNameToId["Other"]
                ?: return@forEach

            // Use the first image URL if available; strip the logo SVG (already filtered in merge step)
            val imageUrl = ex.imageUrls.firstOrNull()?.takeIf { it.isNotBlank() }
            val videoUrl = ex.videoUrls.firstOrNull()?.takeIf { it.isNotBlank() }

            // Derive difficulty — Hevy detail pages don't expose difficulty directly;
            // default to BEGINNER so it can be updated later via PUT /exercises/{id}
            val difficulty = when (ex.difficulty.lowercase()) {
                "beginner", "easy"         -> Difficulty.BEGINNER
                "intermediate", "moderate" -> Difficulty.INTERMEDIATE
                "advanced", "hard"         -> Difficulty.ADVANCED
                else                       -> Difficulty.BEGINNER
            }

            // Build description from primary muscle tag if available
            val description = if (ex.tags.isNotEmpty()) {
                "Primary muscle: ${ex.tags.firstOrNull() ?: ex.muscleGroup}"
            } else ""

            exerciseRepo.save(
                Exercise(
                    name             = ex.name,
                    description      = description,
                    primaryMuscleId  = muscleId,
                    secondaryMuscles = ex.secondaryMuscles,
                    equipmentId      = equipId,
                    difficulty       = difficulty,
                    instructions     = ex.instructions.ifEmpty {
                        listOf("Perform with proper form and controlled movement.")
                    },
                    tips             = ex.tips,
                    imageUrl         = imageUrl,
                    videoUrl         = videoUrl,
                    tags             = ex.tags,
                    createdBy        = creatorId,
                    createdAt        = "",
                    updatedAt        = ""
                )
            )
            inserted++
        }
        return inserted
    }

    // ---------------------------------------------------------------------------
    // Load scraped exercise JSON from classpath (bundled in src/main/resources/data/)
    // ---------------------------------------------------------------------------

    private data class HevyExerciseJson(
        val name:             String,
        val muscleGroup:      String,
        val equipment:        String?,
        val instructions:     List<String> = emptyList(),
        val tips:             List<String> = emptyList(),
        val secondaryMuscles: List<String> = emptyList(),
        val imageUrls:        List<String> = emptyList(),
        val videoUrls:        List<String> = emptyList(),
        val tags:             List<String> = emptyList(),
        val difficulty:       String = "",
        val sourceUrl:        String = ""
    )

    private fun loadHevyExercises(): List<HevyExerciseJson> {
        return try {
            val resource = ClassPathResource("data/hevy_exercises_merged.json")
            resource.inputStream.use { stream ->
                mapper.readValue(stream, object : TypeReference<List<HevyExerciseJson>>() {})
            }
        } catch (e: Exception) {
            // Fall back to empty list if the resource is missing
            emptyList()
        }
    }

    // ---------------------------------------------------------------------------
    // Hevy muscle-group name → our MuscleGroups.displayName
    // ---------------------------------------------------------------------------

    private val HEVY_MUSCLE_MAP = mapOf(
        "Abdominals" to "Core / Abs",
        "Biceps"     to "Biceps",
        "Calves"     to "Calves",
        "Chest"      to "Chest",
        "Forearms"   to "Forearms",
        "Full Body"  to "Full Body",
        "Glutes"     to "Glutes",
        "Hamstrings" to "Hamstrings",
        "Lats"       to "Latissimus Dorsi",
        "Lower Back" to "Lower Back",
        "Quadriceps" to "Quadriceps",
        "Shoulders"  to "Shoulders",
        "Traps"      to "Trapezius",
        "Triceps"    to "Triceps",
        "Upper Back" to "Upper Back"
    )

    // ---------------------------------------------------------------------------
    // Hevy equipment name → our Equipment.displayName
    // ---------------------------------------------------------------------------

    private val HEVY_EQUIP_MAP = mapOf(
        "Barbell"          to "Barbell",
        "Bodyweight"       to "Bodyweight",
        "Dumbbell"         to "Dumbbell",
        "Kettlebell"       to "Kettlebell",
        "Machine"          to "Machine",
        "None"             to "None",
        "Other"            to "Other",
        "Resistance Band"  to "Resistance Band",
        "Suspension Band"  to "Other"   // no Suspension Band in our equipment list — map to Other
    )

    // ---------------------------------------------------------------------------
    // Default lookup data
    // ---------------------------------------------------------------------------

    private fun defaultMuscleGroups() = listOf(
        MuscleGroup(displayName = "Chest",            shortName = "Chest",       description = "Pectoralis major and minor",                         bodyRegion = "UPPER_BODY"),
        MuscleGroup(displayName = "Upper Back",       shortName = "Upper Back",  description = "Rhomboids and trapezius",                            bodyRegion = "UPPER_BODY"),
        MuscleGroup(displayName = "Lower Back",       shortName = "Lower Back",  description = "Erector spinae",                                     bodyRegion = "UPPER_BODY"),
        MuscleGroup(displayName = "Latissimus Dorsi", shortName = "Lats",        description = "Large flat muscles of the back",                     bodyRegion = "UPPER_BODY"),
        MuscleGroup(displayName = "Shoulders",        shortName = "Shoulders",   description = "Anterior, lateral, and rear deltoids",               bodyRegion = "UPPER_BODY"),
        MuscleGroup(displayName = "Biceps",           shortName = "Biceps",      description = "Biceps brachii",                                     bodyRegion = "UPPER_BODY"),
        MuscleGroup(displayName = "Triceps",          shortName = "Triceps",     description = "Triceps brachii",                                    bodyRegion = "UPPER_BODY"),
        MuscleGroup(displayName = "Forearms",         shortName = "Forearms",    description = "Forearm flexors and extensors",                      bodyRegion = "UPPER_BODY"),
        MuscleGroup(displayName = "Trapezius",        shortName = "Traps",       description = "Trapezius muscles of the upper back and neck",        bodyRegion = "UPPER_BODY"),
        MuscleGroup(displayName = "Quadriceps",       shortName = "Quads",       description = "Four muscles of the front thigh",                    bodyRegion = "LOWER_BODY"),
        MuscleGroup(displayName = "Hamstrings",       shortName = "Hamstrings",  description = "Posterior thigh muscles",                            bodyRegion = "LOWER_BODY"),
        MuscleGroup(displayName = "Glutes",           shortName = "Glutes",      description = "Gluteus maximus and medius",                         bodyRegion = "LOWER_BODY"),
        MuscleGroup(displayName = "Calves",           shortName = "Calves",      description = "Gastrocnemius and soleus",                           bodyRegion = "LOWER_BODY"),
        MuscleGroup(displayName = "Core / Abs",       shortName = "Core",        description = "Rectus abdominis and obliques",                      bodyRegion = "CORE"),
        MuscleGroup(displayName = "Full Body",        shortName = "Full Body",   description = "Compound full-body movement",                        bodyRegion = "FULL_BODY"),
        MuscleGroup(displayName = "Cardio",           shortName = "Cardio",      description = "Cardiovascular and aerobic exercises",               bodyRegion = "FULL_BODY"),
        MuscleGroup(displayName = "Other",            shortName = "Other",       description = "Miscellaneous muscle groups",                        bodyRegion = "OTHER"),
    )

    private fun defaultEquipment() = listOf(
        Equipment(displayName = "Barbell",          description = "Standard Olympic barbell",             category = "FREE_WEIGHTS"),
        Equipment(displayName = "Dumbbell",         description = "Pair of dumbbells",                    category = "FREE_WEIGHTS"),
        Equipment(displayName = "Kettlebell",       description = "Cast iron kettlebell",                 category = "FREE_WEIGHTS"),
        Equipment(displayName = "EZ Bar",           description = "Cambered curl bar",                    category = "FREE_WEIGHTS"),
        Equipment(displayName = "Trap Bar",         description = "Hexagonal deadlift bar",               category = "FREE_WEIGHTS"),
        Equipment(displayName = "Cable Machine",    description = "Cable pulley system",                  category = "MACHINES"),
        Equipment(displayName = "Machine",          description = "Plate-loaded or selectorised machine", category = "MACHINES"),
        Equipment(displayName = "Smith Machine",    description = "Barbell on guided vertical rails",     category = "MACHINES"),
        Equipment(displayName = "Bodyweight",       description = "No equipment required",                category = "BODYWEIGHT"),
        Equipment(displayName = "Pull-up Bar",      description = "Horizontal bar for pulling exercises", category = "BODYWEIGHT"),
        Equipment(displayName = "Resistance Band",  description = "Elastic resistance band",              category = "OTHER"),
        Equipment(displayName = "Treadmill",        description = "Motorised running belt",               category = "CARDIO_MACHINES"),
        Equipment(displayName = "Other",            description = "Other equipment",                      category = "OTHER"),
        Equipment(displayName = "None",             description = "No equipment needed",                  category = "BODYWEIGHT"),
    )
}
