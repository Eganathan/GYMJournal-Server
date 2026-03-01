# Body Metrics API

Body Metrics lets users log and track physical measurements over time — body weight, body composition (InBody), limb measurements, blood work, and fully custom user-defined metrics.

**Base path:** `/api/v1/metrics`
**Auth required:** Yes — all endpoints require authentication.
**Data visibility:** Private — each user can only read and modify their own metric entries.

---

## Key concepts for client developers

### How logging works

Each measurement is stored as a flat record: `(metricType, value, unit, logDate)`.

The client should collect all the measurements the user fills in for a given date and submit them together in **one batch call** — `POST /api/v1/metrics/entries`. This keeps the log form submit simple and atomic.

### Metric types

A `metricType` is a string key that identifies what is being measured. There are two kinds:

**Built-in types** — predefined keys with canonical units (see the full table below). The client should always send the canonical unit for built-in types.

**Custom types** — user-defined keys prefixed with `custom_`, e.g. `custom_sgpt`. The key is derived automatically on the backend from the label the user typed — the same derivation the frontend uses:
```
metricKey = "custom_" + label.lowercase().replace(/[^a-z0-9]/g, "_")
```
Create custom definitions via `POST /api/v1/metrics/custom` before logging entries of that type.

### Computed metrics

`bmi` and `smiComputed` are **never stored** and **cannot be submitted** via the log endpoint (returns 400). They are derived server-side whenever the snapshot is fetched:
- `bmi = weight(kg) / (height(cm) / 100)²`
- `smiComputed = smm(kg) / (height(cm) / 100)²`

The snapshot response includes them automatically when the source values (`weight`, `height`, `smm`) are available.

### Typical client flow

```
App launch          → GET /api/v1/metrics/snapshot       (populate dashboard cards)
Open history chart  → GET /api/v1/metrics/{type}/history (populate trend graph)
Open log form       → GET /api/v1/metrics/entries?date=  (pre-fill existing values)
User submits form   → POST /api/v1/metrics/entries       (batch save all filled fields)
User edits one row  → PUT /api/v1/metrics/entries/{id}
User deletes one    → DELETE /api/v1/metrics/entries/{id}
Manage custom types → GET/POST/DELETE /api/v1/metrics/custom
```

---

## Built-in metric types reference

The backend accepts any string as `metricType` but these are the known built-in types. Always send the canonical unit shown — the backend stores whatever unit you send, so mismatched units will affect display on all clients.

### Core

| metricType | Label | Canonical unit |
|---|---|---|
| `weight` | Weight | `kg` |
| `height` | Height | `cm` |
| `bodyFat` | Body Fat | `%` |
| `waist` | Waist | `cm` |

### InBody / Body Composition

| metricType | Label | Canonical unit |
|---|---|---|
| `smm` | Skeletal Muscle Mass | `kg` |
| `bmr` | BMR | `kcal` |
| `totalBodyWater` | Total Body Water | `L` |
| `protein` | Protein | `kg` |
| `minerals` | Minerals | `kg` |
| `smi` | SMI (from InBody scan) | `kg/m²` |
| `visceralFat` | Visceral Fat Level | `level` |
| `leanBodyMass` | Lean Body Mass | `kg` |

### Upper Body

| metricType | Label | Canonical unit |
|---|---|---|
| `neck` | Neck | `cm` |
| `chest` | Chest | `cm` |
| `shoulders` | Shoulders | `cm` |

### Arms

| metricType | Label | Canonical unit |
|---|---|---|
| `bicepLeft` | Bicep (Left) | `cm` |
| `bicepRight` | Bicep (Right) | `cm` |
| `forearmLeft` | Forearm (Left) | `cm` |
| `forearmRight` | Forearm (Right) | `cm` |

### Lower Body

| metricType | Label | Canonical unit |
|---|---|---|
| `hips` | Hips | `cm` |
| `thighLeft` | Thigh (Left) | `cm` |
| `thighRight` | Thigh (Right) | `cm` |
| `calfLeft` | Calf (Left) | `cm` |
| `calfRight` | Calf (Right) | `cm` |

### Lipids

| metricType | Label | Canonical unit |
|---|---|---|
| `cholesterolTotal` | Total Cholesterol | `mg/dL` |
| `cholesterolHDL` | HDL | `mg/dL` |
| `cholesterolLDL` | LDL | `mg/dL` |
| `triglycerides` | Triglycerides | `mg/dL` |

### Metabolic / Blood Sugar

| metricType | Label | Canonical unit |
|---|---|---|
| `fastingGlucose` | Fasting Glucose | `mg/dL` |
| `hba1c` | HbA1c | `%` |
| `insulin` | Insulin | `µU/mL` |
| `uricAcid` | Uric Acid | `mg/dL` |
| `creatinine` | Creatinine | `mg/dL` |

### Vitamins & Minerals

| metricType | Label | Canonical unit |
|---|---|---|
| `vitaminD` | Vitamin D | `ng/mL` |
| `vitaminB12` | Vitamin B12 | `pg/mL` |
| `iron` | Iron | `µg/dL` |
| `ferritin` | Ferritin | `ng/mL` |
| `calcium` | Calcium | `mg/dL` |

### Blood Panel

| metricType | Label | Canonical unit |
|---|---|---|
| `hemoglobin` | Hemoglobin | `g/dL` |
| `rbc` | RBC | `M/µL` |
| `wbc` | WBC | `K/µL` |
| `platelets` | Platelets | `K/µL` |
| `tsh` | TSH | `µIU/mL` |
| `testosterone` | Testosterone | `ng/dL` |

### Computed (server-side — do NOT submit these)

| metricType | Label | How it's derived |
|---|---|---|
| `bmi` | BMI | `weight / (height/100)²` |
| `smiComputed` | SMI (computed) | `smm / (height/100)²` |

---

## Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/metrics/entries` | Batch log metric entries |
| GET | `/api/v1/metrics/entries?date=` | All entries for a date |
| PUT | `/api/v1/metrics/entries/{id}` | Update an entry |
| DELETE | `/api/v1/metrics/entries/{id}` | Delete an entry |
| GET | `/api/v1/metrics/{metricType}/history` | History for one metric type |
| GET | `/api/v1/metrics/snapshot` | Latest value per type + computed BMI/SMI |
| GET | `/api/v1/metrics/insights` | Health insights with status + reference ranges |
| GET | `/api/v1/metrics/custom` | List custom metric definitions |
| POST | `/api/v1/metrics/custom` | Create a custom metric definition |
| DELETE | `/api/v1/metrics/custom/{key}` | Delete custom def + all its entries |

---

### POST /api/v1/metrics/entries

**Batch log metric entries.**

Submit all measurements the user filled in for a date in a single request. The frontend should collect every non-empty field from the log form and send them together.

Each entry requires a `metricType`, `value`, `unit`, and `logDate`. If `logDate` is omitted it defaults to today's date (server time). The `notes` field is optional on every entry.

**Do not submit `bmi` or `smiComputed`** — these are computed and will be rejected with 400.

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `entries` | Array | Yes | One or more entry objects (see below). Must not be empty. |

Each entry object:

| Field | Type | Required | Description |
|---|---|---|---|
| `metricType` | String | Yes | A built-in key or a `custom_*` key. |
| `value` | Double | Yes | The measured numeric value. |
| `unit` | String | Yes | The unit string. Use canonical units for built-in types. |
| `logDate` | String | No | `YYYY-MM-DD`. Defaults to today if omitted. |
| `notes` | String | No | Optional free-text note. Defaults to `""`. |

```json
{
  "entries": [
    { "metricType": "weight",    "value": 82.5, "unit": "kg",    "logDate": "2026-02-28" },
    { "metricType": "bodyFat",   "value": 18.2, "unit": "%",     "logDate": "2026-02-28" },
    { "metricType": "waist",     "value": 84.0, "unit": "cm",    "logDate": "2026-02-28" },
    { "metricType": "smm",       "value": 36.1, "unit": "kg",    "logDate": "2026-02-28" },
    { "metricType": "custom_sgpt", "value": 32, "unit": "U/L",   "logDate": "2026-02-28" }
  ]
}
```

**Response — 201 Created**

Returns the saved entries in the same order they were submitted.

```json
{
  "success": true,
  "data": [
    {
      "id": 1001,
      "metricType": "weight",
      "value": 82.5,
      "unit": "kg",
      "logDate": "2026-02-28",
      "notes": "",
      "createdAt": "2026-02-28T10:00:00",
      "updatedAt": "2026-02-28T10:00:00"
    },
    {
      "id": 1002,
      "metricType": "bodyFat",
      "value": 18.2,
      "unit": "%",
      "logDate": "2026-02-28",
      "notes": "",
      "createdAt": "2026-02-28T10:00:00",
      "updatedAt": "2026-02-28T10:00:00"
    }
  ]
}
```

**Errors**

| Status | When |
|---|---|
| 400 `VALIDATION_ERROR` | `entries` is empty or a required entry field is blank |
| 400 `INVALID_REQUEST` | `metricType` is `bmi` or `smiComputed` (computed-only) |
| 400 `INVALID_REQUEST` | `logDate` is not in `YYYY-MM-DD` format |

---

### GET /api/v1/metrics/entries

**Get all entries logged on a specific date.**

Use this to pre-fill the log form when the user navigates to a past date. Returns every metric type the user logged on that date, sorted alphabetically by `metricType`.

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `date` | String | No | `YYYY-MM-DD`. Defaults to today if omitted. |

**Example**
```
GET /api/v1/metrics/entries?date=2026-02-28
```

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "id": 1001,
      "metricType": "bodyFat",
      "value": 18.2,
      "unit": "%",
      "logDate": "2026-02-28",
      "notes": "",
      "createdAt": "2026-02-28T10:00:00",
      "updatedAt": "2026-02-28T10:00:00"
    },
    {
      "id": 1002,
      "metricType": "weight",
      "value": 82.5,
      "unit": "kg",
      "logDate": "2026-02-28",
      "notes": "",
      "createdAt": "2026-02-28T10:00:00",
      "updatedAt": "2026-02-28T10:00:00"
    }
  ]
}
```

Returns an empty array `[]` if no entries exist for that date.

---

### PUT /api/v1/metrics/entries/{id}

**Update a single metric entry.**

All fields are optional — only the fields you include are updated. Useful when the user corrects a value after submission, or adds a note.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Entry ID from a previous log or list response. |

**Request Body** — all fields optional

| Field | Type | Description |
|---|---|---|
| `value` | Double | New measured value. |
| `unit` | String | New unit string. |
| `logDate` | String | New date in `YYYY-MM-DD`. |
| `notes` | String | New notes text. |

```json
{
  "value": 82.1,
  "notes": "After morning workout"
}
```

**Response — 200 OK** — the full updated entry (same shape as a single entry in the log response).

**Errors**

| Status | When |
|---|---|
| 400 `INVALID_REQUEST` | `logDate` provided but not in `YYYY-MM-DD` format |
| 403 `FORBIDDEN` | Entry belongs to a different user |
| 404 `NOT_FOUND` | Entry ID does not exist |

---

### DELETE /api/v1/metrics/entries/{id}

**Delete a single metric entry.**

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Entry ID to remove. |

**Example**
```
DELETE /api/v1/metrics/entries/1001
```

**Response — 204 No Content** (empty body)

**Errors**

| Status | When |
|---|---|
| 403 `FORBIDDEN` | Entry belongs to a different user |
| 404 `NOT_FOUND` | Entry ID does not exist |

---

### GET /api/v1/metrics/{metricType}/history

**Get the history of measurements for a single metric type.**

Returns all entries for one metric type within a date range, sorted oldest-first. This is the data source for trend charts and progress views.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `metricType` | String | Any built-in key (e.g. `weight`) or a custom key (e.g. `custom_sgpt`). |

**Query Parameters**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `startDate` | String | No | 90 days ago | Start of range, `YYYY-MM-DD` (inclusive). |
| `endDate` | String | No | Today | End of range, `YYYY-MM-DD` (inclusive). |

**Examples**
```
GET /api/v1/metrics/weight/history
GET /api/v1/metrics/weight/history?startDate=2026-01-01&endDate=2026-02-28
GET /api/v1/metrics/cholesterolLDL/history?startDate=2025-01-01
GET /api/v1/metrics/custom_sgpt/history
```

**Response — 200 OK**

Sorted by `logDate` ascending (oldest first).

```json
{
  "success": true,
  "data": [
    {
      "id": 900,
      "metricType": "weight",
      "value": 84.0,
      "unit": "kg",
      "logDate": "2026-01-10",
      "notes": "",
      "createdAt": "2026-01-10T09:00:00",
      "updatedAt": "2026-01-10T09:00:00"
    },
    {
      "id": 1001,
      "metricType": "weight",
      "value": 82.5,
      "unit": "kg",
      "logDate": "2026-02-28",
      "notes": "",
      "createdAt": "2026-02-28T10:00:00",
      "updatedAt": "2026-02-28T10:00:00"
    }
  ]
}
```

Returns an empty array `[]` if no data exists for that metric type in the given range.

> **Note:** Results are capped at 300 entries. For dense daily logging over long periods, use a narrower date range.

---

### GET /api/v1/metrics/snapshot

**Get the most recent value for every metric the user has ever logged.**

This is the primary data source for the dashboard. Call this once on app launch (or when the metrics screen loads) and use the result to populate all metric cards, InBody summary panels, and other "current stats" UI.

The response includes:
- The most-recent stored entry for every metric type the user has logged.
- **Computed metrics** (`bmi`, `smiComputed`) derived server-side and appended automatically if source data is available. These don't have an `id` since they're not stored.

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    { "metricType": "weight",         "value": 82.5,  "unit": "kg",    "logDate": "2026-02-28" },
    { "metricType": "height",         "value": 178.0, "unit": "cm",    "logDate": "2026-01-01" },
    { "metricType": "bodyFat",        "value": 18.2,  "unit": "%",     "logDate": "2026-02-28" },
    { "metricType": "smm",            "value": 36.1,  "unit": "kg",    "logDate": "2026-02-28" },
    { "metricType": "cholesterolHDL", "value": 58.0,  "unit": "mg/dL", "logDate": "2026-02-10" },
    { "metricType": "custom_sgpt",    "value": 32.0,  "unit": "U/L",   "logDate": "2026-02-28" },
    { "metricType": "bmi",            "value": 26.0,  "unit": "kg/m²", "logDate": "2026-02-28" },
    { "metricType": "smiComputed",    "value": 11.4,  "unit": "kg/m²", "logDate": "2026-02-28" }
  ]
}
```

**How to use the snapshot on the client:**

```js
// Convert to a lookup map for easy access
const snapshot = {};
data.forEach(item => { snapshot[item.metricType] = item; });

// Then read any metric instantly
const weight    = snapshot['weight']?.value;        // 82.5
const bmi       = snapshot['bmi']?.value;           // 26.0  (computed)
const sgpt      = snapshot['custom_sgpt']?.value;   // 32.0
const bmiDate   = snapshot['bmi']?.logDate;         // "2026-02-28"
```

**Important notes:**
- `bmi` and `smiComputed` are **only present** when both source values are in the snapshot. If the user has never logged `height`, neither computed metric will appear.
- `logDate` on a computed metric is the most recent of the source metrics' dates.
- The snapshot does not include an `id` field on computed items — they cannot be edited or deleted.
- Returns an empty array `[]` if the user has never logged any metrics.

---

### GET /api/v1/metrics/custom

**List the calling user's custom metric definitions.**

Use this to populate the custom metrics management screen and to know which custom types are available for logging.

**Response — 200 OK**

Sorted alphabetically by label.

```json
{
  "success": true,
  "data": [
    { "id": 5, "metricKey": "custom_sgpt",    "label": "SGPT",    "unit": "U/L"       },
    { "id": 6, "metricKey": "custom_vo2_max", "label": "VO2 Max", "unit": "mL/kg/min" }
  ]
}
```

Returns an empty array `[]` if the user has no custom metrics defined.

---

### POST /api/v1/metrics/custom

**Create a custom metric definition.**

Call this when the user defines a new personal metric that isn't in the built-in list. After creation, the `metricKey` can be used as `metricType` in log entries.

The `metricKey` is derived automatically from the label — you do not need to send it. The derivation matches what the frontend uses:
```
metricKey = "custom_" + label.lowercase().replace(/[^a-z0-9]/g, "_")
```
So `"SGPT"` → `custom_sgpt`, `"VO2 Max"` → `custom_vo2_max`.

**Request Body**

| Field | Type | Required | Description |
|---|---|---|---|
| `label` | String | Yes | Human-readable name the user typed, e.g. `SGPT`. |
| `unit` | String | No | Unit string, e.g. `U/L`. May be empty. Defaults to `""`. |

```json
{ "label": "SGPT", "unit": "U/L" }
```

**Response — 201 Created**

```json
{
  "success": true,
  "data": {
    "id": 5,
    "metricKey": "custom_sgpt",
    "label": "SGPT",
    "unit": "U/L"
  }
}
```

**Errors**

| Status | When |
|---|---|
| 400 `VALIDATION_ERROR` | `label` is blank |
| 400 `INVALID_REQUEST` | A custom metric with the same derived key already exists for this user |

---

### DELETE /api/v1/metrics/custom/{key}

**Delete a custom metric definition and all its logged entries.**

This is a **cascade delete** — removing the definition also permanently removes every `BodyMetricEntries` record the user has logged for that `metricType`. Show the user a confirmation before calling this.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `key` | String | The `metricKey`, e.g. `custom_sgpt`. |

**Example**
```
DELETE /api/v1/metrics/custom/custom_sgpt
```

**Response — 204 No Content** (empty body)

**Errors**

| Status | When |
|---|---|
| 404 `NOT_FOUND` | No custom definition with that key exists for this user |

---

### GET /api/v1/metrics/insights

**Get health insights derived from the user's most-recent metric values.**

Runs all registered intelligence engines against the user's snapshot and returns a list of insights — each with a severity status, a plain-English message, and the clinical reference range used.

Call this to power the health warnings panel, the "Your stats at a glance" dashboard section, or any screen that shows the user what their numbers mean.

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `gender` | String | No | `MALE` or `FEMALE`. Enables gender-aware thresholds for body fat % and SMI. Omit for gender-neutral (conservative) ranges. |

**Examples**
```
GET /api/v1/metrics/insights
GET /api/v1/metrics/insights?gender=MALE
```

**Response — 200 OK**

```json
{
  "success": true,
  "data": [
    {
      "metricType": "bmi",
      "value": 27.3,
      "unit": "kg/m²",
      "status": "BORDERLINE",
      "message": "Overweight range (BMI 25–29.9). A healthy target is 18.5–24.9.",
      "referenceRange": { "min": 18.5, "max": 24.9, "description": "Normal: 18.5–24.9 kg/m²" }
    },
    {
      "metricType": "cholesterolLDL",
      "value": 145.0,
      "unit": "mg/dL",
      "status": "BORDERLINE",
      "message": "LDL cholesterol is borderline high (130–159 mg/dL). Diet and activity can lower LDL.",
      "referenceRange": { "min": null, "max": 100.0, "description": "Optimal: < 100 mg/dL" }
    },
    {
      "metricType": "cholesterolHDL",
      "value": 65.0,
      "unit": "mg/dL",
      "status": "OK",
      "message": "HDL cholesterol is at a protective level (≥ 60 mg/dL).",
      "referenceRange": { "min": 60.0, "max": null, "description": "Protective: ≥ 60 mg/dL" }
    }
  ]
}
```

**Status values**

| Status | Meaning |
|---|---|
| `OK` | Within the healthy reference range |
| `BORDERLINE` | Mildly outside normal — worth monitoring |
| `WARNING` | Outside normal range — action recommended |
| `DANGER` | Significantly outside range — medical attention advised |

**Metrics that currently generate insights**

| metricType | Notes |
|---|---|
| `bmi` | Computed server-side from weight + height |
| `bodyFat` | Gender-aware when `?gender=` is provided |
| `visceralFat` | InBody level scale (1–9 normal) |
| `smiComputed` | Computed server-side; gender-aware cutoffs (AWGS 2019) |
| `cholesterolTotal` | ACC/AHA guidelines |
| `cholesterolLDL` | ACC/AHA guidelines |
| `cholesterolHDL` | Higher is better |
| `triglycerides` | ACC/AHA guidelines |
| `fastingGlucose` | ADA guidelines |
| `hba1c` | ADA guidelines |

Custom metrics and other built-in types (measurements, vitamins, blood panel counts) do not currently generate insights — they appear in the snapshot but not here. More engines can be added without changing this endpoint.

Returns an empty array `[]` if the user has no metric data, or none of the evaluated metrics are present in their snapshot.

---

## Common patterns

### Handling "no data yet" state

All list endpoints return an empty array `[]` when there is no data — the client should render an empty state (not treat it as an error).

### Conflict between existing entries and re-logging

The backend does not enforce "one entry per metricType per date". If the user submits `weight` for the same date twice, two entries will exist. On the log form, use `GET /api/v1/metrics/entries?date=` to pre-fill — if an entry already exists for a type, the client should `PUT` it instead of `POST`ing a new one.

### Displaying `logDate` vs. `createdAt`

- `logDate` (`YYYY-MM-DD`) is **when the measurement was taken** — use this for the date label in charts and cards.
- `createdAt` / `updatedAt` (ISO-8601) are system timestamps for when the record was written to the database — use these only for audit/debug purposes.
