# Workouts API

The Workouts API handles real-time workout session logging. A **WorkoutSession** is one execution of a routine. Every session must be linked to a routine — there is no free-form / standalone session. During the session, the user logs individual sets — one row per exercise set, rest period, or cardio block — as they complete them. When the session is finished, calling `/complete` locks it and auto-detects personal bests.

**Base path:** `/api/v1/workouts` (sessions and sets)
**Secondary path:** `/api/v1/exercises/{id}/history` and `/api/v1/exercises/{id}/pbs` (exercise-scoped queries)
**Auth required:** Yes — all endpoints require authentication.
**Data visibility:** Private — sessions and sets are only visible to their creator.

---

## Key concepts

### Session lifecycle

```
POST /api/v1/workouts                                → status: "IN_PROGRESS"
   ↓  (user logs sets one by one)
PATCH /api/v1/workouts/{id}/sets/{setId}             → update each set as it's completed
   ↓  (workout finished)
POST /api/v1/workouts/{id}/complete                  → status: "COMPLETED", PBs detected
```

Once a session is `COMPLETED`, sets **cannot be added, modified, or deleted**. Attempting to do so returns `409 CONFLICT`. The session itself (name/notes) can still be patched, and it can be deleted at any time.

### Set item types

| `itemType` | Meaning |
|---|---|
| `EXERCISE` | A set of an exercise with reps and weight |
| `REST` | A timed rest period |
| `CARDIO` | A cardio block with time and optional distance |

### Starting a session

Every session must start from a routine — `routineId` is required in `POST /api/v1/workouts`. The session is pre-populated with `WorkoutSet` rows derived from the routine's items:
- One row per planned set in each `EXERCISE` item (`plannedReps` and `plannedWeightKg` come from the routine; `actualReps` starts as `null` = not yet done).
- One row per `REST` item (with `durationSeconds` pre-filled).
- One row per `CARDIO` item.

After the session is started, the user can still add ad-hoc sets via `POST /api/v1/workouts/{id}/sets` (for unplanned exercises or extra sets beyond the routine plan).

### Personal best (PB) detection

PB detection runs automatically when `/complete` is called:
- For each `EXERCISE` set where `actualReps > 0`, the server compares the new weight against all historical sets for the same exercise where `actualReps ≥` the current set's reps.
- If the new weight **strictly exceeds** all historical weights in that rep range → `isPersonalBest = true`.
- **Ties are not flagged as PBs** — only a strict improvement counts.
- If it is the user's first time performing this exercise at this rep range, it is automatically a PB.

---

## Endpoint Summary

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/workouts` | Required | Start a new session |
| `GET` | `/api/v1/workouts` | Required | List own sessions |
| `GET` | `/api/v1/workouts/{id}` | Required (owner) | Full session with grouped sets |
| `PATCH` | `/api/v1/workouts/{id}` | Required (owner) | Update session name / notes |
| `POST` | `/api/v1/workouts/{id}/complete` | Required (owner) | Mark completed, detect PBs |
| `DELETE` | `/api/v1/workouts/{id}` | Required (owner) | Delete session and all sets |
| `POST` | `/api/v1/workouts/{sessionId}/sets` | Required (owner) | Add a set (IN_PROGRESS only) |
| `PATCH` | `/api/v1/workouts/{sessionId}/sets/{setId}` | Required (owner) | Update a set (IN_PROGRESS only) |
| `DELETE` | `/api/v1/workouts/{sessionId}/sets/{setId}` | Required (owner) | Delete a set (IN_PROGRESS only) |
| `GET` | `/api/v1/exercises/{exerciseId}/history` | Required | All completed sets for an exercise |
| `GET` | `/api/v1/exercises/{exerciseId}/pbs` | Required | Personal bests per rep count |

---

## Typical client flow

```
1. Browse routines        → GET /api/v1/routines
2. Start session          → POST /api/v1/workouts { routineId: 1, name: "Push Day - Mon" }
3. Load session           → GET /api/v1/workouts/{id}
                            (sets pre-populated from routine; actualReps = null = not yet done)
4. User completes a set   → PATCH /api/v1/workouts/{sessionId}/sets/{setId}
                            { actualReps: 8, actualWeightKg: "82.5", rpe: 8, completedAt: "..." }
5. User adds extra set    → POST /api/v1/workouts/{sessionId}/sets    (freestyle/unplanned sets)
6. User removes a set     → DELETE /api/v1/workouts/{sessionId}/sets/{setId}
7. Finish workout         → POST /api/v1/workouts/{id}/complete
                            → PBs auto-flagged (isPersonalBest = true on qualifying sets)
8. View history           → GET /api/v1/exercises/{id}/history        (progress chart)
9. View PBs               → GET /api/v1/exercises/{id}/pbs            (records screen)
```

---

## POST /api/v1/workouts

**Start a new workout session.**

If `routineId` is provided, the session is pre-populated with `WorkoutSet` rows from the routine's items:
- `EXERCISE` items → one `WorkoutSet` row per planned set, with `plannedReps` and `plannedWeightKg` filled and `actualReps = null`.
- `REST` items → one `WorkoutSet` row with `durationSeconds` from the routine.
- `CARDIO` items → one `WorkoutSet` row with `durationSeconds` converted from the routine's `durationMinutes`.

**Request Body** — all fields optional

| Field | Type | Description |
|---|---|---|
| `routineId` | Long | ID of the routine to base the session on. Omit for a freestyle session. |
| `name` | String | Session name. Auto-generated as `"<routineName> - <date>"` or `"Free Workout - <date>"` if omitted. |
| `startedAt` | String | ISO-8601 datetime e.g. `"2026-03-02T09:00:00"`. Defaults to current server time if omitted. Supply a past datetime to backdate a workout. |

```json
{
  "routineId": 1,
  "name": "Push Day - Mon 2 Mar",
  "startedAt": "2026-03-02T09:00:00"
}
```

**Freestyle session (no routine):**
```json
{
  "name": "Morning Lift"
}
```

> **Backdating:** Supplying a past `startedAt` places the session in correct chronological order in `GET /api/v1/workouts` (ordered by `startedAt` descending, not insert time).

**Response — 201 Created** — full `WorkoutSessionResponse` (same shape as `GET /api/v1/workouts/{id}`).

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | `startedAt` is not a valid ISO-8601 datetime (`YYYY-MM-DDTHH:MM:SS`) |
| 403 | `FORBIDDEN` | `routineId` references a private routine the caller does not own |
| 404 | `NOT_FOUND` | `routineId` does not exist |

---

## GET /api/v1/workouts

**List the calling user's workout sessions.**

Results are ordered by `startedAt` descending (most recent first based on session start time — not insert time, so backdated sessions appear in true chronological order).

**Query Parameters**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `status` | String | No | — | Filter by status: `IN_PROGRESS` or `COMPLETED`. Omit for all sessions. |
| `startDate` | String | No | — | Only sessions on or after this date (`YYYY-MM-DD`, inclusive). |
| `endDate` | String | No | — | Only sessions on or before this date (`YYYY-MM-DD`, inclusive). |
| `page` | Int | No | `1` | Page number. |
| `pageSize` | Int | No | `20` | Items per page. Max `50`. |

**Examples**
```
GET /api/v1/workouts
GET /api/v1/workouts?status=COMPLETED
GET /api/v1/workouts?startDate=2026-03-01&endDate=2026-03-31
GET /api/v1/workouts?status=COMPLETED&startDate=2026-02-01&page=1&pageSize=50
```

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "id": 5,
      "name": "Push Day A - Sun 1 Mar",
      "routineId": 1,
      "routineName": "Push Day A",
      "status": "COMPLETED",
      "startedAt": "2026-03-01T09:00:00",
      "completedAt": "2026-03-01T10:15:00",
      "durationMinutes": 75,
      "updatedAt": "2026-03-01T10:15:00"
    },
    {
      "id": 4,
      "name": "Free Workout - Fri 28 Feb",
      "routineId": 0,
      "routineName": "",
      "status": "COMPLETED",
      "startedAt": "2026-02-28T07:30:00",
      "completedAt": "2026-02-28T08:20:00",
      "durationMinutes": 50,
      "updatedAt": "2026-02-28T08:20:00"
    }
  ],
  "meta": { "page": 1, "pageSize": 20, "total": 12 }
}
```

| Field | Type | Description |
|---|---|---|
| `id` | Long | Session ID. |
| `name` | String | Session name. |
| `routineId` | Long | Source routine ID. `0` if freestyle. |
| `routineName` | String | Source routine name. `""` if freestyle. |
| `status` | String | `"IN_PROGRESS"` or `"COMPLETED"` |
| `startedAt` | String | ISO-8601 session start time. Never null. |
| `completedAt` | String \| null | ISO-8601 completion time. `null` until `/complete` is called. |
| `durationMinutes` | Int \| null | Whole-minute duration between `startedAt` and `completedAt`. `null` for in-progress sessions. |
| `updatedAt` | String | ISO-8601 last-updated timestamp. |

---

## GET /api/v1/workouts/{id}

**Get the full session with all sets grouped by exercise slot.**

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Session ID. |

**Response — 200 OK**

```json
{
  "success": true,
  "data": {
    "id": 5,
    "name": "Push Day A - Sun 1 Mar",
    "routineId": 1,
    "routineName": "Push Day A",
    "status": "IN_PROGRESS",
    "startedAt": "2026-03-01T09:00:00",
    "completedAt": null,
    "notes": "",
    "createdAt": "2026-03-01T09:00:00",
    "updatedAt": "2026-03-01T09:05:00",
    "exercises": [
      {
        "orderInSession": 1,
        "itemType": "EXERCISE",
        "exerciseId": 12,
        "exerciseName": "Bench Press (Barbell)",
        "sets": [
          {
            "id": 1,
            "sessionId": 5,
            "exerciseId": 12,
            "exerciseName": "Bench Press (Barbell)",
            "itemType": "EXERCISE",
            "orderInSession": 1,
            "setNumber": 1,
            "plannedReps": 8,
            "plannedWeightKg": "80.0",
            "actualReps": 8,
            "actualWeightKg": "82.5",
            "durationSeconds": 0,
            "distanceKm": "0",
            "rpe": 8,
            "isPersonalBest": true,
            "notes": "",
            "completedAt": "2026-03-01T09:05:00"
          },
          {
            "id": 2,
            "sessionId": 5,
            "exerciseId": 12,
            "exerciseName": "Bench Press (Barbell)",
            "itemType": "EXERCISE",
            "orderInSession": 1,
            "setNumber": 2,
            "plannedReps": 8,
            "plannedWeightKg": "80.0",
            "actualReps": null,
            "actualWeightKg": null,
            "durationSeconds": 0,
            "distanceKm": "0",
            "rpe": null,
            "isPersonalBest": false,
            "notes": "",
            "completedAt": null
          }
        ]
      },
      {
        "orderInSession": 2,
        "itemType": "REST",
        "exerciseId": 0,
        "exerciseName": "",
        "sets": [
          {
            "id": 3,
            "sessionId": 5,
            "itemType": "REST",
            "orderInSession": 2,
            "setNumber": 1,
            "durationSeconds": 120,
            "isPersonalBest": false,
            "notes": ""
          }
        ]
      }
    ]
  }
}
```

### Session response fields

| Field | Type | Description |
|---|---|---|
| `id` | Long | Session ID. |
| `name` | String | Session name. |
| `routineId` | Long | Source routine ID. `0` if freestyle. |
| `routineName` | String | Source routine name. `""` if freestyle. |
| `status` | String | `"IN_PROGRESS"` or `"COMPLETED"` |
| `startedAt` | String | ISO-8601 session start. Never null. |
| `completedAt` | String \| null | ISO-8601 completion time. `null` while IN_PROGRESS. |
| `notes` | String | Session-level notes. |
| `createdAt` | String | ISO-8601 record creation time. |
| `updatedAt` | String | ISO-8601 last update time. |
| `exercises` | Array | Sets grouped by `orderInSession`. Each group is one exercise slot, rest block, or cardio block. |

### Set response fields

| Field | Type | Description |
|---|---|---|
| `id` | Long | Set row ID — use this in `PATCH` and `DELETE` calls. |
| `sessionId` | Long | Parent session ID. |
| `exerciseId` | Long | Exercise ID. `0` for REST and CARDIO sets. |
| `exerciseName` | String | Exercise name at the time of logging. `""` for REST. Activity name for CARDIO e.g. `"Treadmill"`. |
| `itemType` | String | `"EXERCISE"` \| `"REST"` \| `"CARDIO"` |
| `orderInSession` | Int | Slot number — all sets for the same exercise share this value. |
| `setNumber` | Int | 1, 2, 3… within this slot. |
| `plannedReps` | Int | From routine template. `0` if not planned (freestyle or REST/CARDIO). |
| `plannedWeightKg` | String | From routine template. `"0"` if not planned. |
| `actualReps` | Int \| null | Reps completed. `null` = not yet done. |
| `actualWeightKg` | String \| null | Weight used. `null` = not yet done. |
| `durationSeconds` | Int | For REST: rest duration. For CARDIO: workout time. `0` for EXERCISE. |
| `distanceKm` | String | CARDIO distance e.g. `"1.2"`. `"0"` for non-cardio. |
| `rpe` | Int \| null | Rate of perceived exertion 1–10. `null` = not set. |
| `isPersonalBest` | Boolean | `true` when a new weight PR is detected by `/complete`. Always `false` until the session is completed. |
| `notes` | String | Per-set notes. |
| `completedAt` | String \| null | ISO-8601 when this set was logged. `null` = not yet done. |

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | Session belongs to a different user. |
| 404 | `NOT_FOUND` | No session with that ID exists. |

---

## PATCH /api/v1/workouts/{id}

**Update session name and/or notes.**

Applies to both `IN_PROGRESS` and `COMPLETED` sessions. All fields optional — omitted fields retain their current values.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Session ID. |

**Request Body** — all fields optional

| Field | Type | Description |
|---|---|---|
| `name` | String | New session name. |
| `notes` | String | New session notes. |

```json
{ "name": "Leg Day", "notes": "Felt strong today — PR on squats!" }
```

**Response — 200 OK** — full `WorkoutSessionResponse`.

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | Session belongs to a different user. |
| 404 | `NOT_FOUND` | No session with that ID exists. |

---

## POST /api/v1/workouts/{id}/complete

**Mark the session as COMPLETED and auto-detect personal bests.**

After this call the session status changes to `COMPLETED`, all EXERCISE sets are evaluated for PBs, and the session is locked — no further set changes are allowed.

**Idempotent:** Calling this on an already-COMPLETED session is a no-op and returns the session unchanged (no error).

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Session ID. |

**Query Parameters**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `force` | Boolean | No | `false` | Bypass the "no exercise sets logged" guard. By default, if the session contains EXERCISE sets but none have `actualReps > 0`, the server rejects completion to prevent accidentally finalising an empty session. Pass `?force=true` to override — useful for REST-only, CARDIO-only, or stretching sessions. |

**Examples**
```
POST /api/v1/workouts/5/complete
POST /api/v1/workouts/5/complete?force=true
```

**PB detection logic (applied at completion):**
1. Collects all EXERCISE sets in this session where `actualReps > 0`.
2. For each set, fetches historical sets for the same exercise where `actualReps ≥` the current set's `actualReps`.
3. If the new weight **strictly exceeds** (`>`) the best historical weight in that range → `isPersonalBest = true`.
4. Ties (new weight equals previous best exactly) are **not** flagged.
5. First time at this rep count → automatically a PB.

**Response — 200 OK** — full `WorkoutSessionResponse` with `status: "COMPLETED"` and PB flags set on qualifying sets.

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | Session has EXERCISE sets but none are logged. Pass `?force=true` to bypass. |
| 403 | `FORBIDDEN` | Session belongs to a different user. |
| 404 | `NOT_FOUND` | No session with that ID exists. |

---

## DELETE /api/v1/workouts/{id}

**Delete a session and all its sets.**

Permanently removes the session and all associated `WorkoutSet` rows. Returns no body on success.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Session ID. |

**Response — 204 No Content** (empty body)

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | Session belongs to a different user. |
| 404 | `NOT_FOUND` | No session with that ID exists. |

---

## POST /api/v1/workouts/{sessionId}/sets

**Add a set to an in-progress session.**

Only allowed while the session is `IN_PROGRESS`. Returns `409 CONFLICT` if the session is already `COMPLETED`.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `sessionId` | Long | Session ID. |

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `itemType` | String | Yes | `"EXERCISE"` \| `"REST"` \| `"CARDIO"` |
| `orderInSession` | Int | Yes | Slot number. All sets for the same exercise should share the same value. |
| `setNumber` | Int | Yes | 1-based set number within this slot. |
| `exerciseId` | Long | Cond. | Required for EXERCISE sets. Use `0` or omit for REST/CARDIO. |
| `exerciseName` | String | Cond. | Required for EXERCISE and CARDIO. CARDIO: use the activity name e.g. `"Treadmill"`. |
| `plannedReps` | Int | No | Planned reps. Defaults to `0`. |
| `plannedWeightKg` | String | No | Planned weight as a numeric string e.g. `"80.0"`. Defaults to `"0"`. |
| `actualReps` | Int | No | Reps completed. Defaults to `0`. |
| `actualWeightKg` | String | No | Weight used e.g. `"82.5"`. Defaults to `"0"`. |
| `durationSeconds` | Int | No | REST: rest duration. CARDIO: workout time. Defaults to `0`. |
| `distanceKm` | String | No | CARDIO distance e.g. `"1.2"`. Defaults to `"0"`. |
| `rpe` | Int | No | Rate of perceived exertion 1–10. Defaults to `0`. |
| `notes` | String | No | Per-set notes. Defaults to `""`. |
| `completedAt` | String | No | ISO-8601 datetime when this set was completed e.g. `"2026-03-01T09:12:00"`. |

**Example — EXERCISE set:**
```json
{
  "itemType": "EXERCISE",
  "exerciseId": 12,
  "exerciseName": "Bench Press (Barbell)",
  "orderInSession": 1,
  "setNumber": 3,
  "plannedReps": 8,
  "plannedWeightKg": "80.0",
  "actualReps": 7,
  "actualWeightKg": "80.0",
  "rpe": 9,
  "completedAt": "2026-03-01T09:12:00"
}
```

**Example — REST set:**
```json
{
  "itemType": "REST",
  "orderInSession": 2,
  "setNumber": 1,
  "durationSeconds": 120
}
```

**Example — CARDIO set:**
```json
{
  "itemType": "CARDIO",
  "exerciseName": "Treadmill",
  "orderInSession": 3,
  "setNumber": 1,
  "durationSeconds": 600,
  "distanceKm": "1.2"
}
```

**Response — 201 Created** — `WorkoutSetResponse` (same set field structure as in `GET /api/v1/workouts/{id}`).

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | `itemType` is not `EXERCISE`, `REST`, or `CARDIO` |
| 400 | `INVALID_REQUEST` | `completedAt`, `plannedWeightKg`, or `actualWeightKg` is malformed |
| 403 | `FORBIDDEN` | Session belongs to a different user |
| 404 | `NOT_FOUND` | Session not found |
| 409 | `CONFLICT` | Session is already `COMPLETED` — sets cannot be added after completion |

---

## PATCH /api/v1/workouts/{sessionId}/sets/{setId}

**Update a set's actual results.**

Only allowed while the session is `IN_PROGRESS`. Returns `409 CONFLICT` if the session is already `COMPLETED`. All fields are optional — only supplied fields are overwritten.

This is the primary endpoint for logging actual performance — called each time the user finishes a set and enters their actual reps, weight, and RPE.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `sessionId` | Long | Session ID. |
| `setId` | Long | Set ID from the session's sets list. |

**Request Body** — all fields optional

| Field | Type | Description |
|---|---|---|
| `actualReps` | Int | Reps completed. |
| `actualWeightKg` | String | Weight used e.g. `"82.5"`. |
| `plannedReps` | Int | Update the planned rep target. |
| `plannedWeightKg` | String | Update the planned weight target. |
| `durationSeconds` | Int | Update REST/CARDIO duration. |
| `distanceKm` | String | Update CARDIO distance. |
| `rpe` | Int | Rate of perceived exertion 1–10. |
| `notes` | String | Set notes. |
| `completedAt` | String | ISO-8601 when this set was completed e.g. `"2026-03-01T09:05:00"`. |

**Example — log a completed set:**
```json
{
  "actualReps": 8,
  "actualWeightKg": "82.5",
  "rpe": 8,
  "completedAt": "2026-03-01T09:05:00"
}
```

**Response — 200 OK** — updated `WorkoutSetResponse`.

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | `completedAt`, `plannedWeightKg`, or `actualWeightKg` is malformed |
| 403 | `FORBIDDEN` | Session belongs to a different user |
| 404 | `NOT_FOUND` | Session or set not found, or set does not belong to this session |
| 409 | `CONFLICT` | Session is already `COMPLETED` — sets cannot be modified after completion |

---

## DELETE /api/v1/workouts/{sessionId}/sets/{setId}

**Remove a set from a session.**

Only allowed while the session is `IN_PROGRESS`. Returns `409 CONFLICT` if the session is already `COMPLETED`. Returns no body on success.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `sessionId` | Long | Session ID. |
| `setId` | Long | Set ID to remove. |

**Response — 204 No Content** (empty body)

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | Session belongs to a different user |
| 404 | `NOT_FOUND` | Session or set not found, or set does not belong to this session |
| 409 | `CONFLICT` | Session is already `COMPLETED` — sets cannot be deleted after completion |

---

## GET /api/v1/exercises/{exerciseId}/history

**Get all completed EXERCISE sets for a specific exercise.**

Returns the calling user's logged sets for this exercise across all COMPLETED sessions, most recent first. Use this to power strength progress charts and historical performance views.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `exerciseId` | Long | Exercise ID. |

**Query Parameters**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `page` | Int | No | `1` | Page number. |
| `pageSize` | Int | No | `50` | Items per page. Max `100`. |

**Examples**
```
GET /api/v1/exercises/12/history
GET /api/v1/exercises/12/history?page=1&pageSize=100
```

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "sessionId": 5,
      "exerciseId": 12,
      "exerciseName": "Bench Press (Barbell)",
      "itemType": "EXERCISE",
      "setNumber": 1,
      "plannedReps": 8,
      "plannedWeightKg": "80.0",
      "actualReps": 8,
      "actualWeightKg": "82.5",
      "rpe": 8,
      "isPersonalBest": true,
      "notes": "",
      "completedAt": "2026-03-01T09:05:00"
    }
  ],
  "meta": { "page": 1, "pageSize": 50, "total": 45 }
}
```

Returns an empty `data` array if the user has no history for this exercise.

---

## GET /api/v1/exercises/{exerciseId}/pbs

**Get personal bests for an exercise — one record per distinct rep count.**

Returns the highest-weight set the user has ever logged for each rep count, sorted by `actualReps` descending. Use this to display a records screen (e.g. "1RM: 105 kg · 5RM: 92.5 kg · 8RM: 82.5 kg").

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `exerciseId` | Long | Exercise ID. |

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "id": 7,
      "exerciseId": 12,
      "exerciseName": "Bench Press (Barbell)",
      "actualReps": 8,
      "actualWeightKg": "82.5",
      "isPersonalBest": true,
      "completedAt": "2026-03-01T09:05:00"
    },
    {
      "id": 3,
      "exerciseId": 12,
      "exerciseName": "Bench Press (Barbell)",
      "actualReps": 5,
      "actualWeightKg": "92.5",
      "isPersonalBest": true,
      "completedAt": "2026-02-15T09:20:00"
    }
  ]
}
```

Returns an empty array if the user has no logged sets for this exercise.

---

## Error cases summary

| Scenario | HTTP | Code |
|---|---|---|
| Session not found | 404 | `NOT_FOUND` |
| Set not found or belongs to a different session | 404 | `NOT_FOUND` |
| Caller is not the session owner | 403 | `FORBIDDEN` |
| Routine not found (on start) | 404 | `NOT_FOUND` |
| Private routine, caller is not creator (on start) | 403 | `FORBIDDEN` |
| Adding / updating / deleting a set on a COMPLETED session | 409 | `CONFLICT` |
| Completing a session with no exercise sets logged (use `?force=true` to bypass) | 400 | `INVALID_REQUEST` |

---

## Common patterns

### Logging a set as the user completes it

Call `PATCH` with actual results immediately after each set:
```
User finishes set 1 of Bench Press
→ PATCH /api/v1/workouts/{sessionId}/sets/{setId}
  { "actualReps": 8, "actualWeightKg": "82.5", "rpe": 8, "completedAt": "2026-03-01T09:05:00" }
```

### Getting set IDs for PATCH / DELETE

Set IDs (`id`) are returned in every `WorkoutSetResponse` object. Load them from `GET /api/v1/workouts/{id}` — the `exercises[].sets[].id` field. Do not construct or guess IDs.

### Understanding `actualReps: null` vs `0`

- `null` → set **has not been done yet** (pre-populated from routine, pending).
- `0` → set was explicitly logged with zero reps (failed attempt or placeholder).
- PB detection only considers sets where `actualReps > 0`.

### Completing a REST-only or CARDIO-only session

If a session contains no `EXERCISE` items at all, `/complete` succeeds without `?force=true`. The guard only activates when EXERCISE sets exist but none have been logged. For a mixed session where you want to finalise without logging all exercises, use `?force=true`.

### Displaying a set as "not yet done"

`actualReps: null` is the "pending" marker. Render these with placeholder values (e.g. greyed out `-- / --`). Once `PATCH` is called with actual values the set is live.
