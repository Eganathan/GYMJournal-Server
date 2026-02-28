package dev.eknath.GymJournal.modules.exercises

import dev.eknath.GymJournal.model.domain.Equipment
import dev.eknath.GymJournal.model.domain.Exercise
import dev.eknath.GymJournal.model.domain.MuscleGroup
import dev.eknath.GymJournal.model.dto.*
import dev.eknath.GymJournal.util.ApiMeta
import org.springframework.stereotype.Service

@Service
class ExerciseService(
    private val exerciseRepo: ExerciseRepository,
    private val muscleGroupRepo: MuscleGroupRepository,
    private val equipmentRepo: EquipmentRepository
) {

    // ---------------------------------------------------------------------------
    // Lookup endpoints
    // ---------------------------------------------------------------------------

    fun getAllMuscleGroups(): List<MuscleGroupResponse> =
        muscleGroupRepo.findAll().map { it.toResponse() }

    fun getAllEquipment(): List<EquipmentResponse> =
        equipmentRepo.findAll().map { it.toResponse() }

    fun addMuscleGroup(request: CreateMuscleGroupRequest): MuscleGroupResponse {
        if (muscleGroupRepo.existsBySlug(request.slug))
            throw IllegalArgumentException("A muscle group with slug '${request.slug}' already exists")
        val saved = muscleGroupRepo.save(
            MuscleGroup(
                slug        = request.slug.uppercase().trim(),
                displayName = request.displayName.trim(),
                shortName   = request.shortName.trim(),
                description = request.description.trim(),
                bodyRegion  = request.bodyRegion.uppercase().trim(),
                imageUrl    = request.imageUrl?.takeIf { it.isNotBlank() }
            )
        )
        return saved.toResponse()
    }

    fun addEquipment(request: CreateEquipmentRequest): EquipmentResponse {
        if (equipmentRepo.existsBySlug(request.slug))
            throw IllegalArgumentException("Equipment with slug '${request.slug}' already exists")
        val saved = equipmentRepo.save(
            Equipment(
                slug        = request.slug.uppercase().trim(),
                displayName = request.displayName.trim(),
                description = request.description.trim(),
                category    = request.category.uppercase().trim(),
                imageUrl    = request.imageUrl?.takeIf { it.isNotBlank() }
            )
        )
        return saved.toResponse()
    }

    // ---------------------------------------------------------------------------
    // Exercise CRUD
    // ---------------------------------------------------------------------------

    fun listExercises(
        callingUserId: String,
        category: String?,
        equipment: String?,
        difficulty: String?,
        search: String?,
        onlyMine: Boolean,
        page: Int,
        pageSize: Int
    ): Pair<List<ExerciseSummaryResponse>, ApiMeta> {
        val all = exerciseRepo.findAll(
            callingUserId = callingUserId,
            muscleSlug    = category?.uppercase(),
            equipmentSlug = equipment?.uppercase(),
            difficulty    = difficulty?.uppercase(),
            onlyMine      = onlyMine
        )

        // In-memory name search (ZCQL has no reliable full-text support)
        val filtered = if (!search.isNullOrBlank())
            all.filter { it.name.contains(search.trim(), ignoreCase = true) }
        else all

        val total = filtered.size.toLong()
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        val paginated = filtered.drop((safePage - 1) * safeSize).take(safeSize)

        return paginated.map { it.toSummaryResponse() } to
            ApiMeta(page = safePage, pageSize = safeSize, total = total)
    }

    fun getExercise(callingUserId: String, id: Long): ExerciseResponse {
        val exercise = exerciseRepo.findById(id)
            ?: throw NoSuchElementException("Exercise $id not found")
        return exercise.toResponse()
    }

    fun createExercise(userId: String, request: CreateExerciseRequest): ExerciseResponse {
        // Validate slugs exist in lookup tables
        if (!muscleGroupRepo.existsBySlug(request.primaryMuscleSlug))
            throw NoSuchElementException("Muscle group '${request.primaryMuscleSlug}' not found")
        if (!equipmentRepo.existsBySlug(request.equipmentSlug))
            throw NoSuchElementException("Equipment '${request.equipmentSlug}' not found")

        val exercise = Exercise(
            name               = request.name.trim(),
            description        = request.description.trim(),
            primaryMuscleSlug  = request.primaryMuscleSlug.uppercase(),
            secondaryMuscles   = request.secondaryMuscles,
            equipmentSlug      = request.equipmentSlug.uppercase(),
            difficulty         = request.difficulty,
            instructions       = request.instructions,
            tips               = request.tips,
            imageUrl           = request.imageUrl?.takeIf { it.isNotBlank() },
            videoUrl           = request.videoUrl?.takeIf { it.isNotBlank() },
            tags               = request.tags,
            createdBy          = "",   // set by Catalyst (CREATORID)
            createdAt          = "",   // set by Catalyst (CREATEDTIME)
            updatedAt          = ""    // set by Catalyst (MODIFIEDTIME)
        )
        return exerciseRepo.save(exercise).toResponse()
    }

    fun updateExercise(userId: String, id: Long, request: UpdateExerciseRequest): ExerciseResponse {
        val existing = exerciseRepo.findById(id)
            ?: throw NoSuchElementException("Exercise $id not found")
        if (existing.createdBy != userId)
            throw IllegalAccessException("Exercise $id does not belong to this user")

        // Validate changed slugs
        request.primaryMuscleSlug?.let {
            if (!muscleGroupRepo.existsBySlug(it))
                throw NoSuchElementException("Muscle group '$it' not found")
        }
        request.equipmentSlug?.let {
            if (!equipmentRepo.existsBySlug(it))
                throw NoSuchElementException("Equipment '$it' not found")
        }

        val updated = existing.copy(
            name              = request.name?.trim() ?: existing.name,
            description       = request.description?.trim() ?: existing.description,
            primaryMuscleSlug = request.primaryMuscleSlug?.uppercase() ?: existing.primaryMuscleSlug,
            secondaryMuscles  = request.secondaryMuscles ?: existing.secondaryMuscles,
            equipmentSlug     = request.equipmentSlug?.uppercase() ?: existing.equipmentSlug,
            difficulty        = request.difficulty ?: existing.difficulty,
            instructions      = request.instructions ?: existing.instructions,
            tips              = request.tips ?: existing.tips,
            imageUrl          = request.imageUrl?.takeIf { it.isNotBlank() } ?: existing.imageUrl,
            videoUrl          = request.videoUrl?.takeIf { it.isNotBlank() } ?: existing.videoUrl,
            tags              = request.tags ?: existing.tags
        )
        exerciseRepo.update(id, updated)
        return (exerciseRepo.findById(id) ?: updated).toResponse()
    }

    fun deleteExercise(userId: String, id: Long) {
        val existing = exerciseRepo.findById(id)
            ?: throw NoSuchElementException("Exercise $id not found")
        if (existing.createdBy != userId)
            throw IllegalAccessException("Exercise $id does not belong to this user")
        exerciseRepo.delete(id)
    }

    // ---------------------------------------------------------------------------
    // Mappers
    // ---------------------------------------------------------------------------

    private fun MuscleGroup.toResponse() = MuscleGroupResponse(
        id          = id ?: 0,
        slug        = slug,
        displayName = displayName,
        shortName   = shortName,
        description = description,
        bodyRegion  = bodyRegion,
        imageUrl    = imageUrl
    )

    private fun Equipment.toResponse() = EquipmentResponse(
        id          = id ?: 0,
        slug        = slug,
        displayName = displayName,
        description = description,
        category    = category,
        imageUrl    = imageUrl
    )

    private fun Exercise.toResponse() = ExerciseResponse(
        id                = id ?: 0,
        name              = name,
        description       = description,
        primaryMuscleSlug = primaryMuscleSlug,
        secondaryMuscles  = secondaryMuscles,
        equipmentSlug     = equipmentSlug,
        difficulty        = difficulty.name,
        instructions      = instructions,
        tips              = tips,
        imageUrl          = imageUrl,
        videoUrl          = videoUrl,
        tags              = tags,
        createdBy         = createdBy,
        createdAt         = createdAt.replace(" ", "T"),
        updatedAt         = updatedAt.replace(" ", "T")
    )

    private fun Exercise.toSummaryResponse() = ExerciseSummaryResponse(
        id                = id ?: 0,
        name              = name,
        primaryMuscleSlug = primaryMuscleSlug,
        equipmentSlug     = equipmentSlug,
        difficulty        = difficulty.name,
        createdBy         = createdBy
    )
}
