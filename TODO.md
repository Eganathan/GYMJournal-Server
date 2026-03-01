# GymJournal Server — TODO / Backlog

> Items are grouped by category and annotated with priority (`🔴 High` / `🟡 Medium` / `🟢 Low`) and effort (`S` = small, `M` = medium, `L` = large).

---

## 🐛 Bug Fixes & Correctness Issues

### 1. `findAllByType` has no LIMIT — cascade delete silently misses rows 🔴 S
**File:** `modules/metrics/BodyMetricRepository.kt` — `findAllByType()`
**Problem:** The query has no `LIMIT` clause. Catalyst DataStore caps unbound queries at an internal limit. If a user has more entries than that limit for a custom metric, `deleteCustomDef()` will silently skip the overflow rows, leaving orphaned entries in the DB.
**Fix:** Add `LIMIT 0,300` and if > 300 entries exist, loop with offset pagination until none remain.

---

### 2. `findExerciseHistory` ZCQL budget consumed by incomplete sets 🔴 S
**File:** `modules/workouts/WorkoutSetRepository.kt` — `findExerciseHistory()`
**Problem:** The query fetches 300 rows ordered by `completedAt DESC` then filters `completedAt.isNotBlank()` in-memory. Because incomplete/pre-populated sets have a blank `completedAt`, they sort last in DESC order but still consume slots from the 300-row ZCQL budget. A user with many incomplete sets (large routines, partially done sessions) will see fewer completed history rows than expected.
**Fix:** Add `AND completedAt != ''` as a 4th ZCQL WHERE condition (currently 3 conditions; max is 5). Confirm ZCQL supports `!= ''` comparison on Var Char first; if not, add `AND completedAt > '0'` as an alternative.

---

### 3. `deleteAllBySession` makes N individual DELETE calls 🟡 S
**File:** `modules/workouts/WorkoutSetRepository.kt` — `deleteAllBySession()`
**Problem:** Deletes each set one by one (N API round-trips). A session with 30+ sets (4 exercises × 4 sets + REST/CARDIO blocks) makes 30+ sequential DataStore calls when a session is deleted.
**Fix:** There is no batch delete in Catalyst DataStore, but this can be mitigated by parallelising the deletes (Kotlin coroutines / `async {}`) or by simply documenting the latency trade-off. Acceptable as-is for now unless latency becomes a user-visible problem.

---

### 4. Completing a session with no logged sets is silently allowed 🟡 S
**File:** `modules/workouts/WorkoutService.kt` — `completeSession()`
**Problem:** A session where every set has `actualReps = 0` and `completedAt = null` can be marked COMPLETED. This produces empty PB detection and a useless entry in the user's history.
**Fix:** Return a `400 Bad Request` (or `409 Conflict`) if no sets have `actualReps > 0` and `completedAt` is set, or add a `force=true` query param to allow intentional empty completions (e.g. stretching sessions).

---

### 5. Sets can be added/updated on a COMPLETED session 🟡 S
**File:** `modules/workouts/WorkoutService.kt` — `addSet()`, `updateSet()`, `deleteSet()`
**Problem:** There is no guard that prevents mutating sets when `session.status == "COMPLETED"`. PB flags set during `/complete` could become stale if sets are subsequently edited.
**Fix:** Check `session.status == "COMPLETED"` at the start of each set mutation and throw `IllegalStateException("Cannot modify sets on a completed session")` — GlobalExceptionHandler can map this to `409 Conflict`.

---

### 6. PB detection flags ties as personal bests 🟢 S
**File:** `modules/workouts/WorkoutService.kt` — `detectPb()`
**Problem:** The condition `newWeight >= bestHistoricalWeight` flags a set as a PB even when the weight exactly matches an existing PB (tying, not beating). Most gym apps only flag strict improvements.
**Fix:** Change `>=` to `>`. A **design decision** — document whichever behaviour is chosen in the code comment.

---

### 7. `RoutineService` duplicates the datetime formatter inline 🟢 S
**File:** `modules/routines/RoutineService.kt` — `createRoutine()`, `cloneRoutine()`
**Problem:** Both functions create `LocalDateTime.now().format(DateTimeFormatter.ofPattern(...))` inline instead of using a shared top-level constant like the `DB_FMT` in `WorkoutService`.
**Fix:** Extract a `private val DB_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")` top-level constant in `RoutineService.kt`.

---

## ✅ Validation Gaps

### 8. `AddSetRequest.itemType` accepts arbitrary strings 🔴 S
**File:** `model/dto/WorkoutDtos.kt` — `AddSetRequest`
**Problem:** `itemType` is a free `String` with default `"EXERCISE"`. The service does `.uppercase()` but does not validate against `EXERCISE | REST | CARDIO`. An invalid value is stored in the DB and breaks grouping/display logic.
**Fix:** Add a validation annotation or an explicit check in `WorkoutService.addSet()`:
```kotlin
require(request.itemType.uppercase() in setOf("EXERCISE", "REST", "CARDIO")) {
    "itemType must be EXERCISE, REST, or CARDIO"
}
```

---

### 9. `startedAt` / `completedAt` datetime strings are not format-validated 🟡 S
**Files:** `model/dto/WorkoutDtos.kt` — `StartWorkoutRequest`, `AddSetRequest`, `UpdateSetRequest`
**Problem:** No format validation on the `startedAt` / `completedAt` / `completedAt` string fields. An invalid string passed in is stored as-is; Catalyst will reject the write with a cryptic SDK error.
**Fix:** Add a regex check in the service (same pattern as `isValidDate()` in `BodyMetricService`) or add a JSR-303 custom constraint:
```kotlin
Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$""")
```
Reject with `400 Bad Request` and a clear message.

---

### 10. Weight fields (`plannedWeightKg`, `actualWeightKg`) accept non-numeric strings 🟢 S
**Files:** `model/dto/WorkoutDtos.kt` — `AddSetRequest`, `UpdateSetRequest`
**Problem:** Stored as `Var Char` and passed through without validation. Storing `"abc"` would break PB detection (`toDoubleOrNull()` returns null and the set is silently excluded from comparisons).
**Fix:** Add a regex constraint or service-level check that the value is a valid decimal number (or `"0"`).

---

### 11. `logDate` allows future dates in body metrics 🟢 S
**File:** `modules/metrics/BodyMetricService.kt` — `batchLog()`
**Problem:** A user can log metrics for dates far in the future, which distorts the snapshot (latest by `logDate` would always be the future entry).
**Fix:** After format validation, check `LocalDate.parse(req.logDate).isAfter(LocalDate.now())` and reject with `400`.

---

## 🚀 Missing Features / Enhancements

### 12. No date-range filter on `GET /api/v1/workouts` 🔴 M
**Problem:** The session list only supports filtering by `status`. Users can't query "show me sessions from last month" without fetching everything and filtering client-side.
**Enhancement:** Add `?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD` query params. In the repository, add `AND startedAt >= '...' AND startedAt <= '...'` ZCQL conditions (mind the 5-condition limit: `userId + status + startDate + endDate` = 4 conditions, still within limit).

---

### 13. No workout duration / volume summary in session response 🟡 S
**Problem:** The session response contains raw sets but no computed summary: total volume lifted (sum of `actualReps × actualWeightKg`), total duration, number of exercises completed vs planned, etc.
**Enhancement:** Add a `summary` object to `WorkoutSessionResponse`:
```json
"summary": {
  "totalVolumeKg": 3240.0,
  "totalSetsCompleted": 12,
  "totalSetsPlanned": 15,
  "durationMinutes": 58
}
```
Computed server-side from the sets — no new DB queries needed.

---

### 14. No "abandon session" state 🟡 S
**Problem:** A user can only complete or delete a session — there is no way to mark it as `ABANDONED` (started but gave up). Deleted sessions are gone from history; completed sessions with zero logged sets pollute the history.
**Enhancement:** Add `ABANDONED` as a valid status. `POST /api/v1/workouts/{id}/abandon` → sets status to `ABANDONED`, does NOT run PB detection. Sessions with `ABANDONED` status are excluded from PB history but visible in the list with `?status=ABANDONED`.

---

### 15. No weekly / monthly training frequency stats 🟡 M
**Problem:** No analytics endpoint. Common gym-app questions ("how many sessions this week?", "what's my average weekly volume?") require the client to fetch all sessions and compute locally.
**Enhancement:** Add `GET /api/v1/workouts/stats?period=weekly|monthly&weeks=8` returning:
```json
{
  "periods": [
    { "week": "2026-W09", "sessions": 4, "totalVolumeKg": 12400 },
    ...
  ]
}
```
Computed from the existing session + set data in-memory (no new tables needed).

---

### 16. No muscle group volume breakdown per session 🟡 M
**Problem:** A user can't see "this session you trained chest 60%, back 25%, shoulders 15%". This requires joining `WorkoutSets.exerciseId` → `Exercises.primaryMuscleId` → `MuscleGroups.displayName`.
**Enhancement:** Add a `muscleBreakdown` field to `WorkoutSessionResponse` (computed at response-build time by batch-fetching exercise data, similar to the exercise name cache already used in `startSession()`).

---

### 17. Clone routine — allow specifying a custom name 🟢 S
**File:** `modules/routines/RoutineController.kt`, `RoutineService.kt`
**Problem:** `POST /api/v1/routines/{id}/clone` always keeps the original name. A user cloning "Push Day A (John's)" would want to rename it immediately.
**Enhancement:** Accept an optional request body `{ "name": "My Push Day" }`. If provided, use it; otherwise fall back to the source name.

---

### 18. No "last used" timestamp on Routines 🟢 S
**Problem:** When browsing own routines (`?mine=true`), there's no way to sort by "most recently used". The routine record has no link to the last session started from it.
**Enhancement:** Add a `lastUsedAt` Var Char column to the `Routines` table. Update it in `WorkoutService.startSession()` when pre-populating from a routine. Allows the client to sort `?mine=true` by recency.

---

### 19. No "skip set" distinction from "not yet done" 🟢 S
**Problem:** Both a set the user hasn't reached yet and a set they intentionally skipped show `actualReps = null`. After the session there is no record of deliberate skips.
**Enhancement:** Add a `skipped` boolean field to `WorkoutSet` / `WorkoutSetResponse`. `PUT .../sets/{setId}` with `{ "skipped": true }` marks it. Skipped sets are excluded from PB detection.

---

### 20. No exercise search by muscle group within workout history 🟢 M
**Problem:** `GET /api/v1/exercises/{exerciseId}/history` requires knowing the exact exercise ID. Users can't query "all my chest exercises this month".
**Enhancement:** Add `GET /api/v1/workouts/history?muscleGroupId=X&startDate=...&endDate=...` returning sets grouped by exercise. Requires a ZCQL JOIN (ZCQL V2 is live) or in-memory cross-reference.

---

## ⚙️ Performance & DataStore Concerns

### 21. `listRoutines` and `listSessions` hit the 300-row ZCQL ceiling 🟡 M
**Files:** `RoutineRepository.findAll()`, `WorkoutSessionRepository.findByUser()`
**Problem:** Both fetch all rows (max 300) then do in-memory search + pagination. A heavy user approaching 300 routines or 300 sessions will silently lose older records.
**Fix:** Pass `page` and `pageSize` down to the ZCQL query using `LIMIT offset,count`. For routines, the in-memory `search` substring filter would need to be replaced with ZCQL `LIKE '%term%'` (if supported) or paginated per server-request.

---

### 22. `getSnapshot()` fetches 300 entries to find one per type 🟢 M
**File:** `modules/metrics/BodyMetricService.kt` — `getSnapshot()`
**Problem:** Fetches 300 most-recent entries across all metric types just to take the `first()` of each group. This is wasteful if a user has many entries.
**Better approach:** Query the latest row per type with one query per tracked metric type (weight, height, bodyFat, etc.) — typically 6–10 queries instead of 300 rows. Alternatively, maintain a separate `LatestMetrics` table updated on each write.

---

## 🔧 Code Quality & Consistency

### 23. `WorkoutController` uses flat path strings instead of class-level `@RequestMapping` 🟢 S
**File:** `modules/workouts/WorkoutController.kt`
**Problem:** Every `@GetMapping` / `@PostMapping` has the full path hardcoded (e.g. `"/api/v1/workouts/{id}"`). Every other controller uses a class-level `@RequestMapping`. The exception was intentional (endpoints span two path prefixes: `/workouts` and `/exercises`), but should be documented in a comment.
**Fix:** Add a comment explaining the deliberate choice, or split into two controllers: `WorkoutSessionController` and `ExerciseInsightsController` each with their own `@RequestMapping`.

---

### 24. No unit tests 🟡 L
**Problem:** Zero test coverage. `GymJournalApplicationTests.kt` contains only the Spring context load test.
**Recommended test targets (in priority order):**
- `WorkoutService.detectPb()` — pure function, easy to unit-test edge cases (ties, first set ever, same reps/weight, higher reps/lower weight).
- `BodyMetricService.getSnapshot()` — BMI and SMI computation with edge cases (missing height, zero height).
- `ZcqlSanitizer` — injection string escaping.
- `WorkoutService.startSession()` — routine pre-population logic and exercise name resolution.

---

### 25. `RoutineRepository` has no `updatedAt` management 🟢 S
**File:** `modules/routines/RoutineRepository.kt` — `toMap()`
**Problem:** `updatedAt` is the `MODIFIEDTIME` system column and auto-updates on every Catalyst write — so the DB value is always correct. However, the domain `Routine.updatedAt` field is only set on create/clone and never re-fetched after an update. `RoutineService.updateRoutine()` returns `routineRepo.findById(id) ?: updated` which does a read-back, so the response is always fresh. ✅ This is fine — but worth calling out to avoid confusion.

---

## 📝 API Design Improvements

### 26. `PUT /workouts/{sessionId}/sets/{setId}` should be `PATCH` 🟢 S
**Problem:** The endpoint is semantically a partial update (all fields optional), but it's mapped as `PUT`. `PUT` implies full replacement; `PATCH` implies partial update.
**Fix:** Rename to `PATCH` in both the controller and API docs. No code logic change needed.

---

### 27. No `Content-Type: application/json` enforcement on non-body endpoints 🟢 S
**Problem:** Spring Boot handles this automatically via `@RestController`, but there is no explicit `produces`/`consumes` annotation on controllers. Works fine today but could be an issue if Catalyst gateway adds content-negotiation headers.
**Fix:** Add `produces = [MediaType.APPLICATION_JSON_VALUE]` to `@RequestMapping` or individual mappings, particularly for endpoints that always return JSON.

---

### 28. Session list response missing `totalDurationMinutes` for COMPLETED sessions 🟢 S
**Problem:** The `WorkoutSessionSummaryResponse` (used in the list) includes `startedAt` and `completedAt` but not the computed duration. Clients have to compute `completedAt - startedAt` themselves.
**Enhancement:** Add `durationMinutes: Int?` (null for IN_PROGRESS) computed in `toSummaryResponse()`:
```kotlin
durationMinutes = if (completedAt.isNotBlank()) computeDuration(startedAt, completedAt) else null
```

---

## 📦 Deployment / Environment

### 29. `ZOHO_CATALYST_ZCQL_PARSER=V2` env var not documented in `CLAUDE.md` / `PROJECT.md` 🟡 S
**Problem:** ZCQL V2 (which enables JOIN support) requires `ZOHO_CATALYST_ZCQL_PARSER=V2` to be set in AppSail function config. This is mentioned in `PROJECT.md` but not in `CLAUDE.md` or the deployment section.
**Fix:** Add a deployment checklist item to `PROJECT.md` under the Deployment section confirming this env var is set in both Development and Production AppSail environments.

---

### 30. `POST /api/v1/admin/seed` has no auth guard for admin role 🟡 S
**Problem:** The seed endpoint is authenticated (requires `x-zc-user-id`) but any authenticated user can call it. It is idempotent but still exposes unnecessary surface area.
**Fix:** Either (a) remove the endpoint after initial seeding and re-add when needed, or (b) check `currentUserId()` against a hardcoded admin user ID env var. Document the chosen approach.

---

*Last updated: 2026-03-01*
