# Hydration API

All endpoints are prefixed with `/api/v1/water` and require authentication.

---

## POST /api/v1/water

Log a water intake entry. Creates one entry per call; daily totals are computed from entries.

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `amountMl` | Int | Yes | Volume in millilitres. Must be ≥ 1. |
| `logDateTime` | String | No | ISO-8601 datetime (e.g. `"2025-01-15T08:30:00"`). Defaults to server time if omitted. |
| `notes` | String | No | Free text. Defaults to `""`. |

```json
{
  "amountMl": 250,
  "logDateTime": "2025-01-15T08:30:00",
  "notes": "Morning glass"
}
```

**Response — 201 Created**
```json
{
  "success": true,
  "data": {
    "id": 1234567890123456,
    "logDateTime": "2025-01-15T08:30:00",
    "amountMl": 250,
    "notes": "Morning glass"
  }
}
```

---

## GET /api/v1/water/today

Shortcut for today's daily summary.

**Response — 200 OK**
```json
{
  "success": true,
  "data": {
    "date": "2025-01-15",
    "totalMl": 1500,
    "goalMl": 2500,
    "progressPercent": 60,
    "entries": [
      {
        "id": 1234567890123456,
        "logDateTime": "2025-01-15T08:30:00",
        "amountMl": 250,
        "notes": "Morning glass"
      }
    ]
  }
}
```

---

## GET /api/v1/water/daily

Get the summary and all entries for a specific date.

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `date` | String | Yes | Date in `YYYY-MM-DD` format. |

**Example**
```
GET /api/v1/water/daily?date=2025-01-15
```

**Response — 200 OK**

Same shape as `/today`.

---

## GET /api/v1/water/history

Get daily totals for a date range. Useful for trend charts. Returns one summary per day that has at least one entry; days with no entries are omitted. Results are sorted newest-first.

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `startDate` | String | Yes | Start of range in `YYYY-MM-DD` format (inclusive). |
| `endDate` | String | Yes | End of range in `YYYY-MM-DD` format (inclusive). |

**Example**
```
GET /api/v1/water/history?startDate=2025-01-01&endDate=2025-01-31
```

**Response — 200 OK**
```json
{
  "success": true,
  "data": [
    {
      "date": "2025-01-15",
      "totalMl": 2600,
      "goalMl": 2500,
      "progressPercent": 100
    },
    {
      "date": "2025-01-14",
      "totalMl": 1800,
      "goalMl": 2500,
      "progressPercent": 72
    }
  ]
}
```

---

## PUT /api/v1/water/{id}

Update an existing entry. Only `amountMl` and `notes` can be changed. At least one field should be provided; omitted fields retain their current value.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The entry ID from a previous log or list response. |

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `amountMl` | Int | No | New volume in millilitres. Must be ≥ 1 if provided. |
| `notes` | String | No | New notes text. |

```json
{
  "amountMl": 330,
  "notes": "Updated to full can"
}
```

**Response — 200 OK**
```json
{
  "success": true,
  "data": {
    "id": 1234567890123456,
    "logDateTime": "2025-01-15T08:30:00",
    "amountMl": 330,
    "notes": "Updated to full can"
  }
}
```

**Error — 404** if the entry ID does not exist.
**Error — 403** if the entry belongs to a different user.

---

## DELETE /api/v1/water/{id}

Remove a logged entry.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | The entry ID to delete. |

**Example**
```
DELETE /api/v1/water/1234567890123456
```

**Response — 204 No Content** (empty body on success)

**Error — 404** if the entry ID does not exist.
**Error — 403** if the entry belongs to a different user.
