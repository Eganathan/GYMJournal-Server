# Routines API

A **Routine** is a named, reusable workout template — an ordered list of exercises, rest periods, and cardio blocks with planned sets, reps, and target weights. Routines are the starting point for workout sessions: when you start a session with a `routineId`, the session is automatically pre-populated with sets derived from the routine's items.

**Base path:** `/api/v1/routines`
**Auth required:** Yes — all endpoints require authentication.
**Data visibility:** Mixed:
- Public routines (`isPublic: true`) are browsable by any authenticated user.
- Private routines (`isPublic: false`) are only visible to the creator.
- Only the creator can update or delete their own routines.
- Any user can clone a public routine into their own library.

---

## Key concepts

- A routine has an **items** array — an ordered list of workout blocks. Each item has an `order` (1-based position) and a `type`: `EXERCISE`, `REST`, or `CARDIO`.
- When a session is started from a routine, the routine's `EXERCISE` items are expanded into individual `WorkoutSet` rows (one per planned set), with `plannedReps` and `plannedWeightKg` pre-filled. See [workouts.md](./workouts.md) for full session behavior.
- **Updating `items` via `PUT` replaces the entire array** — send the complete new list of items, not a partial update. Omitting `items` from a PUT body leaves the items unchanged.
- The `estimatedMinutes` field is stored exactly as provided — the server does not compute it.

---

## Endpoint Summary

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/routines` | Required | Browse routines (public + own private) |
| `GET` | `/api/v1/routines/{id}` | Required | Full routine detail with all items |
| `POST` | `/api/v1/routines` | Required | Create a new routine |
| `PUT` | `/api/v1/routines/{id}` | Required (creator) | Update a routine |
| `DELETE` | `/api/v1/routines/{id}` | Required (creator) | Delete a routine |
| `POST` | `/api/v1/routines/{id}/clone` | Required | Clone a public routine to own library |

---

## Typical client flow

```
Browse library        → GET /api/v1/routines                         (public routines feed)
My routines           → GET /api/v1/routines?mine=true               (own library, including private)
View detail           → GET /api/v1/routines/{id}                    (full items list — exercises, rest, cardio)
Save a public routine → POST /api/v1/routines/{id}/clone             (creates a private copy for the user)
Start a workout       → POST /api/v1/workouts { routineId: id }      (see workouts.md)
Create routine        → POST /api/v1/routines
Edit routine          → PUT  /api/v1/routines/{id}
Delete routine        → DELETE /api/v1/routines/{id}
```

---

## GET /api/v1/routines

**Browse routines.**

By default returns all **public** routines. Use `?mine=true` to list the calling user's own routines (including private ones). Results are paginated and sorted by `updatedAt` descending.

**Query Parameters**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `mine` | Boolean | No | `false` | `true` = return only the calling user's routines (public and private). `false` = return all public routines. |
| `search` | String | No | — | Substring match on routine name (case-insensitive). |
| `page` | Int | No | `1` | Page number. |
| `pageSize` | Int | No | `20` | Items per page. Max `50`. |

**Examples**
```
GET /api/v1/routines
GET /api/v1/routines?mine=true
GET /api/v1/routines?search=push&page=1&pageSize=20
```

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "Push Day A",
      "description": "Chest, shoulders, triceps",
      "estimatedMinutes": 60,
      "tags": ["push", "chest"],
      "itemCount": 5,
      "isPublic": true,
      "createdBy": "userId123",
      "updatedAt": "2026-03-01T10:00:00"
    }
  ],
  "meta": { "page": 1, "pageSize": 20, "total": 3 }
}
```

> This is a **summary view** — items are not included. Use `GET /api/v1/routines/{id}` to load the full routine with all items.

| Field | Type | Description |
|---|---|---|
| `id` | Long | Routine ID. Use this as `routineId` when starting a workout session. |
| `name` | String | Routine name. |
| `description` | String | Short description. |
| `estimatedMinutes` | Int | Estimated duration in minutes. `0` if not set. |
| `tags` | String[] | Free-text tags. |
| `itemCount` | Int | Number of items in the routine. |
| `isPublic` | Boolean | `true` = visible to all users; `false` = private to creator. |
| `createdBy` | String | User ID of the creator. |
| `updatedAt` | String | ISO-8601 timestamp. |

---

## GET /api/v1/routines/{id}

**Get full routine detail including all items.**

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Routine ID. |

**Visibility:** Public routines are readable by any authenticated user. A private routine returns `403 FORBIDDEN` if the caller is not the creator.

**Response — 200 OK**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Push Day A",
    "description": "Chest, shoulders, triceps",
    "items": [
      {
        "order": 1,
        "type": "EXERCISE",
        "exerciseId": 12,
        "exerciseName": "Bench Press (Barbell)",
        "sets": 4,
        "repsPerSet": 8,
        "weightKg": "80.0",
        "restAfterSeconds": 90,
        "notes": null
      },
      {
        "order": 2,
        "type": "REST",
        "durationSeconds": 120
      },
      {
        "order": 3,
        "type": "CARDIO",
        "cardioName": "Treadmill",
        "durationMinutes": 10,
        "targetSpeedKmh": "8.0"
      }
    ],
    "estimatedMinutes": 60,
    "tags": ["push", "chest"],
    "isPublic": true,
    "createdBy": "userId123",
    "createdAt": "2026-03-01T10:00:00",
    "updatedAt": "2026-03-01T10:00:00"
  }
}
```

### Item fields by type

**EXERCISE item:**

| Field | Type | Required | Description |
|---|---|---|---|
| `order` | Int | Yes | 1-based position in routine. |
| `type` | String | Yes | `"EXERCISE"` |
| `exerciseId` | Long | Yes | ID from the Exercises table. |
| `exerciseName` | String | Yes | Display name (stored for quick display — may differ from current exercise name if renamed). |
| `sets` | Int | Yes | Number of planned sets. When a session is started from this routine, one `WorkoutSet` row is created per set. |
| `repsPerSet` | Int | Yes | Planned reps per set. Becomes `plannedReps` on the set rows. |
| `weightKg` | String \| null | No | Target weight e.g. `"80.0"`. `null` = bodyweight. Becomes `plannedWeightKg` on the set rows. |
| `restAfterSeconds` | Int \| null | No | Rest period following this exercise slot. |
| `notes` | String \| null | No | Coaching notes for this exercise. |

**REST item:**

| Field | Type | Required | Description |
|---|---|---|---|
| `order` | Int | Yes | Position in routine. |
| `type` | String | Yes | `"REST"` |
| `durationSeconds` | Int | Yes | Rest duration in seconds. |

**CARDIO item:**

| Field | Type | Required | Description |
|---|---|---|---|
| `order` | Int | Yes | Position in routine. |
| `type` | String | Yes | `"CARDIO"` |
| `cardioName` | String | Yes | Activity name e.g. `"Treadmill"`, `"Rowing Machine"`. |
| `durationMinutes` | Int | Yes | Planned duration in minutes. Converted to `durationSeconds` on the session set row. |
| `targetSpeedKmh` | String \| null | No | Target speed. |

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | Routine is private and the calling user is not the creator. |
| 404 | `NOT_FOUND` | No routine with that ID exists. |

---

## POST /api/v1/routines

**Create a new routine template.**

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | String | Yes | Routine name. |
| `description` | String | No | Short description. Defaults to `""`. |
| `items` | Array | Yes | At least one item required. See item field tables above. |
| `estimatedMinutes` | Int | No | Estimated workout duration. Defaults to `0`. |
| `tags` | String[] | No | Free-text tags. Defaults to `[]`. |
| `isPublic` | Boolean | No | `true` = visible to all users. Defaults to `false`. |

```json
{
  "name": "Push Day A",
  "description": "Chest, shoulders, triceps",
  "items": [
    {
      "order": 1,
      "type": "EXERCISE",
      "exerciseId": 12,
      "exerciseName": "Bench Press (Barbell)",
      "sets": 4,
      "repsPerSet": 8,
      "weightKg": "80.0",
      "restAfterSeconds": 90
    },
    {
      "order": 2,
      "type": "REST",
      "durationSeconds": 120
    },
    {
      "order": 3,
      "type": "CARDIO",
      "cardioName": "Treadmill",
      "durationMinutes": 10,
      "targetSpeedKmh": "8.0"
    }
  ],
  "estimatedMinutes": 60,
  "tags": ["push", "chest"],
  "isPublic": true
}
```

**Response — 201 Created** — full `RoutineResponse` (same shape as `GET /api/v1/routines/{id}`).

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `name` is missing or blank |
| 400 | `VALIDATION_ERROR` | `items` is missing or empty |

---

## PUT /api/v1/routines/{id}

**Update an existing routine.**

Only the creator can edit. All top-level fields are optional — **omitted fields retain their current values**. The one important exception is `items`: **if you include `items`, it completely replaces the existing items array**. To leave items unchanged, omit the `items` key entirely.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Routine ID. |

**Request Body** — all fields optional

| Field | Type | Description |
|---|---|---|
| `name` | String | New routine name. |
| `description` | String | New description. |
| `items` | Array | **Replaces the entire items array.** Send the full new list. Omit to leave items unchanged. |
| `estimatedMinutes` | Int | New estimated duration. |
| `tags` | String[] | Replaces the tags array. |
| `isPublic` | Boolean | Change visibility. |

```json
{
  "name": "Push Day A (Heavy)",
  "isPublic": false
}
```

```json
{
  "items": [
    { "order": 1, "type": "EXERCISE", "exerciseId": 12, "exerciseName": "Bench Press (Barbell)", "sets": 5, "repsPerSet": 5, "weightKg": "90.0" },
    { "order": 2, "type": "REST", "durationSeconds": 180 }
  ]
}
```

**Response — 200 OK** — full `RoutineResponse`.

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | Caller is not the creator. |
| 404 | `NOT_FOUND` | No routine with that ID exists. |
| 400 | `VALIDATION_ERROR` | `items` is provided but empty (must have at least one item if included). |

---

## DELETE /api/v1/routines/{id}

**Delete a routine.**

Only the creator can delete. Returns no body on success.

> **Note:** Deleting a routine does not affect existing workout sessions that were started from it. Those sessions retain a copy of the routine name and their pre-populated sets.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Routine ID. |

**Response — 204 No Content** (empty body)

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | Caller is not the creator. |
| 404 | `NOT_FOUND` | No routine with that ID exists. |

---

## POST /api/v1/routines/{id}/clone

**Clone a routine into the calling user's library.**

Creates a copy of the routine owned by the caller. The clone is always **private** (`isPublic: false`) regardless of the original's visibility. The caller can then edit the clone without affecting the original.

**Who can clone:**
- Any user can clone a public routine.
- Only the creator can clone their own private routine.
- Returns `403` if the routine is private and the caller is not the creator.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | ID of the routine to clone. |

**No request body.**

**Response — 201 Created** — the new `RoutineResponse` owned by the calling user, with `isPublic: false`.

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | Routine is private and the calling user is not the creator. |
| 404 | `NOT_FOUND` | No routine with that ID exists. |

---

## Common patterns

### Starting a workout from a routine

```
1. GET /api/v1/routines           → browse and pick a routine
2. GET /api/v1/routines/{id}      → preview the items before starting
3. POST /api/v1/workouts { routineId: id }  → creates session with pre-populated sets
4. GET /api/v1/workouts/{sessionId}         → shows all pre-populated WorkoutSet rows
```

### Editing a public community routine

You cannot edit a routine you didn't create. Instead, clone it first:
```
POST /api/v1/routines/{id}/clone   → creates a private copy you own
PUT  /api/v1/routines/{newId}      → edit your copy freely
```

### Reordering items

When updating items via `PUT`, include all items with their new `order` values. The server replaces the entire array:
```json
{
  "items": [
    { "order": 1, "type": "EXERCISE", "exerciseId": 14, "exerciseName": "Squat", "sets": 4, "repsPerSet": 6, "weightKg": "100.0" },
    { "order": 2, "type": "REST", "durationSeconds": 180 },
    { "order": 3, "type": "EXERCISE", "exerciseId": 22, "exerciseName": "Leg Press", "sets": 3, "repsPerSet": 10, "weightKg": "120.0" }
  ]
}
```
