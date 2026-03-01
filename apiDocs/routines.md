# Routines API

> Routine templates — create reusable workout plans, browse the community library, and clone public routines into your own library.

---

## Concepts

A **Routine** is a named, ordered workout template. It contains an array of **items**:

| Item type | Purpose |
|---|---|
| `EXERCISE` | A planned exercise with sets, reps, and target weight |
| `REST` | A timed rest period |
| `CARDIO` | A cardio block (treadmill, rowing, etc.) |

Routines are either **public** (`isPublic: true`) — browsable and cloneable by any user — or **private** (`isPublic: false`) — visible only to the creator.

---

## Endpoint Summary

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/routines` | Required | Browse routines |
| `GET` | `/api/v1/routines/{id}` | Required | Get single routine |
| `POST` | `/api/v1/routines` | Required | Create routine |
| `PUT` | `/api/v1/routines/{id}` | Required (creator) | Update routine |
| `DELETE` | `/api/v1/routines/{id}` | Required (creator) | Delete routine |
| `POST` | `/api/v1/routines/{id}/clone` | Required | Clone routine to own library |

---

## GET /api/v1/routines

Browse routines. By default returns all **public** routines. Use `?mine=true` to list your own (including private ones).

### Query parameters

| Param | Type | Default | Description |
|---|---|---|---|
| `mine` | boolean | `false` | `true` = only show the calling user's routines |
| `search` | string | — | Substring match on routine name (case-insensitive) |
| `page` | int | `1` | Page number |
| `pageSize` | int | `20` | Items per page (max 50) |

### Response

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

---

## GET /api/v1/routines/{id}

Returns the full routine detail including all items.

**Visibility**: Public routines are accessible to any authenticated user. Private routines return `403 FORBIDDEN` if the caller is not the creator.

### Response

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

| Field | Type | Description |
|---|---|---|
| `order` | int | Position in routine (1-based) |
| `type` | string | `"EXERCISE"` |
| `exerciseId` | long | ID from the Exercises table |
| `exerciseName` | string | Denormalised display name |
| `sets` | int | Number of planned sets |
| `repsPerSet` | int | Planned reps per set |
| `weightKg` | string \| null | Target weight e.g. `"80.0"`; `null` = bodyweight |
| `restAfterSeconds` | int \| null | Rest period following this exercise |
| `notes` | string \| null | Coaching notes |

**REST item:**

| Field | Type | Description |
|---|---|---|
| `order` | int | Position in routine |
| `type` | string | `"REST"` |
| `durationSeconds` | int | Rest duration |

**CARDIO item:**

| Field | Type | Description |
|---|---|---|
| `order` | int | Position in routine |
| `type` | string | `"CARDIO"` |
| `cardioName` | string | Activity name e.g. `"Treadmill"` |
| `durationMinutes` | int | Planned duration |
| `targetSpeedKmh` | string \| null | Target speed |

---

## POST /api/v1/routines

Create a new routine template.

### Request body

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
    }
  ],
  "estimatedMinutes": 60,
  "tags": ["push", "chest"],
  "isPublic": true
}
```

**Required:** `name`, `items` (at least one item).

### Response

`201 Created` with the full `RoutineResponse` (same shape as `GET /{id}`).

---

## PUT /api/v1/routines/{id}

Update an existing routine. Only the creator can edit. All fields are optional — only supplied fields are overwritten.

### Request body

```json
{
  "name": "Push Day A (updated)",
  "items": [ ... ],
  "isPublic": false
}
```

### Response

`200 OK` with the updated `RoutineResponse`.

---

## DELETE /api/v1/routines/{id}

Delete a routine. Only the creator can delete.

**Response:** `204 No Content`

---

## POST /api/v1/routines/{id}/clone

Creates a copy of the routine in the caller's library. The clone is always **private** (`isPublic: false`).

**Who can clone?**
- Any user can clone a public routine.
- Only the creator can clone their own private routine.
- Returns `403` if the routine is private and the caller is not the creator.

### Response

`201 Created` with the new `RoutineResponse` (owned by the caller, `isPublic: false`).

---

## Error cases

| Scenario | HTTP | Error code |
|---|---|---|
| `name` missing or blank | 400 | `VALIDATION_ERROR` |
| `items` array is empty | 400 | `VALIDATION_ERROR` |
| Routine ID not found | 404 | `NOT_FOUND` |
| Private routine, caller is not creator | 403 | `FORBIDDEN` |
| Update/delete by non-creator | 403 | `FORBIDDEN` |
