# GymJournal Server — Project Documentation

## Overview

**GymJournal** is a Kotlin/Spring Boot backend API for a personal gym and fitness journaling
application. It is designed to run on **Zoho Catalyst AppSail** (a serverless Java hosting platform)
and uses **Zoho Catalyst DataStore** as its database. The app implements hydration (water intake)
tracking, a community exercise library, body metrics logging with health insights, routine templates,
and workout session logging.

---

## Base URLs

| Service | Environment | URL |
|---|---|---|
| API (AppSail) | Development | `https://appsail-10119736618.development.catalystappsail.com` |
| API (AppSail) | Production | `https://gym.eknath.dev` |
| API (AppSail) | Local | `http://localhost:8080` |
| Web Client | Development | `https://gymjournal-778776887.development.catalystserverless.com` |
| Web Client | Production | `https://app.gym.eknath.dev` |

## API Documentation

Detailed REST API docs (request/response shapes, params, error codes) live in [`apiDocs/`](./apiDocs/index.md):

| File | Coverage |
|---|---|
| [`apiDocs/health.md`](./apiDocs/health.md) | `GET /api/v1/health` |
| [`apiDocs/hydration.md`](./apiDocs/hydration.md) | All `/api/v1/water` endpoints |
| [`apiDocs/exercises.md`](./apiDocs/exercises.md) | All `/api/v1/exercises` + `/api/v1/admin/seed` endpoints |
| [`apiDocs/metrics.md`](./apiDocs/metrics.md) | All `/api/v1/body-metrics` endpoints |
| [`apiDocs/routines.md`](./apiDocs/routines.md) | All `/api/v1/routines` endpoints |
| [`apiDocs/workouts.md`](./apiDocs/workouts.md) | All `/api/v1/workouts` + exercise history/PBs endpoints |
| [`apiDocs/media.md`](./apiDocs/media.md) | `POST /api/v1/media/upload` — image/video uploads to Catalyst FileStore |

When adding a new module, add a corresponding `.md` file in `apiDocs/` and link it here.

## Feature Docs

Comprehensive per-feature documentation (API spec + DB tables + implementation plan + frontend wiring notes).
**Always update the relevant feature doc when changing that feature.**

| File | Feature |
|---|---|
| [`features/BodyMetrics.md`](./features/BodyMetrics.md) | Body metrics logging, history, snapshot, custom metric defs |

---

## Skill Files

Reference docs for technologies used in this project:

| File | Content |
|---|---|
| [`skill/zcql.md`](./skill/zcql.md) | Complete ZCQL + DataStore reference — column types, table creation recipe format, Console navigation, full query syntax, Kotlin patterns, gotcha cheatsheet |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.3.10 |
| Framework | Spring Boot 4.1.0-SNAPSHOT |
| Security | Spring Security (stateless, ZGS-injected headers via CatalystSDK) |
| Database | Zoho Catalyst DataStore (queried via ZCQL) |
| DB SDK | Zoho Catalyst Java SDK 2.2.0 (bundled JARs in `libs/`) |
| Build | Gradle with Kotlin DSL |
| JVM | Java 17 |
| Testing | JUnit 5 |
| Deployment | Zoho Catalyst AppSail |

---

## Project Structure

```
GymJournal/Server/
├── build.gradle.kts                  # Gradle build config
├── settings.gradle.kts               # Root project name
├── libs/                             # Catalyst SDK JARs (local file dependencies)
├── app-config.json                   # Catalyst app configuration
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.properties
    │   └── kotlin/dev/eknath/GymJournal/
    │       ├── GymJournalApplication.kt          # Spring Boot entry point
    │       ├── config/
    │       │   ├── CatalystAuthFilter.kt          # Auth: reads x-zc-user-id injected by ZGS
    │       │   ├── SecurityConfig.kt              # Spring Security filter chains
    │       │   ├── CatalystConfig.kt              # Catalyst SDK notes (no startup init)
    │       │   └── CatalystPortCustomizer.kt      # Reads port from env var
    │       ├── repository/
    │       │   └── CatalystDataStoreRepository.kt # Generic DataStore CRUD wrapper
    │       ├── model/
    │       │   ├── domain/
    │       │   │   ├── WaterIntake.kt             # Domain entities
    │       │   │   ├── Exercise.kt                # Exercise + Difficulty enum
    │       │   │   ├── Lookup.kt                  # MuscleGroup + Equipment domain classes
    │       │   │   ├── BodyMetric.kt              # BodyMetricEntry + CustomMetricDef
    │       │   │   ├── Routine.kt                 # Routine + RoutineItem + RoutineItemType
    │       │   │   └── Workout.kt                 # WorkoutSession + WorkoutSet
    │       │   └── dto/
    │       │       ├── WaterIntakeDtos.kt         # Request/response DTOs
    │       │       ├── ExerciseDtos.kt            # Exercise + lookup DTOs
    │       │       ├── BodyMetricDtos.kt          # Metric entry + snapshot + custom def DTOs
    │       │       ├── RoutineDtos.kt             # Routine request/response DTOs
    │       │       ├── WorkoutDtos.kt             # Workout session + set request/response DTOs
    │       │       └── MediaDtos.kt               # UploadResponse DTO
    │       ├── util/
    │       │   ├── ApiResponse.kt                 # Unified response envelope
    │       │   ├── GlobalExceptionHandler.kt      # @RestControllerAdvice — maps exceptions to ApiResponse
    │       │   ├── ZcqlSanitizer.kt               # ZCQL injection prevention
    │       │   └── SecurityContextExtensions.kt   # currentUserId() helper
    │       └── modules/
    │           ├── health/
    │           │   └── HealthController.kt        # Public health-check endpoint
    │           ├── hydration/
    │           │   ├── WaterIntakeController.kt   # REST endpoints
    │           │   ├── WaterIntakeService.kt      # Business logic
    │           │   └── WaterIntakeRepository.kt   # DataStore queries
    │           ├── exercises/
    │           │   ├── ExerciseController.kt      # Exercise library endpoints
    │           │   ├── ExerciseService.kt         # Ownership checks, search, pagination
    │           │   ├── ExerciseRepository.kt      # Exercise CRUD + JSON list helpers
    │           │   ├── MuscleGroupRepository.kt   # MuscleGroups lookup table CRUD
    │           │   └── EquipmentRepository.kt     # Equipment lookup table CRUD
    │           ├── metrics/
    │           │   ├── BodyMetricController.kt    # All /api/v1/body-metrics endpoints (except /insights)
    │           │   ├── BodyMetricService.kt       # Batch log, history, snapshot + computed metrics
    │           │   ├── BodyMetricRepository.kt    # BodyMetricEntries DataStore queries
    │           │   └── CustomMetricDefRepository.kt # CustomMetricDefs DataStore queries
    │           ├── insights/
    │           │   ├── MetricInsightsEngine.kt    # Interface + domain types (InsightContext, MetricInsight, etc.)
    │           │   ├── CompositeInsightsEngine.kt # Aggregates all engines via Spring list injection
    │           │   ├── InsightsService.kt         # Calls snapshot → builds context → runs composite engine
    │           │   ├── InsightsController.kt      # GET /api/v1/body-metrics/insights
    │           │   └── engines/
    │           │       └── ReferenceRangeInsightsEngine.kt  # @Order(1) engine; clinical ref ranges (WHO/ACC/ADA)
    │           ├── routines/
    │           │   ├── RoutineController.kt       # All /api/v1/routines endpoints
    │           │   ├── RoutineService.kt          # Ownership checks, search/pagination, clone logic
    │           │   └── RoutineRepository.kt       # Routines table CRUD; Jackson JSON for items/tags
    │           ├── workouts/
    │           │   ├── WorkoutController.kt       # All /api/v1/workouts + exercise history/PBs
    │           │   ├── WorkoutService.kt          # Session assembly, routine pre-population, PB detection
    │           │   ├── WorkoutSessionRepository.kt # WorkoutSessions CRUD
    │           │   └── WorkoutSetRepository.kt    # WorkoutSets CRUD; history/PBs queries
    │           ├── media/
    │           │   ├── MediaController.kt         # POST /api/v1/media/upload
    │           │   ├── MediaService.kt            # Validation (type, size), delegates to repo
    │           │   └── CatalystFileRepository.kt  # ZCFile → ZCFolder → uploadFile; constructs URL
    │           └── admin/
    │               └── AdminController.kt         # POST /api/v1/admin/seed (one-time data seeder)
    └── test/
        └── kotlin/dev/eknath/GymJournal/
            └── GymJournalApplicationTests.kt
```

> `skill/` at the project root contains developer reference documents (ZCQL, etc.) — not deployed.

---

## Architecture

### Module Pattern

Each feature is a self-contained **module** under `modules/`, following a layered pattern:

```
Controller  →  Service  →  Module Repository  →  CatalystDataStoreRepository (generic)
```

- **Controller**: Handles HTTP, validation, extracts `currentUserId()`, returns `ApiResponse<T>`.
- **Service**: Business logic — computes summaries, enforces ownership, defaults dates/times.
- **Repository**: Module-specific DataStore queries using ZCQL (sanitized via `ZcqlSanitizer`).
- **`CatalystDataStoreRepository`**: Generic base — `query`, `queryOne`, `insert`, `update`, `delete`, `count`, `getRow`.

### Authentication

Auth is handled by **`CatalystAuthFilter`** which runs on every protected request:

1. **User identity** — reads `x-zc-user-id` header injected by ZGS (trusted, no SDK call needed). Stored as the Spring Security principal; accessible via `currentUserId()`.
2. **SDK init** — calls `CatalystSDK.init(AuthHeaderProvider)` which reads ZGS-injected project/credential headers to give the DataStore SDK a request context. Then calls `ZCProject.initProject(config)` with the default project config so `ZCTable.getInstance()` has a non-null project.

If `x-zc-user-id` is absent → unauthenticated (chain continues, Spring Security rejects).
If SDK init throws → logged as warning; DataStore will surface its own error downstream.

`/api/v1/health` is public — no auth required. All other endpoints require `x-zc-user-id` to be present.

#### Web app auth flow

The web client (on Catalyst Serverless) authenticates via the `zcauthtoken` cookie set by Catalyst's web auth. ZGS validates the cookie and injects `x-zc-user-id` into the forwarded AppSail request. `ZD_CSRF_TOKEN` / `X-ZCSRF-TOKEN` are handled by the Catalyst web SDK.

### Security Filter Chains

```
Order 1 — publicApiChain    → matches /api/v1/health, permits all
Order 2 — protectedApiChain → matches /api/**, stateless, CatalystAuthFilter, requires auth
```

### CORS

CORS is handled entirely by **ZGS** (the gateway in front of AppSail) — not by Spring. Spring CORS is explicitly disabled to prevent duplicate `Access-Control-Allow-Origin` headers (ZGS adds its own; if Spring also adds one the browser rejects the response). To allow a new web client origin, configure it in the Catalyst Console under the AppSail service CORS settings.

### Database (Catalyst DataStore)

- Zoho Catalyst DataStore is a NoSQL-like store queried with **ZCQL** (a SQL subset). See `skill/zcql.md` for the full reference.
- **ZCQL V2** is live on production (since April 2025). INNER JOIN and LEFT JOIN are supported (up to 4 per query). Set env var `ZOHO_CATALYST_ZCQL_PARSER=V2` in AppSail function config.
- **`COUNT(*)`** is not supported in ZCQL V2. Use `COUNT(ROWID)` instead — `ROWID` is always present on every table. `CatalystDataStoreRepository.count()` already does this correctly.
- **`getRow(tableName, rowId)`** — **always use this for single-row lookups** (`findById`). It uses `ZCObject.getTableInstance(name).getRow(rowId)` (the same ZCObject path as writes). `queryOne("WHERE ROWID = x")` silently returns null in AppSail even when the row exists (ZCQL and ZCObject use different SDK context paths). All repositories use `db.getRow(TABLE, id)` for `findById`.
- There are **no bind parameters** in ZCQL — all user input is sanitized manually via `ZcqlSanitizer` (escapes single quotes, strips semicolons).
- **Tables are created via the Catalyst Console UI only** — ZCQL has no DDL. Always provide exact column specs (name, type, mandatory, unique) when a new table is needed.
- **System columns** on every table (never create manually): `ROWID` (BigInt, auto PK), `CREATORID` (Var Char, auto user), `CREATEDTIME` (DateTime, auto), `MODIFIEDTIME` (DateTime, auto-updated on write).
- Max **300 rows** per query — always paginate with `LIMIT offset,count`.
- `ZCProject` is initialized per-request (not at startup) to support local development with `catalyst serve`.

---

## API Endpoints

### Health

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/health` | None | Health check; returns `{ status: "UP" }` |

### Water Intake (Hydration)

All endpoints require auth — `zcauthtoken` cookie (validated by ZGS, which injects `x-zc-user-id`).

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/water` | Log a water intake entry |
| GET | `/api/v1/water/today` | Today's summary (total ml, goal, entries) |
| GET | `/api/v1/water/daily?date=YYYY-MM-DD` | Specific day summary |
| GET | `/api/v1/water/history?startDate=...&endDate=...` | Date range daily totals |
| PUT | `/api/v1/water/{id}` | Update an existing entry |
| DELETE | `/api/v1/water/{id}` | Delete an entry |

#### POST /api/v1/water — Request Body
```json
{
  "amountMl": 250,
  "logDateTime": "2025-01-15T08:30:00",   // optional, defaults to now
  "notes": "Morning glass"                 // optional
}
```

#### Daily Summary Response
```json
{
  "success": true,
  "data": {
    "date": "2025-01-15",
    "totalMl": 1500,
    "goalMl": 2500,
    "progressPercent": 60,
    "entries": [...]
  }
}
```

### Exercise Library

All endpoints require auth. All exercises are public (no private/draft state). Only the creator can edit or delete their own exercise.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/exercises/categories` | List all muscle groups (lookup table) |
| POST | `/api/v1/exercises/categories` | Add a new muscle group |
| GET | `/api/v1/exercises/equipment` | List all equipment types (lookup table) |
| POST | `/api/v1/exercises/equipment` | Add a new equipment type |
| GET | `/api/v1/exercises` | Browse exercises — filter + search + paginate |
| GET | `/api/v1/exercises/{id}` | Get full exercise detail |
| POST | `/api/v1/exercises` | Create a new exercise (any authenticated user) |
| PUT | `/api/v1/exercises/{id}` | Update exercise (creator only — 403 otherwise) |
| DELETE | `/api/v1/exercises/{id}` | Delete exercise (creator only — 403 otherwise) |

**GET /api/v1/exercises query params:**
- `categoryId` — filter by MuscleGroups ROWID (obtain IDs from `GET /exercises/categories`)
- `equipmentId` — filter by Equipment ROWID (obtain IDs from `GET /exercises/equipment`)
- `difficulty` — `BEGINNER` | `INTERMEDIATE` | `ADVANCED`
- `search` — substring match on name (in-memory, ZCQL has no reliable full-text search)
- `mine=true` — only show the calling user's exercises (filtered via `CREATORID`)
- `page` / `pageSize` — pagination (default 1 / 20, max pageSize 50)

### Body Metrics

All endpoints require auth. Each entry is owned by the creator. See [`features/BodyMetrics.md`](./features/BodyMetrics.md) for full spec.

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/body-metrics/entries` | Batch log metric entries (rejects computed types bmi/smiComputed with 400) |
| GET | `/api/v1/body-metrics/entries?date=YYYY-MM-DD` | All entries for a date (defaults to today) |
| PUT | `/api/v1/body-metrics/entries/{id}` | Update an entry (creator only — 403 otherwise) |
| DELETE | `/api/v1/body-metrics/entries/{id}` | Delete an entry (creator only — 403 otherwise) |
| GET | `/api/v1/body-metrics/{metricType}/history` | History for one metric type (default: last 90 days) |
| GET | `/api/v1/body-metrics/snapshot` | Latest value per type + server-side computed bmi/smiComputed |
| GET | `/api/v1/body-metrics/insights?gender=MALE\|FEMALE` | Health insights with severity status + reference ranges (WHO/ACC/ADA) |
| GET | `/api/v1/body-metrics/custom` | List user's custom metric definitions |
| POST | `/api/v1/body-metrics/custom` | Create a custom metric definition |
| DELETE | `/api/v1/body-metrics/custom/{key}` | Delete custom def + cascade-delete all its entries |

**Computed metrics** (`bmi`, `smiComputed`) are never stored. They are derived server-side in the snapshot:
- `bmi = weight(kg) / (height(cm)/100)²`
- `smiComputed = smm(kg) / (height(cm)/100)²`

**Insights** are served by `InsightsController` (module: `insights/`) via the composite engine pattern. The `?gender=MALE|FEMALE` param enables gender-aware thresholds for body fat % and SMI cutoffs.

### Routine Library

All endpoints require auth. Public routines are readable by any user; private routines are only accessible to the creator.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/routines` | Browse routines (`?mine=true`, `?search=`, `?page=`, `?pageSize=`) |
| GET | `/api/v1/routines/{id}` | Get full routine (403 if private and not creator) |
| POST | `/api/v1/routines` | Create a routine template |
| PUT | `/api/v1/routines/{id}` | Update routine (creator only) |
| DELETE | `/api/v1/routines/{id}` | Delete routine (creator only) |
| POST | `/api/v1/routines/{id}/clone` | Clone any public routine to own library (always private copy) |

### Workout Sessions

All endpoints require auth. Sessions are private — only the creator can view/edit.

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/workouts` | Start session from routine (`routineId` required, `name?`, `startedAt?`) |
| GET | `/api/v1/workouts` | List own sessions (`?status=IN_PROGRESS|COMPLETED`, `?page=`, `?pageSize=`) |
| GET | `/api/v1/workouts/{id}` | Full session with sets grouped by `orderInSession` |
| PATCH | `/api/v1/workouts/{id}` | Update session name/notes |
| POST | `/api/v1/workouts/{id}/complete` | Mark COMPLETED, auto-detect personal bests |
| DELETE | `/api/v1/workouts/{id}` | Delete session + all sets |
| POST | `/api/v1/workouts/{sessionId}/sets` | Add a set (`itemType: EXERCISE|REST|CARDIO`) |
| PUT | `/api/v1/workouts/{sessionId}/sets/{setId}` | Update a set's actual data |
| DELETE | `/api/v1/workouts/{sessionId}/sets/{setId}` | Remove a set |
| GET | `/api/v1/exercises/{id}/history` | Completed EXERCISE sets for this exercise (own, paginated) |
| GET | `/api/v1/exercises/{id}/pbs` | Personal bests by rep count for this exercise |

### Media Upload

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/media/upload` | Yes | Upload image/video to Catalyst FileStore; returns permanent URL |

**Form fields:** `file` (MultipartFile, required), `folder` (String?, optional: `exercises` \| `routines`; default `misc`)
**Allowed types:** images (JPEG/PNG/WebP, max 5 MB), videos (MP4/MOV, max 50 MB)
**FileStore folders** must be pre-created in Catalyst Console: `exercises`, `routines`, `misc`.
**URL construction:** `https://{projectDomain}/baas/v1/project/{projectId}/filestore/{folderId}/folder/{fileId}/download`

> `ZCFileService` is package-private in the SDK. Use `ZCFile.getInstance().getFolder(name).uploadFile(File)` instead.

### Admin / Seed

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/admin/seed` | One-time seed for MuscleGroups and Equipment lookup tables |

Idempotent — skips tables that already have rows. Call once after deploying to a fresh environment.

---

## DataStore Tables

> All tables are managed in the **Catalyst Console** — ZCQL has no DDL. System columns (`ROWID` BigInt auto-PK, `CREATORID`, `CREATEDTIME`, `MODIFIEDTIME`) are added automatically — never create these manually.
> All user-owned tables have an explicit `USER_ID` BigInt column for ownership. Use `WHERE USER_ID = $userId` in ZCQL — BigInt comparisons are reliable. Do **not** rely on `CREATORID`.

### WaterIntakeLogs
**Catalyst Console Table ID:** `11585000000689556`

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `USER_ID` | BigInt | ✓ | Catalyst user ID; used for ZCQL ownership queries |
| `logDateTime` | DateTime | ✓ | Search-indexed; stored as `yyyy-MM-dd HH:mm:ss`; returned as ISO-8601 |
| `amountMl` | Int | ✓ | Volume in millilitres (min 1) |
| `notes` | Text | — | Optional free text |

### MuscleGroups
**Catalyst Console Table ID:** `11585000000698134`

Reference data — no `userId` (global lookup table; seeded via admin endpoint, not user-owned).

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `displayName` | Text | ✓ | e.g. `Latissimus Dorsi` |
| `shortName` | Text | — | e.g. `Lats` |
| `description` | Text | — | Human-readable description |
| `bodyRegion` | Text | — | `UPPER_BODY` \| `LOWER_BODY` \| `CORE` \| `FULL_BODY` \| `OTHER` |
| `imageUrl` | Text | — | Optional illustration URL |

Default data inserted via `POST /api/v1/admin/seed`.

### Equipment
**Catalyst Console Table ID:** `11585000000690690`

Reference data — no `userId` (global lookup table; seeded via admin endpoint, not user-owned).

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `displayName` | Text | — | e.g. `Barbell` |
| `description` | Text | — | Human-readable description |
| `category` | Text | — | `FREE_WEIGHTS` \| `MACHINES` \| `BODYWEIGHT` \| `CARDIO_MACHINES` \| `OTHER` |
| `imageUrl` | Text | — | Optional illustration URL |

Default data inserted via `POST /api/v1/admin/seed`.

### Exercises
**Catalyst Console Table ID:** `11585000000697412`

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `name` | Text | — | Exercise name |
| `description` | Text | — | Overview |
| `difficulty` | Text | — | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` |
| `instructions` | Text | — | JSON array of ordered steps |
| `tips` | Text | — | JSON array of coaching cues |
| `imageUrl` | Text | — | Optional illustration URL |
| `videoUrl` | Text | — | Optional video URL |
| `tags` | Text | — | JSON array e.g. `["compound","pull"]` |
| `primaryMuscleId` | BigInt (FK) | ✓ | FK → `MuscleGroups.ROWID`; search-indexed |
| `equipmentId` | BigInt (FK) | — | FK → `Equipment.ROWID` |
| `secondaryMuscles` | Text | — | JSON array e.g. `["Rhomboids","Biceps"]` |
| `USER_ID` | BigInt | — | Creator's Catalyst user ID; used for ownership checks (edit/delete) |

List fields (`secondaryMuscles`, `instructions`, `tips`, `tags`) are stored as JSON strings and serialised/deserialised by `ExerciseRepository` via Jackson `ObjectMapper`.

### BodyMetricEntries
**Catalyst Console Table ID:** `11585000000699049`

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `metricType` | Text | ✓ | e.g. `weight`, `bodyFat`, `custom_sgpt` |
| `value` | Double | ✓ | The measured numeric value |
| `unit` | Text | ✓ | e.g. `kg`, `%`, `mg/dL` |
| `logDate` | Date | ✓ | Search-indexed; `YYYY-MM-DD` date type — lexicographic `>=`/`<=` works for range queries |
| `notes` | Text | — | Optional free-text note |
| `USER_ID` | BigInt | — | Creator's Catalyst user ID. **Note:** previously `userId` Var Char — migrated to `USER_ID` BigInt; old column deleted from Console (was causing mandatory-column insert errors). |

Computed metrics (`bmi`, `smiComputed`) are never stored — derived server-side in the snapshot endpoint.

### CustomMetricDefs
**Catalyst Console Table ID:** `11585000000699771`

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `metricKey` | Text | ✓ | e.g. `custom_sgpt` — derived from label; unique per user (enforced in service) |
| `label` | Text | ✓ | User-supplied display name, e.g. `SGPT` |
| `unit` | Text | — | e.g. `U/L` — may be empty string |
| `USER_ID` | BigInt | — | Creator's Catalyst user ID |

### Routines
**Catalyst Console Table ID:** `11585000000700564`

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `name` | Var Char | ✓ | Routine name |
| `description` | Text | — | Overview |
| `items` | Text | ✓ | JSON array of `RoutineItem` objects |
| `estimatedMinutes` | Int | — | Advisory duration hint; 0 = not set |
| `isPublic` | Int | — | Search-indexed; default `0`; `1` = any user can see/clone; `0` = private |
| `tags` | Text | — | JSON array e.g. `["push","chest"]` |
| `USER_ID` | BigInt | — | Creator's Catalyst user ID |

`items` JSON structure per item: `{ order, type, exerciseId?, exerciseName?, sets?, repsPerSet?, weightKg?, restAfterSeconds?, durationSeconds?, cardioName?, durationMinutes?, targetSpeedKmh? }`

### WorkoutSessions
**Catalyst Console Table ID:** `11585000000701286`

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `USER_ID` | BigInt | ✓ | Catalyst user ID |
| `routineId` | BigInt (FK) | ✓ | FK → `Routines.ROWID`. Every session must reference a routine — no standalone sessions. |
| `routineName` | Var Char | — | Denormalised routine name snapshot |
| `name` | Var Char | ✓ | e.g. "Push Day - Mon 3 Mar" |
| `status` | Var Char | — | `IN_PROGRESS` \| `COMPLETED` |
| `startedAt` | DateTime | — | Written as `yyyy-MM-dd HH:mm:ss`; sessions list ordered by this DESC |
| `completedAt` | DateTime | — | Omitted (null) on insert; written only when `/complete` is called |
| `notes` | Text | — | Post-workout notes |

> `completedAt` is a **DateTime** column. It is never written as an empty string — omitted from the insert map entirely and remains null until the session is completed. This avoids Catalyst rejecting an empty string for a DateTime column.

### WorkoutSets
**Catalyst Console Table ID:** `11585000000702008`

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `sessionId` | BigInt (FK) | — | FK → `WorkoutSessions.ROWID` |
| `USER_ID` | BigInt | — | Denormalised owner ID for direct ZCQL queries (avoids joining sessions) |
| `exerciseId` | BigInt (FK) | — | FK → `Exercises.ROWID`; `0` for REST/CARDIO |
| `exerciseName` | Var Char | — | Denormalised; empty for REST; activity name for CARDIO |
| `itemType` | Var Char | — | `EXERCISE` \| `REST` \| `CARDIO` |
| `orderInSession` | Int | ✓ | Groups sets for the same exercise slot |
| `setNumber` | Int | ✓ | 1, 2, 3… within an exercise slot; always 1 for REST/CARDIO |
| `plannedReps` | Int | — | From routine template; 0 if not planned |
| `plannedWeightKg` | Var Char | — | e.g. `"80.0"`; `"0"` if N/A |
| `actualReps` | Int | — | 0 = not done yet |
| `actualWeightKg` | Var Char | — | `"0"` = not done yet |
| `durationSeconds` | Int | — | REST duration or CARDIO timed duration; 0 if N/A |
| `distanceKm` | Var Char | — | CARDIO only; `"0"` otherwise |
| `rpe` | Int | — | 1–10 Rate of Perceived Exertion; 0 = not set |
| `isPersonalBest` | Int | — | `0`/`1`; set by `/complete` PB detection |
| `notes` | Text | — | Per-set notes |
| `completedAt` | Var Char | — | Empty string = not done; `yyyy-MM-dd HH:mm:ss` when done |

### ExerciseLibrary (Legacy — Do Not Use)
**Catalyst Console Table ID:** `11585000000686059`

This table predates the current exercise architecture and uses all-uppercase column names (`NAME`, `CATEGORY`, `PRIMARY_MUSCLE_...`, `SECONDARY_MUSC...`, `EQUIPMENT`, `TRACKING_TYPE`, `INSTRUCTIONS`, `IS_CUSTOM`, `USER_ID`). It is **not read or written** by the current server code. Do not query, modify, or build on this table.

---

## Key Utilities

### `ApiResponse<T>`
Unified response envelope for all endpoints:
```kotlin
ApiResponse.ok(data)                      // { success: true, data: ... }
ApiResponse.error("CODE", "message")      // { success: false, error: { code, message } }
```

### `ZcqlSanitizer`
Prevents ZCQL injection since prepared statements are not supported:
```kotlin
ZcqlSanitizer.sanitize(userInput)   // escapes ' → '' and strips ;
```

### `currentUserId()`
Extension function — retrieves the authenticated user's ID from the Spring Security context:
```kotlin
val userId = currentUserId()
```

### `CatalystPortCustomizer`
Reads `X_ZOHO_CATALYST_LISTEN_PORT` env var (set by Catalyst AppSail at runtime); falls back to `8080` locally.

---

## Development Commands

```bash
# Build the project
./gradlew build

# Run locally (standalone Spring Boot)
./gradlew bootRun

# Run with Catalyst CLI (recommended for local dev — sets up Catalyst env)
catalyst serve

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```
---

## Conventions & Code Style

- **Kotlin idioms**: data classes, extension functions, companion objects, `buildMap {}`.
- **Naming**: `PascalCase` for classes, `camelCase` for functions/properties, `SCREAMING_SNAKE_CASE` for constants.
- **DTOs**: Separate request and response types; validation annotations on request fields (`@Min`, `@NotBlank`).
- **Domain models**: Plain data classes in `model/domain/`; no persistence annotations.
- **No framework ORM**: All DB access is via the Catalyst SDK and raw ZCQL strings.
- **Ownership enforcement**: Services check `entry.userId != userId` before update/delete.
- **Daily goal**: Hardcoded at `2500 ml` (`DEFAULT_DAILY_GOAL_ML` constant in `WaterIntakeService`).
- **Error handling**: Centralised in `GlobalExceptionHandler` (`util/GlobalExceptionHandler.kt`). Maps `HttpMessageNotReadableException` → 400, `MethodArgumentNotValidException` → 400, `IllegalArgumentException` → 400, `NoSuchElementException` → 404, `IllegalAccessException` → 403, `IllegalStateException` → 409, `MaxUploadSizeExceededException` → 413. All error responses use the `ApiResponse` envelope.

---

## Deployment (Zoho Catalyst AppSail)

1. Build: `./gradlew build` → produces `build/libs/GymJournal-0.0.1-SNAPSHOT.jar`
2. Deploy via Catalyst CLI or Catalyst console.
3. AppSail injects `X_ZOHO_CATALYST_LISTEN_PORT` — the app reads this via `CatalystPortCustomizer`.
4. `ZCProject` is initialised per-request in `CatalystAuthFilter`. The no-arg `initProject()` does **not** work in AppSail — AppSail does not provide a `catalyst-config.json` at runtime.

---

## Extending the Project

To add a new feature module:

1. Create `src/main/kotlin/.../modules/<feature>/` directory.
2. Add a domain model in `model/domain/`.
3. Add DTOs in `model/dto/`.
4. Create `<Feature>Repository` using `CatalystDataStoreRepository`.
5. Create `<Feature>Service` with business logic.
6. Create `<Feature>Controller` with `@RestController` and `@RequestMapping("/api/v1/<feature>")`.
7. Create the DataStore table in the Catalyst Console — provide exact column specs (name, type, mandatory, unique). See `skill/zcql.md` for the table creation recipe format and all available column types.
