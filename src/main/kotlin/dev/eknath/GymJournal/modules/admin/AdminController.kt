package dev.eknath.GymJournal.modules.admin

import dev.eknath.GymJournal.model.domain.Equipment
import dev.eknath.GymJournal.model.domain.MuscleGroup
import dev.eknath.GymJournal.modules.exercises.EquipmentRepository
import dev.eknath.GymJournal.modules.exercises.MuscleGroupRepository
import dev.eknath.GymJournal.util.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * One-time seed endpoint to populate the MuscleGroups and Equipment lookup tables.
 *
 * Call once after deploying to a fresh environment:
 *   POST /api/v1/admin/seed
 *
 * Idempotent — only inserts if the table is empty. Safe to call multiple times.
 * Requires standard API auth (zcauthtoken cookie validated by ZGS).
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val muscleGroupRepo: MuscleGroupRepository,
    private val equipmentRepo: EquipmentRepository
) {

    @PostMapping("/seed")
    fun seed(): ApiResponse<*> {
        val musclesSeeded = seedMuscleGroups()
        val equipmentSeeded = seedEquipment()
        return ApiResponse.ok(
            mapOf(
                "muscleGroupsInserted" to musclesSeeded,
                "equipmentInserted"    to equipmentSeeded,
                "message"              to "Seed complete"
            )
        )
    }

    private fun seedMuscleGroups(): Int {
        if (muscleGroupRepo.count() > 0) return 0   // already seeded
        defaultMuscleGroups().forEach { muscleGroupRepo.save(it) }
        return defaultMuscleGroups().size
    }

    private fun seedEquipment(): Int {
        if (equipmentRepo.count() > 0) return 0     // already seeded
        defaultEquipment().forEach { equipmentRepo.save(it) }
        return defaultEquipment().size
    }

    // ---------------------------------------------------------------------------
    // Default data
    // ---------------------------------------------------------------------------

    private fun defaultMuscleGroups() = listOf(
        MuscleGroup(displayName="Chest",             shortName="Chest",       description="Pectoralis major and minor",                 bodyRegion="UPPER_BODY"),
        MuscleGroup(displayName="Upper Back",        shortName="Upper Back",  description="Rhomboids and trapezius",                    bodyRegion="UPPER_BODY"),
        MuscleGroup(displayName="Lower Back",        shortName="Lower Back",  description="Erector spinae",                             bodyRegion="UPPER_BODY"),
        MuscleGroup(displayName="Latissimus Dorsi",  shortName="Lats",        description="Large flat muscles of the back",             bodyRegion="UPPER_BODY"),
        MuscleGroup(displayName="Shoulders",         shortName="Shoulders",   description="Anterior, lateral, and rear deltoids",       bodyRegion="UPPER_BODY"),
        MuscleGroup(displayName="Biceps",            shortName="Biceps",      description="Biceps brachii",                             bodyRegion="UPPER_BODY"),
        MuscleGroup(displayName="Triceps",           shortName="Triceps",     description="Triceps brachii",                            bodyRegion="UPPER_BODY"),
        MuscleGroup(displayName="Forearms",          shortName="Forearms",    description="Forearm flexors and extensors",              bodyRegion="UPPER_BODY"),
        MuscleGroup(displayName="Quadriceps",        shortName="Quads",       description="Four muscles of the front thigh",            bodyRegion="LOWER_BODY"),
        MuscleGroup(displayName="Hamstrings",        shortName="Hamstrings",  description="Posterior thigh muscles",                    bodyRegion="LOWER_BODY"),
        MuscleGroup(displayName="Glutes",            shortName="Glutes",      description="Gluteus maximus and medius",                 bodyRegion="LOWER_BODY"),
        MuscleGroup(displayName="Calves",            shortName="Calves",      description="Gastrocnemius and soleus",                   bodyRegion="LOWER_BODY"),
        MuscleGroup(displayName="Core / Abs",        shortName="Core",        description="Rectus abdominis and obliques",              bodyRegion="CORE"),
        MuscleGroup(displayName="Full Body",         shortName="Full Body",   description="Compound full-body movement",                bodyRegion="FULL_BODY"),
        MuscleGroup(displayName="Cardio",            shortName="Cardio",      description="Cardiovascular and aerobic exercises",       bodyRegion="FULL_BODY"),
        MuscleGroup(displayName="Other",             shortName="Other",       description="Miscellaneous muscle groups",                bodyRegion="OTHER"),
    )

    private fun defaultEquipment() = listOf(
        Equipment(displayName="Barbell",          description="Standard Olympic barbell",              category="FREE_WEIGHTS"),
        Equipment(displayName="Dumbbell",         description="Pair of dumbbells",                     category="FREE_WEIGHTS"),
        Equipment(displayName="Kettlebell",       description="Cast iron kettlebell",                  category="FREE_WEIGHTS"),
        Equipment(displayName="EZ Bar",           description="Cambered curl bar",                     category="FREE_WEIGHTS"),
        Equipment(displayName="Trap Bar",         description="Hexagonal deadlift bar",                category="FREE_WEIGHTS"),
        Equipment(displayName="Cable Machine",    description="Cable pulley system",                   category="MACHINES"),
        Equipment(displayName="Machine",          description="Plate-loaded or selectorised machine",  category="MACHINES"),
        Equipment(displayName="Smith Machine",    description="Barbell on guided vertical rails",      category="MACHINES"),
        Equipment(displayName="Bodyweight",       description="No equipment required",                 category="BODYWEIGHT"),
        Equipment(displayName="Pull-up Bar",      description="Horizontal bar for pulling exercises",  category="BODYWEIGHT"),
        Equipment(displayName="Resistance Band",  description="Elastic resistance band",               category="OTHER"),
        Equipment(displayName="Treadmill",        description="Motorised running belt",                category="CARDIO_MACHINES"),
        Equipment(displayName="Other",            description="Other equipment",                       category="OTHER"),
        Equipment(displayName="None",             description="No equipment needed",                   category="BODYWEIGHT"),
    )
}
