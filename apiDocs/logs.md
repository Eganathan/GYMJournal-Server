# Gym Logs (Incidents) API

**Base path:** `/api/v1/logs`
**Auth required:** Yes — all endpoints require authentication.
**Data visibility:** Private — each user can only read and modify their own entries.

---

## Key concepts

- A **gym log** is a timestamped note attached to a gym day (date). It captures incidents, health events, or general observations for that session.
- Three **types** are supported:
  - `INJURY` — physical injury (e.g. "pulled hamstring on leg day")
  - `MEDICATION` — medication or supplement event (e.g. "took ibuprofen before session")
  - `NOTE` — general free-text note for the day
- **Severity** is optional and most useful for `INJURY` / `MEDICATION`: `LOW` | `MEDIUM` | `HIGH`. Leave empty for `NOTE` types or when severity is not relevant.
- Multiple entries can exist for the same date — e.g. one `INJURY` and one `NOTE` on the same day.

---

## Endpoint Summary

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/logs` | Create a new log entry |
| GET | `/api/v1/logs?date=` | Get all entries for a specific date |
| GET | `/api/v1/logs/recent` | Get the 50 most recent entries |
| PUT | `/api/v1/logs/{id}` | Update title, description, or severity |
| DELETE | `/api/v1/logs/{id}` | Delete an entry |

---

## Typical client flow

```
View today's log      → GET /api/v1/logs?date=2026-03-09
Add an injury note    → POST /api/v1/logs  { type: "INJURY", ... }
View recent history   → GET /api/v1/logs/recent
Edit a note           → PUT /api/v1/logs/{id}
Remove an entry       → DELETE /api/v1/logs/{id}
```

---

## POST /api/v1/logs

**Create a new gym log entry.**

**Request Body**

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `logDate` | String | Yes | `YYYY-MM-DD` | The gym day this entry belongs to. |
| `type` | String | Yes | `INJURY`, `MEDICATION`, or `NOTE` | Category of the log entry. Case-insensitive. |
| `title` | String | Yes | Non-empty | Short summary (e.g. "Left knee pain during squats"). |
| `description` | String | No | — | Full details. Defaults to `""`. |
| `severity` | String | No | `LOW`, `MEDIUM`, `HIGH`, or `""` | Severity level. Case-insensitive. Leave empty if not applicable. |

```json
{
  "logDate": "2026-03-09",
  "type": "INJURY",
  "title": "Left knee pain during squats",
  "description": "Sharp pain on the medial side, stopped the set early. Will rest tomorrow.",
  "severity": "MEDIUM"
}
```

**Response — 201 Created**

```json
{
  "success": true,
  "data": {
    "id": "11585000000456789",
    "logDate": "2026-03-09",
    "type": "INJURY",
    "title": "Left knee pain during squats",
    "description": "Sharp pain on the medial side, stopped the set early. Will rest tomorrow.",
    "severity": "MEDIUM",
    "createdAt": "2026-03-09T14:32:00"
  }
}
```

| Field | Type | Description |
|---|---|---|
| `id` | String | Entry ID — store this to update or delete. |
| `logDate` | String | The gym day in `YYYY-MM-DD`. |
| `type` | String | `INJURY`, `MEDICATION`, or `NOTE`. |
| `title` | String | Short summary. |
| `description` | String | Full details (empty string if not provided). |
| `severity` | String | `LOW`, `MEDIUM`, `HIGH`, or `""`. |
| `createdAt` | String | ISO-8601 timestamp of when the entry was created. |

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `logDate`, `type`, or `title` is missing/blank |
| 400 | `INVALID_REQUEST` | `type` is not `INJURY`, `MEDICATION`, or `NOTE` |
| 400 | `INVALID_REQUEST` | `severity` is not `LOW`, `MEDIUM`, `HIGH`, or `""` |

---

## GET /api/v1/logs

**Get all log entries for a specific date.**

Returns all entries for the given date, sorted by creation time ascending.

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `date` | String | Yes | Target date in `YYYY-MM-DD`. |

**Example**
```
GET /api/v1/logs?date=2026-03-09
```

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "id": "11585000000456789",
      "logDate": "2026-03-09",
      "type": "INJURY",
      "title": "Left knee pain during squats",
      "description": "Sharp pain on the medial side.",
      "severity": "MEDIUM",
      "createdAt": "2026-03-09T14:32:00"
    },
    {
      "id": "11585000000456790",
      "logDate": "2026-03-09",
      "type": "MEDICATION",
      "title": "Ibuprofen 400mg",
      "description": "Taken post-session for inflammation.",
      "severity": "LOW",
      "createdAt": "2026-03-09T16:00:00"
    }
  ]
}
```

Returns `[]` if no entries exist for the given date.

---

## GET /api/v1/logs/recent

**Get the 50 most recent log entries across all dates.**

Useful for a health/incident timeline screen. Results are sorted newest date first.

**No parameters.**

**Response — 200 OK** — array of log entry objects (same shape as above).

---

## PUT /api/v1/logs/{id}

**Update an existing log entry.**

Only `title`, `description`, and `severity` can be changed — `logDate` and `type` are fixed at creation. Omitted fields retain their current value.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The entry ID. |

**Request Body** — all fields optional

| Field | Type | Constraints | Description |
|---|---|---|---|
| `title` | String | Non-empty if provided | New short summary. |
| `description` | String | — | New full details (replaces existing). |
| `severity` | String | `LOW`, `MEDIUM`, `HIGH`, or `""` | New severity level. |

```json
{
  "severity": "HIGH",
  "description": "Worsened overnight — going to see a physio."
}
```

**Response — 200 OK** — updated log entry object.

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | `severity` value is invalid |
| 403 | `FORBIDDEN` | Entry belongs to a different user |
| 404 | `NOT_FOUND` | Entry ID does not exist |

---

## DELETE /api/v1/logs/{id}

**Delete a log entry.**

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The entry ID to remove. |

**Response — 204 No Content** (empty body)

**Errors**

| Status | Code | When |
|---|---|---|
| 403 | `FORBIDDEN` | Entry belongs to a different user |
| 404 | `NOT_FOUND` | Entry ID does not exist |

---

## DataStore table setup

Create a `GymLogs` table in the Catalyst Console with these columns:

| Column | Type | Notes |
|---|---|---|
| `USER_ID` | BigInt | Owner's Catalyst user ID |
| `logDate` | Var Char | `YYYY-MM-DD` — the gym day |
| `type` | Var Char | `INJURY`, `MEDICATION`, or `NOTE` |
| `title` | Var Char | Short summary |
| `description` | Var Char | Full details |
| `severity` | Var Char | `LOW`, `MEDIUM`, `HIGH`, or empty |
