# Exercises API

The Exercises API is the **shared reference library** at the heart of the app. It stores every exercise definition, the muscle group catalogue, and the equipment list. All other workout-related APIs reference exercises by ID — routines use `exerciseId` in their items, and workout sessions log sets against `exerciseId`. Media files (images, videos) are uploaded separately via the [Media Upload API](./media.md) and then linked here via `PUT /api/v1/exercises/{id}`.

**Base path:** `/api/v1/exercises`
**Auth required:** Yes — all endpoints require authentication.
**Data visibility:** Mixed:
- Exercises, muscle groups, and equipment are **public** — any authenticated user can read them.
- Only the **creator** of an exercise can update or delete it (`403 FORBIDDEN` otherwise).
- The admin seed endpoint is open to any authenticated user (for initial setup only).

---

## Key concepts

- **Muscle groups** (`/categories`) and **Equipment** are lookup tables. Populate them once using the seed endpoint, then reference them by ID when creating exercises.
- **Exercises** form the community library. Any user can contribute a new exercise. Exercises reference a `primaryMuscleId` (muscle group ID) and an `equipmentId` (equipment ID).
- **`secondaryMuscles`** is a **free-text string array** — supply human-readable muscle names (e.g. `["Rhomboids", "Biceps"]`), not IDs.
- **Images and videos** are not uploaded via this API. Use `POST /api/v1/media/upload` to upload a file and get a URL, then attach it here via `PUT /api/v1/exercises/{id}` with `imageUrl` or `videoUrl`.

---

## Endpoint Summary

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/exercises/categories` | Required | List all muscle groups |
| `POST` | `/api/v1/exercises/categories` | Required | Add a muscle group |
| `GET` | `/api/v1/exercises/equipment` | Required | List all equipment types |
| `POST` | `/api/v1/exercises/equipment` | Required | Add an equipment type |
| `GET` | `/api/v1/exercises` | Required | Browse exercise library (paginated) |
| `GET` | `/api/v1/exercises/{id}` | Required | Full exercise detail |
| `POST` | `/api/v1/exercises` | Required | Create a new exercise |
| `PUT` | `/api/v1/exercises/{id}` | Required (creator) | Update an exercise (incl. attaching media URLs) |
| `DELETE` | `/api/v1/exercises/{id}` | Required (creator) | Delete an exercise |
| `GET` | `/api/v1/exercises/{id}/history` | Required | Set history for an exercise (logged sets) |
| `GET` | `/api/v1/exercises/{id}/pbs` | Required | Personal bests for an exercise |
| `POST` | `/api/v1/admin/seed` | Required | Seed default muscle groups + equipment |

> **Note:** `/api/v1/exercises/{id}/history` and `/api/v1/exercises/{id}/pbs` are documented in [workouts.md](./workouts.md) — they are part of the workout logging system and are placed here for discovery only.

---

## Typical client flow

```
App first launch      → POST /api/v1/admin/seed                         (one-time setup — skip on subsequent launches)
App launch            → GET /api/v1/exercises/categories                 (cache for filter UI and body diagram)
                        GET /api/v1/exercises/equipment                  (cache for filter UI)
                        GET /api/v1/exercises                            (populate exercise library screen)

Browse exercises      → GET /api/v1/exercises?categoryId=3&page=1
                        GET /api/v1/exercises/{id}                       (user taps an exercise)

Create exercise       → POST /api/v1/exercises                           (user adds an exercise)
                        POST /api/v1/media/upload (folder=exercises)     (optionally upload image)
                        PUT  /api/v1/exercises/{id} { imageUrl: "..." }  (attach uploaded image)

Edit exercise         → PUT /api/v1/exercises/{id}                       (only creator)
Delete exercise       → DELETE /api/v1/exercises/{id}                    (only creator)

View history          → GET /api/v1/exercises/{id}/history               (strength progress chart)
View PBs              → GET /api/v1/exercises/{id}/pbs                   (records screen)
```

---

## GET /api/v1/exercises/categories

**List all muscle groups.**

Used to populate category filters, body-diagram UIs, and the filter panel on the exercise library screen. Results are not paginated — the full list is returned.

**No parameters.**

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "displayName": "Latissimus Dorsi",
      "shortName": "Lats",
      "description": "Large flat muscles of the back",
      "bodyRegion": "UPPER_BODY",
      "imageUrl": null
    },
    {
      "id": 2,
      "displayName": "Quadriceps",
      "shortName": "Quads",
      "description": "Front thigh muscles",
      "bodyRegion": "LOWER_BODY",
      "imageUrl": "https://catalyst.zoho.com/baas/v1/project/.../download"
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `id` | Long | Muscle group ID — use this as `categoryId` filter on `GET /exercises` and `primaryMuscleId` on `POST /exercises`. |
| `displayName` | String | Full anatomical name. |
| `shortName` | String | Short display label. |
| `description` | String | Human-readable description. Empty string if not set. |
| `bodyRegion` | String | `UPPER_BODY` \| `LOWER_BODY` \| `CORE` \| `FULL_BODY` \| `OTHER` |
| `imageUrl` | String \| null | Optional image URL. `null` if no image is set. |

---

## POST /api/v1/exercises/categories

**Add a new muscle group to the library.**

Typically called only during initial setup (or when adding custom muscle groups). Use the seed endpoint for the default set.

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | String | Yes | Full name e.g. `"Neck"` |
| `shortName` | String | Yes | Short label e.g. `"Neck"` |
| `description` | String | No | Defaults to `""`. |
| `bodyRegion` | String | Yes | `UPPER_BODY` \| `LOWER_BODY` \| `CORE` \| `FULL_BODY` \| `OTHER` |
| `imageUrl` | String | No | Optional URL for a body-diagram image. |

```json
{
  "displayName": "Neck",
  "shortName": "Neck",
  "description": "Cervical spine muscles",
  "bodyRegion": "UPPER_BODY"
}
```

**Response — 201 Created** — the full muscle group object (same shape as a single item in the list response above).

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `displayName`, `shortName`, or `bodyRegion` is missing or blank |

---

## GET /api/v1/exercises/equipment

**List all equipment types.**

Used to populate equipment filters on the exercise library screen. Full list returned, not paginated.

**No parameters.**

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "displayName": "Barbell",
      "description": "Standard Olympic barbell",
      "category": "FREE_WEIGHTS",
      "imageUrl": null
    },
    {
      "id": 2,
      "displayName": "Pull-up Bar",
      "description": "Wall-mounted or door-frame bar",
      "category": "BODYWEIGHT",
      "imageUrl": null
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `id` | Long | Equipment ID — use this as `equipmentId` filter on `GET /exercises` and `equipmentId` on `POST /exercises`. |
| `displayName` | String | Human-readable name. |
| `description` | String | Description. Empty string if not set. |
| `category` | String | `FREE_WEIGHTS` \| `MACHINES` \| `BODYWEIGHT` \| `CARDIO_MACHINES` \| `OTHER` |
| `imageUrl` | String \| null | Optional image URL. `null` if not set. |

---

## POST /api/v1/exercises/equipment

**Add a new equipment type to the library.**

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | String | Yes | e.g. `"Gymnastic Rings"` |
| `description` | String | No | Defaults to `""`. |
| `category` | String | Yes | `FREE_WEIGHTS` \| `MACHINES` \| `BODYWEIGHT` \| `CARDIO_MACHINES` \| `OTHER` |
| `imageUrl` | String | No | Optional URL. |

**Response — 201 Created** — the full equipment object (same shape as a single item in the list response above).

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `displayName` or `category` is missing or blank |

---

## GET /api/v1/exercises

**Browse the community exercise library.**

Returns a summary view of exercises (no `instructions`, `tips`, or full `secondaryMuscles`). Use `GET /api/v1/exercises/{id}` to load full detail for a specific exercise. Results are paginated and sorted by name ascending.

**Query Parameters**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `categoryId` | Long | No | — | Filter by muscle group ID. Get IDs from `GET /exercises/categories`. |
| `equipmentId` | Long | No | — | Filter by equipment ID. Get IDs from `GET /exercises/equipment`. |
| `difficulty` | String | No | — | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` |
| `search` | String | No | — | Substring match on exercise name (case-insensitive). |
| `mine` | Boolean | No | `false` | `true` to show only exercises created by the calling user. |
| `page` | Int | No | `1` | Page number. |
| `pageSize` | Int | No | `20` | Items per page. Max `50`. |

**Examples**
```
GET /api/v1/exercises
GET /api/v1/exercises?categoryId=3&difficulty=INTERMEDIATE&page=1&pageSize=20
GET /api/v1/exercises?search=pull&mine=true
```

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "id": 789,
      "name": "Pull Up",
      "primaryMuscleId": 3,
      "equipmentId": 10,
      "difficulty": "INTERMEDIATE",
      "createdBy": "userId123"
    }
  ],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 42
  }
}
```

> This is a **summary view**. It does not include `description`, `instructions`, `tips`, `secondaryMuscles`, `imageUrl`, `videoUrl`, or `tags`. Call `GET /api/v1/exercises/{id}` to get the full record.

---

## GET /api/v1/exercises/{id}

**Get full detail for a single exercise.**

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Exercise ID from `GET /api/v1/exercises`. |

**Response — 200 OK**

```json
{
  "success": true,
  "data": {
    "id": 789,
    "name": "Pull Up",
    "description": "A foundational upper-body pulling movement.",
    "primaryMuscleId": 3,
    "secondaryMuscles": ["Rhomboids", "Trapezius", "Biceps"],
    "equipmentId": 10,
    "difficulty": "INTERMEDIATE",
    "instructions": [
      "Grip the bar slightly wider than shoulder width, palms facing away.",
      "Hang at full arm extension with shoulders engaged.",
      "Pull yourself up until your chin clears the bar.",
      "Lower under control to the starting position."
    ],
    "tips": [
      "Keep your core tight throughout the movement.",
      "Avoid swinging or kipping unless training for that specifically."
    ],
    "imageUrl": null,
    "videoUrl": null,
    "tags": ["compound", "pull", "bodyweight"],
    "createdBy": "userId123",
    "createdAt": "2026-02-28T10:00:00",
    "updatedAt": "2026-02-28T10:00:00"
  }
}
```

| Field | Type | Description |
|---|---|---|
| `id` | Long | Exercise ID. |
| `name` | String | Exercise name. |
| `description` | String | Detailed description. Empty string if not set. |
| `primaryMuscleId` | Long | ID of the primary muscle group targeted. |
| `secondaryMuscles` | String[] | Free-text secondary muscle names (not IDs). Empty array if not set. |
| `equipmentId` | Long | ID of the equipment required. |
| `difficulty` | String | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` |
| `instructions` | String[] | Step-by-step performance instructions. |
| `tips` | String[] | Coaching tips. Empty array if not set. |
| `imageUrl` | String \| null | Demo image URL. `null` if not set. |
| `videoUrl` | String \| null | Demo video URL. `null` if not set. |
| `tags` | String[] | Free-text tags. Empty array if not set. |
| `createdBy` | String | User ID of the creator. |
| `createdAt` | String | ISO-8601 creation timestamp. |
| `updatedAt` | String | ISO-8601 last-updated timestamp. |

**Errors**

| Status | Code | When |
|---|---|---|
| 404 | `NOT_FOUND` | No exercise with that ID exists. |

---

## POST /api/v1/exercises

**Create a new exercise.**

Any authenticated user can contribute to the library. The calling user becomes the creator.

**Request Body**

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `name` | String | Yes | Max 100 chars | Exercise name. |
| `description` | String | No | — | Defaults to `""`. |
| `primaryMuscleId` | Long | Yes | Must exist in MuscleGroups table | Primary muscle targeted. Get IDs from `GET /exercises/categories`. |
| `secondaryMuscles` | String[] | No | Free-text names, not IDs | e.g. `["Rhomboids", "Biceps"]`. Defaults to `[]`. |
| `equipmentId` | Long | Yes | Must exist in Equipment table | Required equipment. Get IDs from `GET /exercises/equipment`. |
| `difficulty` | String | No | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` | Defaults to `BEGINNER`. |
| `instructions` | String[] | Yes | At least one step required | Step-by-step instructions. |
| `tips` | String[] | No | — | Defaults to `[]`. |
| `imageUrl` | String | No | — | Attach a media URL returned by `POST /api/v1/media/upload`. |
| `videoUrl` | String | No | — | Attach a media URL returned by `POST /api/v1/media/upload`. |
| `tags` | String[] | No | — | Defaults to `[]`. |

```json
{
  "name": "Pull Up",
  "description": "A foundational upper-body pulling movement.",
  "primaryMuscleId": 3,
  "secondaryMuscles": ["Rhomboids", "Trapezius", "Biceps"],
  "equipmentId": 10,
  "difficulty": "INTERMEDIATE",
  "instructions": [
    "Grip the bar slightly wider than shoulder width, palms facing away.",
    "Hang at full arm extension with shoulders engaged.",
    "Pull yourself up until your chin clears the bar.",
    "Lower under control to the starting position."
  ],
  "tips": ["Keep your core tight throughout."],
  "tags": ["compound", "pull", "bodyweight"]
}
```

**Response — 201 Created** — full `ExerciseResponse` (same shape as `GET /api/v1/exercises/{id}`).

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `name`, `primaryMuscleId`, `equipmentId`, or `instructions` is missing or empty |
| 404 | `NOT_FOUND` | `primaryMuscleId` or `equipmentId` does not reference a valid row in the lookup tables |

---

## PUT /api/v1/exercises/{id}

**Update an exercise.**

Only the original creator can edit. All fields are optional — omitted fields retain their current values. This is also the endpoint used to **attach media URLs** after uploading files via `POST /api/v1/media/upload`.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Exercise ID. |

**Request Body** — all fields optional

| Field | Type | Description |
|---|---|---|
| `name` | String | New exercise name. |
| `description` | String | New description. |
| `primaryMuscleId` | Long | New primary muscle group. Must exist in MuscleGroups table. |
| `secondaryMuscles` | String[] | Replaces the entire secondary muscles array. |
| `equipmentId` | Long | New equipment. Must exist in Equipment table. |
| `difficulty` | String | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` |
| `instructions` | String[] | Replaces the entire instructions array. |
| `tips` | String[] | Replaces the entire tips array. |
| `imageUrl` | String | Set or update the image URL. |
| `videoUrl` | String | Set or update the video URL. |
| `tags` | String[] | Replaces the entire tags array. |

**Example — update difficulty and tips only:**
```json
{
  "difficulty": "ADVANCED",
  "tips": ["Keep your core tight.", "Focus on full range of motion."]
}
```

**Example — attach an uploaded image:**
```json
{
  "imageUrl": "https://catalyst.zoho.com/baas/v1/project/12345/filestore/67/folder/9001/download"
}
```

**Response — 200 OK** — full `ExerciseResponse` (same shape as `GET /api/v1/exercises/{id}`).

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | The calling user is not the creator of this exercise. |
| 404 | `NOT_FOUND` | No exercise with that ID exists. |
| 404 | `NOT_FOUND` | `primaryMuscleId` or `equipmentId` does not exist in the lookup tables (if provided). |

---

## DELETE /api/v1/exercises/{id}

**Delete an exercise.**

Only the original creator can delete. Returns no body on success.

> **Warning:** Deleting an exercise does not remove it from existing routine items or historical workout sets. Those rows retain the `exerciseId` and `exerciseName` at the time they were logged.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Exercise ID. |

**Response — 204 No Content** (empty body)

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | The calling user is not the creator. |
| 404 | `NOT_FOUND` | No exercise with that ID exists. |

---

## Admin — POST /api/v1/admin/seed

**Seed the MuscleGroups and Equipment tables with default data.**

Call this **once** after deploying to a fresh environment. It is idempotent — any table that already has rows is skipped. Do **not** call this repeatedly; it only inserts when a table is empty.

**No request body.**

**Response — 200 OK**

```json
{
  "success": true,
  "data": {
    "muscleGroupsInserted": 16,
    "equipmentInserted": 14,
    "message": "Seed complete"
  }
}
```

| Field | Description |
|---|---|
| `muscleGroupsInserted` | Number of muscle group rows inserted. `0` if the table was already populated. |
| `equipmentInserted` | Number of equipment rows inserted. `0` if the table was already populated. |

---

## Common patterns

### Attaching media to an exercise

```
1. POST /api/v1/media/upload          (multipart, folder=exercises)
   → returns { url, fileId, ... }

2. PUT  /api/v1/exercises/{id}
   { "imageUrl": "<url from step 1>" }
   — or —
   { "videoUrl": "<url from step 1>" }
```

The upload and the entity update are two separate calls. The upload endpoint never modifies any exercise record.

### Looking up foreign keys before creating an exercise

Always call `GET /api/v1/exercises/categories` and `GET /api/v1/exercises/equipment` to get the valid IDs before presenting the exercise creation form. Cache the results — these lists change rarely.

### Secondary muscles are names, not IDs

`secondaryMuscles` is a free-text string array. Do **not** look up IDs — pass the human-readable name directly:
```json
{ "secondaryMuscles": ["Rhomboids", "Trapezius", "Biceps Brachii"] }
```
