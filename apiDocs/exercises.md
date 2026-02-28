# Exercises API

All endpoints are prefixed with `/api/v1/exercises` and require authentication unless noted.

All exercises are **public** — any authenticated user can read any exercise. Only the creator can update or delete their own exercise.

---

## GET /api/v1/exercises/categories

List all muscle groups. Used to populate category filters and body-diagram UIs on the client.

**Response — 200 OK**
```json
{
  "success": true,
  "data": [
    {
      "id": 123,
      "slug": "LATS",
      "displayName": "Latissimus Dorsi",
      "shortName": "Lats",
      "description": "Large flat muscles of the back",
      "bodyRegion": "UPPER_BODY",
      "imageUrl": null
    }
  ]
}
```

---

## POST /api/v1/exercises/categories

Add a new muscle group to the library.

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `slug` | String | Yes | Uppercase identifier e.g. `NECK`. Must be unique. |
| `displayName` | String | Yes | e.g. `Neck` |
| `shortName` | String | Yes | Short label e.g. `Neck` |
| `description` | String | No | Human-readable description. Defaults to `""`. |
| `bodyRegion` | String | Yes | `UPPER_BODY` \| `LOWER_BODY` \| `CORE` \| `FULL_BODY` \| `OTHER` |
| `imageUrl` | String | No | Optional URL. |

```json
{
  "slug": "NECK",
  "displayName": "Neck",
  "shortName": "Neck",
  "description": "Cervical spine muscles",
  "bodyRegion": "UPPER_BODY"
}
```

**Response — 201 Created** — same shape as a single category object above.

**Error — 400** if slug already exists.

---

## GET /api/v1/exercises/equipment

List all equipment types.

**Response — 200 OK**
```json
{
  "success": true,
  "data": [
    {
      "id": 456,
      "slug": "BARBELL",
      "displayName": "Barbell",
      "description": "Standard Olympic barbell",
      "category": "FREE_WEIGHTS",
      "imageUrl": null
    }
  ]
}
```

---

## POST /api/v1/exercises/equipment

Add a new equipment type to the library.

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `slug` | String | Yes | Uppercase identifier e.g. `RINGS`. Must be unique. |
| `displayName` | String | Yes | e.g. `Gymnastic Rings` |
| `description` | String | No | Defaults to `""`. |
| `category` | String | Yes | `FREE_WEIGHTS` \| `MACHINES` \| `BODYWEIGHT` \| `CARDIO_MACHINES` \| `OTHER` |
| `imageUrl` | String | No | Optional URL. |

**Response — 201 Created** — same shape as a single equipment object above.

**Error — 400** if slug already exists.

---

## GET /api/v1/exercises

Browse the community exercise library. Results are paginated and sorted by name ascending.

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `category` | String | No | Filter by muscle group slug e.g. `LATS`. |
| `equipment` | String | No | Filter by equipment slug e.g. `BARBELL`. |
| `difficulty` | String | No | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` |
| `search` | String | No | Substring match on exercise name (case-insensitive). |
| `mine` | Boolean | No | `true` to show only the calling user's exercises. Default `false`. |
| `page` | Int | No | Page number. Default `1`. |
| `pageSize` | Int | No | Items per page. Default `20`, max `50`. |

**Example**
```
GET /api/v1/exercises?category=LATS&difficulty=INTERMEDIATE&page=1&pageSize=20
```

**Response — 200 OK**
```json
{
  "success": true,
  "data": [
    {
      "id": 789,
      "name": "Pull Up",
      "primaryMuscleSlug": "LATS",
      "equipmentSlug": "PULLUP_BAR",
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

---

## GET /api/v1/exercises/{id}

Get full detail for a single exercise.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Exercise ROWID. |

**Response — 200 OK**
```json
{
  "success": true,
  "data": {
    "id": 789,
    "name": "Pull Up",
    "description": "A foundational upper-body pulling movement.",
    "primaryMuscleSlug": "LATS",
    "secondaryMuscles": ["Rhomboids", "Trapezius", "Biceps"],
    "equipmentSlug": "PULLUP_BAR",
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

**Error — 404** if the exercise does not exist.

---

## POST /api/v1/exercises

Create a new exercise. Any authenticated user can contribute to the library.

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | String | Yes | Max 100 chars. |
| `description` | String | No | Defaults to `""`. |
| `primaryMuscleSlug` | String | Yes | Must match a slug in the MuscleGroups table. |
| `secondaryMuscles` | String[] | No | Named muscles e.g. `["Rhomboids", "Biceps"]`. Defaults to `[]`. |
| `equipmentSlug` | String | Yes | Must match a slug in the Equipment table. |
| `difficulty` | String | No | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED`. Default `BEGINNER`. |
| `instructions` | String[] | Yes | At least one step required. |
| `tips` | String[] | No | Defaults to `[]`. |
| `imageUrl` | String | No | Optional URL. |
| `videoUrl` | String | No | Optional URL. |
| `tags` | String[] | No | Defaults to `[]`. |

```json
{
  "name": "Pull Up",
  "description": "A foundational upper-body pulling movement.",
  "primaryMuscleSlug": "LATS",
  "secondaryMuscles": ["Rhomboids", "Trapezius", "Biceps"],
  "equipmentSlug": "PULLUP_BAR",
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

**Response — 201 Created** — full `ExerciseResponse` (same shape as GET /{id}).

**Error — 404** if `primaryMuscleSlug` or `equipmentSlug` does not exist in the lookup tables.

---

## PUT /api/v1/exercises/{id}

Update an exercise. Only the original creator can edit. All fields are optional — omitted fields retain their current values.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Exercise ROWID. |

**Request Body**

All fields optional. Same field names as POST; provide only the fields you want to change.

```json
{
  "difficulty": "ADVANCED",
  "tips": ["Keep your core tight.", "Focus on full range of motion."]
}
```

**Response — 200 OK** — full `ExerciseResponse`.

**Error — 404** if the exercise does not exist.
**Error — 403** if the caller is not the creator.

---

## DELETE /api/v1/exercises/{id}

Delete an exercise. Only the original creator can delete.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Exercise ROWID. |

**Response — 204 No Content** (empty body on success).

**Error — 404** if the exercise does not exist.
**Error — 403** if the caller is not the creator.

---

## Admin — POST /api/v1/admin/seed

One-time endpoint to populate the MuscleGroups and Equipment lookup tables with default data.

**No request body.**

Call once after deploying to a fresh environment. Idempotent — skips any table that already has rows.

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
