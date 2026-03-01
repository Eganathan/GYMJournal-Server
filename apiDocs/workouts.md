# Workouts API

> Workout session logging — start a session from a routine (or freestyle), log sets in real time, mark the session complete, and track personal bests and exercise history.

---

## Concepts

A **WorkoutSession** is one execution of a routine (or a free-form workout). It has:
- A **status**: `IN_PROGRESS` while active, `COMPLETED` when done.
- An ordered list of **WorkoutSets** — one row per set (or REST/CARDIO block).

WorkoutSets can be added, updated, or deleted at any time while the session is `IN_PROGRESS`. When `POST /complete` is called, the session is locked and **personal bests** are automatically detected.

### Set item types

| `itemType` | Meaning |
|---|---|
| `EXERCISE` | A set of an exercise (reps + weight) |
| `REST` | A timed rest period |
| `CARDIO` | A cardio block (time + optional distance) |

---

## Endpoint Summary

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/workouts` | Required | Start a session |
| `GET` | `/api/v1/workouts` | Required | List own sessions |
| `GET` | `/api/v1/workouts/{id}` | Required | Full session with grouped sets |
| `PATCH` | `/api/v1/workouts/{id}` | Required (creator) | Update name/notes |
| `POST` | `/api/v1/workouts/{id}/complete` | Required (creator) | Mark completed, detect PBs |
| `DELETE` | `/api/v1/workouts/{id}` | Required (creator) | Delete session + all sets |
| `POST` | `/api/v1/workouts/{sessionId}/sets` | Required (creator) | Add a set |
| `PUT` | `/api/v1/workouts/{sessionId}/sets/{setId}` | Required (creator) | Update a set |
| `DELETE` | `/api/v1/workouts/{sessionId}/sets/{setId}` | Required (creator) | Delete a set |
| `GET` | `/api/v1/exercises/{exerciseId}/history` | Required | Exercise set history |
| `GET` | `/api/v1/exercises/{exerciseId}/pbs` | Required | Personal bests by rep count |

---

## POST /api/v1/workouts

Start a new workout session.

If `routineId` is provided, the session is pre-populated with sets from the routine's items:
- `EXERCISE` items → one `WorkoutSet` row per planned set (with `plannedReps` / `plannedWeightKg` filled, `actualReps` = 0)
- `REST` items → one `WorkoutSet` row with `durationSeconds` filled
- `CARDIO` items → one `WorkoutSet` row with `durationSeconds` filled

### Request body

```json
{
  "routineId": 1,
  "name": "Push Day - Mon 2 Mar",
  "startedAt": "2026-03-02T09:00:00"
}
```

All fields are optional:
- `routineId` — omit for a free-form standalone session
- `name` — auto-generated as `"<routineName> - <date>"` or `"Free Workout - <date>"` if omitted
- `startedAt` — ISO-8601 (e.g. `"2026-03-01T09:00:00"`); defaults to current server time if omitted. Stored as a **DateTime** column — pass any ISO-8601 datetime and the server normalises the `T` separator.

> **Backdating**: supplying a past `startedAt` (e.g. logging yesterday's workout) places the session in the correct chronological position in `GET /api/v1/workouts` because the list is ordered by `startedAt`, not by insert time.

### Response

`201 Created` — full `WorkoutSessionResponse` (see below).

---

## GET /api/v1/workouts

List the calling user's sessions ordered by `startedAt DESC` (most recent workout first, regardless of when the record was inserted — backdated sessions appear in true chronological order).

### Query parameters

| Param | Type | Default | Description |
|---|---|---|---|
| `status` | string | — | `IN_PROGRESS` or `COMPLETED`; omit for all |
| `page` | int | `1` | Page number |
| `pageSize` | int | `20` | Items per page (max 50) |

### Response

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
      "updatedAt": "2026-03-01T10:15:00"
    }
  ],
  "meta": { "page": 1, "pageSize": 20, "total": 12 }
}
```

---

## GET /api/v1/workouts/{id}

Returns the full session with all sets grouped by exercise slot.

### Response

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
            "setNumber": 2,
            "plannedReps": 8,
            "plannedWeightKg": "80.0",
            "actualReps": null,
            "actualWeightKg": null,
            "rpe": null,
            "isPersonalBest": false,
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
          { "id": 3, "setNumber": 1, "durationSeconds": 120, "itemType": "REST" }
        ]
      }
    ]
  }
}
```

### Session datetime fields

`startedAt` and `completedAt` are stored as **DateTime** columns in Catalyst DataStore.

| Field | Stored type | Returned as | Null? |
|---|---|---|---|
| `startedAt` | DateTime | ISO-8601 string e.g. `"2026-03-01T09:00:00"` | Never null |
| `completedAt` | DateTime | ISO-8601 string or `null` | `null` until `/complete` is called |

`completedAt` is **omitted** from the DataStore insert entirely (never written as an empty string) — Catalyst would reject an empty string for a DateTime column. On read, a null DB value becomes `null` in the JSON response.

---

### Set response fields

| Field | Type | Notes |
|---|---|---|
| `id` | long | Set row ID |
| `sessionId` | long | Parent session ID |
| `exerciseId` | long | `0` for REST/CARDIO |
| `exerciseName` | string | `""` for REST; activity name for CARDIO |
| `itemType` | string | `EXERCISE` \| `REST` \| `CARDIO` |
| `orderInSession` | int | Slot number (all sets for the same exercise share this) |
| `setNumber` | int | 1, 2, 3… within this slot |
| `plannedReps` | int | From routine template (0 if not planned) |
| `plannedWeightKg` | string | From template |
| `actualReps` | int \| null | `null` = not done yet |
| `actualWeightKg` | string \| null | `null` = not done yet |
| `durationSeconds` | int | REST duration or CARDIO time; 0 if N/A |
| `distanceKm` | string | CARDIO only; `"0"` otherwise |
| `rpe` | int \| null | 1–10; `null` = not set |
| `isPersonalBest` | boolean | Set by `/complete` |
| `notes` | string | Per-set notes |
| `completedAt` | string \| null | ISO-8601; `null` = not done yet |

---

## PATCH /api/v1/workouts/{id}

Update session name or notes. All fields optional.

```json
{ "name": "Leg Day", "notes": "Felt strong today" }
```

**Response:** `200 OK` — full `WorkoutSessionResponse`.

---

## POST /api/v1/workouts/{id}/complete

Marks the session `COMPLETED` and auto-detects personal bests.

**PB detection logic:**
1. For each EXERCISE set where `actualReps > 0`, compare weight against all historical sets for the same exercise with the same or more reps.
2. If the new weight equals or beats the historical best → `isPersonalBest = true`.

**Response:** `200 OK` — full `WorkoutSessionResponse` with PB flags populated.

> Calling `/complete` on an already-completed session is a no-op (returns unchanged session).

---

## DELETE /api/v1/workouts/{id}

Deletes the session and all its sets. Returns `204 No Content`.

---

## POST /api/v1/workouts/{sessionId}/sets

Add a set to a session.

### Request body

```json
{
  "exerciseId": 12,
  "exerciseName": "Bench Press (Barbell)",
  "itemType": "EXERCISE",
  "orderInSession": 1,
  "setNumber": 3,
  "plannedReps": 8,
  "plannedWeightKg": "80.0",
  "actualReps": 7,
  "actualWeightKg": "80.0",
  "rpe": 9,
  "notes": "",
  "completedAt": "2026-03-01T09:12:00"
}
```

For a **REST set:**
```json
{ "itemType": "REST", "orderInSession": 2, "setNumber": 1, "durationSeconds": 120 }
```

For a **CARDIO set:**
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

**Response:** `201 Created` — `WorkoutSetResponse`.

---

## PUT /api/v1/workouts/{sessionId}/sets/{setId}

Update an existing set. All fields optional.

```json
{
  "actualReps": 8,
  "actualWeightKg": "82.5",
  "rpe": 8,
  "completedAt": "2026-03-01T09:05:00"
}
```

**Response:** `200 OK` — updated `WorkoutSetResponse`.

---

## DELETE /api/v1/workouts/{sessionId}/sets/{setId}

Removes a set from the session. Returns `204 No Content`.

---

## GET /api/v1/exercises/{exerciseId}/history

Returns the calling user's completed `EXERCISE` sets for a specific exercise, most recent first.

### Query parameters

| Param | Default | Description |
|---|---|---|
| `page` | `1` | Page number |
| `pageSize` | `50` | Items per page (max 100) |

### Response

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
      "actualReps": 8,
      "actualWeightKg": "82.5",
      "rpe": 8,
      "isPersonalBest": true,
      "completedAt": "2026-03-01T09:05:00"
    }
  ],
  "meta": { "page": 1, "pageSize": 50, "total": 45 }
}
```

---

## GET /api/v1/exercises/{exerciseId}/pbs

Returns personal best sets for this exercise — one record per distinct rep count, showing the highest weight achieved.

Sorted by rep count **descending** (heaviest volume at top, lightest at bottom).

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

---

## Typical client flow

1. **Browse routines** → `GET /api/v1/routines`
2. **Start session** → `POST /api/v1/workouts` with `routineId`
3. **Display session** → `GET /api/v1/workouts/{id}` (sets pre-populated, `actualReps = null`)
4. **As user completes each set** → `PUT /api/v1/workouts/{sessionId}/sets/{setId}` with actual data
5. **User adds an unplanned exercise** → `POST /api/v1/workouts/{sessionId}/sets`
6. **User removes an exercise** → `DELETE /api/v1/workouts/{sessionId}/sets/{setId}`
7. **Finish workout** → `POST /api/v1/workouts/{id}/complete` (PBs auto-flagged)
8. **View exercise history** → `GET /api/v1/exercises/{id}/history`

---

## Error cases

| Scenario | HTTP | Error code |
|---|---|---|
| Session not found | 404 | `NOT_FOUND` |
| Set not found | 404 | `NOT_FOUND` |
| Caller is not session owner | 403 | `FORBIDDEN` |
| Routine not found (on start) | 404 | `NOT_FOUND` |
| Private routine, caller not creator (on start) | 403 | `FORBIDDEN` |
