# Hydration (Water Intake) API

**Base path:** `/api/v1/water`
**Auth required:** Yes — all endpoints require authentication.
**Data visibility:** Private — each user can only read and modify their own entries.

---

## Key concepts

- Each call to `POST /api/v1/water` creates one **entry** (e.g. "drank a 250 mL glass at 8:30 AM").
- **Daily totals** are computed on-the-fly from entries — there is no separate daily record.
- The **daily goal** is **2500 mL**, hardcoded server-side. All `goalMl` fields in responses will always return `2500`.
- `progressPercent` is clamped to `100` — it never exceeds 100 even when the user drinks more than the goal.
- Entries are private — only the creator can read, update, or delete them.

---

## Endpoint Summary

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/water` | Log a new water intake entry |
| GET | `/api/v1/water/today` | Today's summary (total, goal, all entries) |
| GET | `/api/v1/water/daily?date=` | Any day's summary |
| GET | `/api/v1/water/history?startDate=&endDate=` | Daily totals across a date range |
| PUT | `/api/v1/water/{id}` | Update an existing entry |
| DELETE | `/api/v1/water/{id}` | Delete an entry |

---

## Typical client flow

```
App opens            → GET /api/v1/water/today              (populate dashboard — total, goal ring)
User taps "+" button → POST /api/v1/water                   (log a new drink)
                       GET /api/v1/water/today              (refresh dashboard after logging)
Open history screen  → GET /api/v1/water/history?startDate=&endDate=  (trend chart)
User edits an entry  → PUT /api/v1/water/{id}
User deletes entry   → DELETE /api/v1/water/{id}
                       GET /api/v1/water/today              (refresh after change)
```

---

## POST /api/v1/water

**Log a new water intake entry.**

Creates a single timestamped entry. Multiple calls per day accumulate — the daily total is computed from all entries for that day.

**Request Body**

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `amountMl` | Int | Yes | ≥ 1 | Volume consumed in millilitres. |
| `logDateTime` | String | No | ISO-8601, e.g. `"2026-03-01T08:30:00"` | When the water was consumed. Defaults to current server time if omitted. Use this to backdate (e.g. logging yesterday's intake). |
| `notes` | String | No | — | Optional free-text note. Defaults to `""`. |

```json
{
  "amountMl": 500,
  "logDateTime": "2026-03-01T08:30:00",
  "notes": "Morning glass"
}
```

**Response — 201 Created**

```json
{
  "success": true,
  "data": {
    "id": 11585000000123456,
    "logDateTime": "2026-03-01T08:30:00",
    "amountMl": 500,
    "notes": "Morning glass"
  }
}
```

| Field | Type | Description |
|---|---|---|
| `id` | Long | Entry ID — store this if you need to update or delete this entry. |
| `logDateTime` | String | ISO-8601 string (T separator). |
| `amountMl` | Int | Volume logged. |
| `notes` | String | Notes text (empty string if not provided). |

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `amountMl` is missing or less than 1 |
| 400 | `INVALID_REQUEST` | `logDateTime` is not a valid ISO-8601 datetime |

---

## GET /api/v1/water/today

**Get today's hydration summary and all entries.**

Returns the total intake for today (UTC server date), the daily goal, progress percentage, and the full list of entries ordered by `logDateTime` ascending.

**No parameters.**

**Response — 200 OK**

```json
{
  "success": true,
  "data": {
    "date": "2026-03-01",
    "totalMl": 1750,
    "goalMl": 2500,
    "progressPercent": 70,
    "entries": [
      {
        "id": 11585000000123456,
        "logDateTime": "2026-03-01T08:30:00",
        "amountMl": 500,
        "notes": "Morning glass"
      },
      {
        "id": 11585000000123789,
        "logDateTime": "2026-03-01T12:00:00",
        "amountMl": 1250,
        "notes": ""
      }
    ]
  }
}
```

| Field | Type | Description |
|---|---|---|
| `date` | String | Today's date in `YYYY-MM-DD`. |
| `totalMl` | Int | Sum of all `amountMl` entries for today. |
| `goalMl` | Int | Always `2500` (hardcoded daily goal). |
| `progressPercent` | Int | `min(round(totalMl / goalMl × 100), 100)` — capped at 100. |
| `entries` | Array | All entries for today, ordered by `logDateTime` ascending. Empty array `[]` if nothing logged. |

**Returns empty entries with totalMl 0** if no intake has been logged today.

---

## GET /api/v1/water/daily

**Get the summary and entries for a specific date.**

Same response shape as `/today` but for any date you specify. Use this to display historical day views.

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `date` | String | Yes | Target date in `YYYY-MM-DD`. |

**Example**
```
GET /api/v1/water/daily?date=2026-02-28
```

**Response — 200 OK** — identical shape to `/today`, with `date` set to the requested date.

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | `date` is missing or not in `YYYY-MM-DD` format |

---

## GET /api/v1/water/history

**Get daily totals across a date range.**

Returns one summary per day that has at least one entry — days with no intake are omitted entirely. Useful for trend charts. Results are sorted newest-first.

> Note: Each item in the history response does **not** include the entries list — only the totals. Fetch `/daily?date=` for the full entry breakdown of a specific day.

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `startDate` | String | Yes | Start of range in `YYYY-MM-DD` (inclusive). |
| `endDate` | String | Yes | End of range in `YYYY-MM-DD` (inclusive). |

**Example**
```
GET /api/v1/water/history?startDate=2026-02-01&endDate=2026-02-28
```

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "date": "2026-02-28",
      "totalMl": 2600,
      "goalMl": 2500,
      "progressPercent": 100
    },
    {
      "date": "2026-02-27",
      "totalMl": 1800,
      "goalMl": 2500,
      "progressPercent": 72
    }
  ]
}
```

Returns an empty array `[]` if no entries exist in the given range.

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | `startDate` or `endDate` is missing or not in `YYYY-MM-DD` format |

---

## PUT /api/v1/water/{id}

**Update an existing entry.**

Only `amountMl` and `notes` can be changed — the timestamp (`logDateTime`) is fixed at creation time and cannot be updated. Omitted fields retain their current value.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The entry ID from a previous log or list response. |

**Request Body** — all fields optional

| Field | Type | Constraints | Description |
|---|---|---|---|
| `amountMl` | Int | ≥ 1 if provided | New volume in millilitres. |
| `notes` | String | — | New notes text (replaces existing notes entirely). |

```json
{
  "amountMl": 330,
  "notes": "Updated — full water bottle"
}
```

**Response — 200 OK**

```json
{
  "success": true,
  "data": {
    "id": 11585000000123456,
    "logDateTime": "2026-03-01T08:30:00",
    "amountMl": 330,
    "notes": "Updated — full water bottle"
  }
}
```

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `amountMl` is provided but less than 1 |
| 403 | `FORBIDDEN` | Entry belongs to a different user |
| 404 | `NOT_FOUND` | Entry ID does not exist |

---

## DELETE /api/v1/water/{id}

**Remove a logged water intake entry.**

Deleting an entry reduces the daily total for the entry's date. The daily goal and progressPercent for that day will decrease accordingly.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The entry ID to remove. |

**Example**
```
DELETE /api/v1/water/11585000000123456
```

**Response — 204 No Content** (empty body)

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | Entry belongs to a different user |
| 404 | `NOT_FOUND` | Entry ID does not exist |

---

## Common patterns

### Getting an entry ID for PUT/DELETE

The `id` is returned in every entry object — from the `POST` response or from the `entries` array in `GET /today` or `GET /daily`. Never guess or construct an ID; always read it from a prior response.

### Displaying progress

Use `progressPercent` directly for a progress ring/bar. It is already clamped to 100 — if a user drinks 3000 mL against a 2500 mL goal, `progressPercent` returns `100` (not `120`).

### Handling no-data days

`GET /today` and `GET /daily` always return a valid response with `totalMl: 0` and an empty `entries` array — they never return 404. Render an empty/zero state rather than treating it as an error.
