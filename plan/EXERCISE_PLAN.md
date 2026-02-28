# Exercise Library + Workout Logging — Feature Plan

> **Reference**: [Hevy App Exercise Library](https://www.hevyapp.com/exercises/) — the UX model for this feature.

---

## Overview

Two-phase feature expansion for GymJournal:

| Phase | Feature | Status |
|---|---|---|
| 1 | Exercise Library (community database) | Planned |
| 2 | Workout Logging (sessions, sets, personal bests) | Planned |

Both phases follow the existing module pattern:
```
Controller → Service → Repository → CatalystDataStoreRepository
```

---

## Why Lookup Tables for MuscleGroups and Equipment (not Kotlin enums)

`MuscleGroup` and `Equipment` are **DataStore tables**, not Kotlin enums.

Adding a new piece of equipment (e.g., "Landmine Attachment") or a new muscle group should be a database operation — not a code change + redeploy. For a community app this matters: the library grows organically, and the app owner or power users can extend it without touching server code.

`Difficulty` stays as a Kotlin enum — there are exactly 3 levels (Beginner, Intermediate, Advanced), this is truly fixed, and it needs no metadata.

**Storage pattern** — Exercise still stores the slug string (`"LATS"`, `"BARBELL"`) in its own columns. No JOIN is ever needed for exercise queries. Lookup tables are only touched when loading the category/equipment list, or validating a new exercise creation.

---

## Phase 1 — Exercise Library

### Access Rules

| Action | Who |
|---|---|
| Browse / Search / Get exercises | Any authenticated user |
| Create exercise | Any authenticated user |
| Edit exercise | Only the creator |
| Delete exercise | Only the creator |
| Get/List categories and equipment | Any authenticated user |
| Create new category or equipment type | Any authenticated user (community contribution) |

Private exercises (`isPublic = 0`) are only visible to their creator.

---

### Domain Models

#### `model/domain/Lookup.kt`

```kotlin
package dev.eknath.GymJournal.model.domain

enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }

data class MuscleGroup(
    val id: Long? = null,
    val slug: String,           // e.g. "LATS" — stored in Exercise.primaryMuscleSlug
    val displayName: String,    // e.g. "Latissimus Dorsi (Lats)"
    val shortName: String,      // e.g. "Lats"
    val description: String,
    val bodyRegion: String,     // UPPER_BODY | LOWER_BODY | CORE | FULL_BODY | OTHER
    val imageUrl: String?
)

data class Equipment(
    val id: Long? = null,
    val slug: String,           // e.g. "BARBELL" — stored in Exercise.equipmentSlug
    val displayName: String,    // e.g. "Barbell"
    val description: String,
    val category: String,       // FREE_WEIGHTS | MACHINES | BODYWEIGHT | CARDIO_MACHINES | OTHER
    val imageUrl: String?
)
```

#### `model/domain/Exercise.kt`

```kotlin
package dev.eknath.GymJournal.model.domain

data class Exercise(
    val id: Long? = null,
    val name: String,
    val description: String,
    val primaryMuscleSlug: String,          // FK (conceptual) → MuscleGroups.slug, e.g. "LATS"
    val secondaryMuscles: List<String>,     // named muscles, stored as JSON string
    val equipmentSlug: String,              // FK (conceptual) → Equipment.slug, e.g. "BARBELL"
    val difficulty: Difficulty,
    val instructions: List<String>,         // ordered steps, stored as JSON string
    val tips: List<String>,                 // coaching cues / mistakes, stored as JSON string
    val imageUrl: String?,
    val videoUrl: String?,
    val tags: List<String>,                 // stored as JSON string
    val createdBy: String,                  // userId
    val isPublic: Int,                      // 1 = community, 0 = private
    val createdAt: String,
    val updatedAt: String
)
```

#### Storage Strategy for List Fields

DataStore has no array type. `List<String>` fields are serialised to JSON strings via Jackson `ObjectMapper`:

| Field | Stored as |
|---|---|
| `instructions` | `["Step 1: Grip the bar...", "Step 2: ..."]` |
| `secondaryMuscles` | `["Rhomboids", "Trapezius", "Biceps"]` |
| `tips` | `["Keep core tight", "Full ROM"]` |
| `tags` | `["compound", "pull", "bodyweight"]` |

---

### DataStore Tables

> Create all 3 tables in the Catalyst Console before deploying Phase 1.

#### `MuscleGroups`

| Column | Type | Notes |
|---|---|---|
| ROWID | Long | Auto |
| slug | String | Unique identifier, e.g. `"LATS"` |
| displayName | String | e.g. `"Latissimus Dorsi (Lats)"` |
| shortName | String | e.g. `"Lats"` — for compact list views |
| description | String | What this muscle group does |
| bodyRegion | String | `UPPER_BODY \| LOWER_BODY \| CORE \| FULL_BODY \| OTHER` |
| imageUrl | String | Optional muscle diagram |

#### Initial MuscleGroups seed data

| slug | displayName | shortName | bodyRegion |
|---|---|---|---|
| CHEST | Chest | Chest | UPPER_BODY |
| UPPER_BACK | Upper Back | Upper Back | UPPER_BODY |
| LOWER_BACK | Lower Back | Lower Back | UPPER_BODY |
| LATS | Latissimus Dorsi | Lats | UPPER_BODY |
| SHOULDERS | Shoulders | Shoulders | UPPER_BODY |
| BICEPS | Biceps | Biceps | UPPER_BODY |
| TRICEPS | Triceps | Triceps | UPPER_BODY |
| FOREARMS | Forearms | Forearms | UPPER_BODY |
| QUADRICEPS | Quadriceps | Quads | LOWER_BODY |
| HAMSTRINGS | Hamstrings | Hamstrings | LOWER_BODY |
| GLUTES | Glutes | Glutes | LOWER_BODY |
| CALVES | Calves | Calves | LOWER_BODY |
| CORE | Core / Abs | Core | CORE |
| FULL_BODY | Full Body | Full Body | FULL_BODY |
| CARDIO | Cardio | Cardio | FULL_BODY |
| OTHER | Other | Other | OTHER |

#### `Equipment`

| Column | Type | Notes |
|---|---|---|
| ROWID | Long | Auto |
| slug | String | Unique identifier, e.g. `"BARBELL"` |
| displayName | String | e.g. `"Barbell"` |
| description | String | What this equipment is |
| category | String | `FREE_WEIGHTS \| MACHINES \| BODYWEIGHT \| CARDIO_MACHINES \| OTHER` |
| imageUrl | String | Optional equipment photo |

#### Initial Equipment seed data

| slug | displayName | category |
|---|---|---|
| BARBELL | Barbell | FREE_WEIGHTS |
| DUMBBELL | Dumbbell | FREE_WEIGHTS |
| KETTLEBELL | Kettlebell | FREE_WEIGHTS |
| EZ_BAR | EZ Bar | FREE_WEIGHTS |
| TRAP_BAR | Trap Bar | FREE_WEIGHTS |
| CABLE | Cable Machine | MACHINES |
| MACHINE | Machine | MACHINES |
| SMITH_MACHINE | Smith Machine | MACHINES |
| BODYWEIGHT | Bodyweight | BODYWEIGHT |
| PULLUP_BAR | Pull-up Bar | BODYWEIGHT |
| RESISTANCE_BAND | Resistance Band | OTHER |
| TREADMILL | Treadmill | CARDIO_MACHINES |
| OTHER | Other | OTHER |
| NONE | None | BODYWEIGHT |

#### `Exercises`

| Column | Type | Notes |
|---|---|---|
| ROWID | Long | Auto |
| name | String | Required |
| description | String | |
| primaryMuscleSlug | String | e.g. `"LATS"` |
| secondaryMuscles | String | JSON array string |
| equipmentSlug | String | e.g. `"BARBELL"` |
| difficulty | String | `"BEGINNER" \| "INTERMEDIATE" \| "ADVANCED"` |
| instructions | String | JSON array string |
| tips | String | JSON array string |
| imageUrl | String | Optional |
| videoUrl | String | Optional |
| tags | String | JSON array string |
| createdBy | String | userId |
| isPublic | Int | `1` = public, `0` = private |
| createdAt | String | `yyyy-MM-dd HH:mm:ss` |
| updatedAt | String | `yyyy-MM-dd HH:mm:ss` |

---

### Seeding Initial Data

Since the Catalyst SDK cannot be used at startup (no request context = no ZGS headers), a `CommandLineRunner` won't work. Use a protected seed endpoint instead:

```
POST /api/v1/admin/seed
```

- Authenticated (any user, called once by developer post-deploy)
- Checks if MuscleGroups table is empty → inserts defaults
- Checks if Equipment table is empty → inserts defaults
- Idempotent — safe to call multiple times

---

### API Endpoints

**Exercises:**

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/exercises` | Required | Browse/search exercises (paginated) |
| GET | `/api/v1/exercises/{id}` | Required | Full exercise detail |
| POST | `/api/v1/exercises` | Required | Create exercise |
| PUT | `/api/v1/exercises/{id}` | Required | Update (creator only) |
| DELETE | `/api/v1/exercises/{id}` | Required | Delete (creator only) |

**GET /api/v1/exercises query params:**

| Param | Description |
|---|---|
| `category` | Filter by muscle group slug (e.g. `LATS`) |
| `equipment` | Filter by equipment slug (e.g. `BARBELL`) |
| `difficulty` | Filter by difficulty (e.g. `INTERMEDIATE`) |
| `search` | Substring match on name (in-memory after ZCQL fetch) |
| `mine` | `true` = only the calling user's exercises |
| `page` | Default 1 |
| `pageSize` | Default 20, max 50 |

**Lookup (categories / equipment):**

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/exercises/categories` | List all muscle groups (full metadata) |
| POST | `/api/v1/exercises/categories` | Add a new muscle group |
| GET | `/api/v1/exercises/equipment` | List all equipment types (full metadata) |
| POST | `/api/v1/exercises/equipment` | Add a new equipment type |

---

### Response Shapes

#### `MuscleGroupResponse`
```json
{
  "id": 1,
  "slug": "LATS",
  "displayName": "Latissimus Dorsi",
  "shortName": "Lats",
  "description": "Large flat muscles of the back...",
  "bodyRegion": "UPPER_BODY",
  "imageUrl": null
}
```

#### `ExerciseResponse`
```json
{
  "id": 12345,
  "name": "Pull Up",
  "description": "One of the most effective bodyweight exercises...",
  "primaryMuscleSlug": "LATS",
  "secondaryMuscles": ["Rhomboids", "Trapezius", "Biceps"],
  "equipmentSlug": "PULLUP_BAR",
  "difficulty": "INTERMEDIATE",
  "instructions": [
    "Grip the bar slightly wider than shoulder width",
    "Retract shoulders and engage core",
    "Pull up until chin clears the bar",
    "Lower slowly to full extension"
  ],
  "tips": [
    "Keep shoulders retracted throughout",
    "Avoid swinging — control both phases"
  ],
  "imageUrl": null,
  "videoUrl": null,
  "tags": ["compound", "pull", "bodyweight"],
  "createdBy": "userId123",
  "isPublic": true,
  "createdAt": "2026-02-28T10:00:00",
  "updatedAt": "2026-02-28T10:00:00"
}
```

---

### Files to Create (Phase 1)

| File | Description |
|---|---|
| `model/domain/Lookup.kt` | `MuscleGroup`, `Equipment` data classes + `Difficulty` enum |
| `model/domain/Exercise.kt` | `Exercise` data class (stores slugs as strings) |
| `model/dto/ExerciseDtos.kt` | All request/response DTOs |
| `modules/exercises/MuscleGroupRepository.kt` | CRUD for MuscleGroups table |
| `modules/exercises/EquipmentRepository.kt` | CRUD for Equipment table |
| `modules/exercises/ExerciseRepository.kt` | Exercise CRUD + Jackson list serialisation |
| `modules/exercises/ExerciseService.kt` | Business logic, ownership checks, search, pagination |
| `modules/exercises/ExerciseController.kt` | All REST endpoints |
| `modules/admin/AdminController.kt` | Seed endpoint |

---

## Phase 2 — Workout Logging

### Domain Models (`model/domain/Workout.kt`)

```kotlin
data class WorkoutSession(
    val id: Long? = null,
    val userId: String,
    val name: String,
    val date: String,       // yyyy-MM-dd
    val startedAt: String,
    val endedAt: String?,   // empty until finished
    val notes: String
)

data class WorkoutSet(
    val id: Long? = null,
    val sessionId: Long,
    val userId: String,         // denormalised
    val exerciseId: Long,
    val exerciseName: String,   // denormalised
    val orderInSession: Int,    // groups sets for same exercise
    val setNumber: Int,
    val reps: Int,
    val weightKg: String,       // decimal string "102.5", "0" for bodyweight
    val isBodyweight: Int,      // 0/1
    val durationSeconds: Int,   // 0 if N/A
    val distanceMeters: String, // "0" if N/A
    val rpe: Int,               // 0 = not set
    val isPersonalBest: Int,    // 0/1
    val notes: String,
    val completedAt: String
)
```

### DataStore Tables

#### `WorkoutSessions`

| Column | Type | Notes |
|---|---|---|
| ROWID | Long | Auto |
| userId | String | |
| name | String | e.g. "Push Day" |
| date | String | `yyyy-MM-dd` |
| startedAt | String | `yyyy-MM-dd HH:mm:ss` |
| endedAt | String | Empty until finished |
| notes | String | |

#### `WorkoutSets`

| Column | Type | Notes |
|---|---|---|
| ROWID | Long | Auto |
| sessionId | Long | FK → WorkoutSessions.ROWID |
| userId | String | Denormalised |
| exerciseId | Long | FK → Exercises.ROWID |
| exerciseName | String | Denormalised |
| orderInSession | Int | Exercise grouping |
| setNumber | Int | |
| reps | Int | |
| weightKg | String | Decimal string |
| isBodyweight | Int | 0/1 |
| durationSeconds | Int | 0 if N/A |
| distanceMeters | String | "0" if N/A |
| rpe | Int | 0 = not set |
| isPersonalBest | Int | 0/1 |
| notes | String | |
| completedAt | String | `yyyy-MM-dd HH:mm:ss` |

### API Endpoints (Phase 2)

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/workouts` | Create session |
| GET | `/api/v1/workouts` | List sessions (paginated, recent first) |
| GET | `/api/v1/workouts/{id}` | Full session with sets grouped by exercise |
| PUT | `/api/v1/workouts/{id}` | Update session (name, endedAt, notes) |
| DELETE | `/api/v1/workouts/{id}` | Delete session + all sets |
| POST | `/api/v1/workouts/{id}/sets` | Add set to session |
| PUT | `/api/v1/workouts/{sessionId}/sets/{setId}` | Update set |
| DELETE | `/api/v1/workouts/{sessionId}/sets/{setId}` | Delete set |
| GET | `/api/v1/exercises/{id}/history` | Personal set history for an exercise |
| GET | `/api/v1/exercises/{id}/pbs` | All-time personal bests |

---

## Shared Implementation Notes

### Jackson for List Fields

```kotlin
@Repository
class ExerciseRepository(
    private val db: CatalystDataStoreRepository,
    private val mapper: ObjectMapper
) {
    private fun List<String>.toJson(): String = mapper.writeValueAsString(this)
    private fun String?.toStringList(): List<String> =
        if (isNullOrBlank() || this == "null") emptyList()
        else try { mapper.readValue(this, object : TypeReference<List<String>>() {}) }
             catch (_: Exception) { emptyList() }
}
```

### ZCQL Search (no full-text support)

1. Apply filters via ZCQL: `primaryMuscleSlug`, `equipmentSlug`, `difficulty`, `isPublic`, `createdBy`
2. Filter name keyword in memory: `.filter { it.name.contains(search, ignoreCase = true) }`
3. Paginate in memory after filter → return `ApiMeta(page, pageSize, total)`

### Datetime Consistency

| Direction | Format | Conversion |
|---|---|---|
| Store | `yyyy-MM-dd HH:mm:ss` | `.replace("T", " ")` on inbound ISO-8601 |
| Respond | `yyyy-MM-ddTHH:mm:ss` | `.replace(" ", "T")` in `toResponse()` |

### Personal Best Detection (Phase 2)

On set save (reps > 0, weightKg > "0"):
1. Query `WorkoutSets WHERE userId = X AND exerciseId = Y AND reps = Z ORDER BY weightKg DESC LIMIT 1`
2. If new weight ≥ stored best → `isPersonalBest = 1`
3. Also compare estimated 1RM via Epley: `1RM = weight × (1 + reps / 30.0)`

---

## Implementation Order

### Phase 1
1. `model/domain/Lookup.kt`
2. `model/domain/Exercise.kt`
3. `model/dto/ExerciseDtos.kt`
4. `modules/exercises/MuscleGroupRepository.kt`
5. `modules/exercises/EquipmentRepository.kt`
6. `modules/exercises/ExerciseRepository.kt`
7. `modules/exercises/ExerciseService.kt`
8. `modules/exercises/ExerciseController.kt`
9. `modules/admin/AdminController.kt` (seed endpoint)
10. `./gradlew build` → create Catalyst tables → call seed endpoint → test

### Phase 2
11. `model/domain/Workout.kt`
12. `model/dto/WorkoutDtos.kt`
13. `modules/workouts/WorkoutSessionRepository.kt`
14. `modules/workouts/WorkoutSetRepository.kt`
15. `modules/workouts/WorkoutService.kt`
16. `modules/workouts/WorkoutController.kt`
17. `./gradlew build` → create Catalyst tables → test

---

## Verification Checklist

### Phase 1
- [ ] Seed: `POST /api/v1/admin/seed` inserts 16 muscle groups + 14 equipment types
- [ ] `GET /api/v1/exercises/categories` returns full metadata objects
- [ ] `GET /api/v1/exercises/equipment` returns full metadata objects
- [ ] `POST /api/v1/exercises/categories` adds a new muscle group
- [ ] `POST /api/v1/exercises` creates exercise, list fields properly deserialised in response
- [ ] `GET /api/v1/exercises?category=LATS` filters correctly
- [ ] `GET /api/v1/exercises?search=pull` returns correct exercises
- [ ] `GET /api/v1/exercises?page=2&pageSize=5` correct pagination + meta
- [ ] `GET /api/v1/exercises/{id}` returns full detail
- [ ] `PUT /api/v1/exercises/{id}` with wrong user → HTTP 403
- [ ] Private exercise not visible to other users

### Phase 2
- [ ] `POST /api/v1/workouts` creates session
- [ ] `POST /api/v1/workouts/{id}/sets` adds set, detects personal best
- [ ] `GET /api/v1/workouts/{id}` groups sets by orderInSession
- [ ] `GET /api/v1/exercises/{id}/pbs` returns best set per rep count
- [ ] `DELETE /api/v1/workouts/{id}` deletes session + all sets
- [ ] Wrong user operations → HTTP 403
