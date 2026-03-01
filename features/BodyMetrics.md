# Body Metrics Feature

> **Status:** Implementation in progress.
> Keep this file updated as implementation progresses.

---

## Overview

Body Metrics lets users log and track physical measurements over time:
body weight, body fat, limb measurements, InBody composition data, blood work, and custom user-defined metrics.

Each logged entry is a single `(metricType, value, unit, date)` tuple owned by the authenticated user.
The frontend (`MetricsLog.js`) submits multiple entries for one date in a single batch call.
Users can also define their own custom metric types (e.g. "SGPT", "VO2 Max").

**Computed metrics** (`bmi`, `smiComputed`) are **never stored in the database**.
They are derived **server-side** in the snapshot endpoint from stored `weight`, `height`, and `smm` values,
and appended to the snapshot response automatically when source data is available.
Attempting to log them directly via `POST /api/v1/metrics/entries` returns a 400 error.

---

## Complete Metric Types Reference

This table is the authoritative list of every valid `metricType` value. Sourced directly from `react-app/src/lib/constants.js`.

The backend accepts any string as `metricType` (including custom keys prefixed `custom_`), but this table documents all known built-in types.

### Group: Core Metrics (`core`)

Shown by default on the log form.

| metricType | Label | Unit | Stored? |
|---|---|---|---|
| `weight` | Weight | `kg` | ✓ |
| `height` | Height | `cm` | ✓ |
| `bodyFat` | Body Fat | `%` | ✓ |
| `waist` | Waist | `cm` | ✓ |
| `bmi` | BMI | `kg/m²` | ✗ Computed server-side: `weight ÷ (height/100)²` |

### Group: Body Composition / InBody (`inbody`)

Shown by default. Typically from an InBody scan.

| metricType | Label | Unit | Stored? |
|---|---|---|---|
| `smm` | Skeletal Muscle | `kg` | ✓ |
| `bmr` | BMR | `kcal` | ✓ |
| `totalBodyWater` | Total Body Water | `L` | ✓ |
| `protein` | Protein | `kg` | ✓ |
| `minerals` | Minerals | `kg` | ✓ |
| `smi` | SMI | `kg/m²` | ✓ |
| `visceralFat` | Visceral Fat | `level` | ✓ |
| `leanBodyMass` | Lean Body Mass | `kg` | ✓ |
| `smiComputed` | SMI (computed) | `kg/m²` | ✗ Computed server-side: `smm ÷ (height/100)²` |

### Group: Upper Body Measurements (`upper`)

Shown by default.

| metricType | Label | Unit | Stored? |
|---|---|---|---|
| `neck` | Neck | `cm` | ✓ |
| `chest` | Chest | `cm` | ✓ |
| `shoulders` | Shoulders | `cm` | ✓ |

### Group: Arms (`arms`)

Shown by default.

| metricType | Label | Unit | Stored? |
|---|---|---|---|
| `bicepLeft` | Bicep (L) | `cm` | ✓ |
| `bicepRight` | Bicep (R) | `cm` | ✓ |
| `forearmLeft` | Forearm (L) | `cm` | ✓ |
| `forearmRight` | Forearm (R) | `cm` | ✓ |

### Group: Lower Body Measurements (`lower`)

Shown by default.

| metricType | Label | Unit | Stored? |
|---|---|---|---|
| `hips` | Hips | `cm` | ✓ |
| `thighLeft` | Thigh (L) | `cm` | ✓ |
| `thighRight` | Thigh (R) | `cm` | ✓ |
| `calfLeft` | Calf (L) | `cm` | ✓ |
| `calfRight` | Calf (R) | `cm` | ✓ |

---

> The groups below are under **Medical & Blood Work** — collapsed behind an "Advanced" toggle in the UI. Same storage model, just hidden by default.

### Group: Lipid Panel (`lipids`)

| metricType | Label | Unit | Stored? |
|---|---|---|---|
| `cholesterolTotal` | Total Cholesterol | `mg/dL` | ✓ |
| `cholesterolHDL` | HDL | `mg/dL` | ✓ |
| `cholesterolLDL` | LDL | `mg/dL` | ✓ |
| `triglycerides` | Triglycerides | `mg/dL` | ✓ |

### Group: Sugar & Metabolic (`metabolic`)

| metricType | Label | Unit | Stored? |
|---|---|---|---|
| `fastingGlucose` | Fasting Glucose | `mg/dL` | ✓ |
| `hba1c` | HbA1c | `%` | ✓ |
| `insulin` | Insulin | `µU/mL` | ✓ |
| `uricAcid` | Uric Acid | `mg/dL` | ✓ |
| `creatinine` | Creatinine | `mg/dL` | ✓ |

### Group: Vitamins & Minerals (`vitamins`)

| metricType | Label | Unit | Stored? |
|---|---|---|---|
| `vitaminD` | Vitamin D | `ng/mL` | ✓ |
| `vitaminB12` | Vitamin B12 | `pg/mL` | ✓ |
| `iron` | Iron | `µg/dL` | ✓ |
| `ferritin` | Ferritin | `ng/mL` | ✓ |
| `calcium` | Calcium | `mg/dL` | ✓ |

### Group: Blood Panel (`blood`)

| metricType | Label | Unit | Stored? |
|---|---|---|---|
| `hemoglobin` | Hemoglobin | `g/dL` | ✓ |
| `rbc` | RBC | `M/µL` | ✓ |
| `wbc` | WBC | `K/µL` | ✓ |
| `platelets` | Platelets | `K/µL` | ✓ |
| `tsh` | TSH | `µIU/mL` | ✓ |
| `testosterone` | Testosterone | `ng/dL` | ✓ |

### Custom Metrics

Keys are prefixed `custom_`, derived from the user-supplied label:
```
metricKey = "custom_" + label.lowercase().replace(/[^a-z0-9]/g, "_")
```

Examples: `custom_sgpt`, `custom_vo2_max`, `custom_cortisol`.

Unit and label are user-defined. Custom metric definitions are stored in the `CustomMetricDefs` table.

---

## DataStore Tables

Create both tables in the **Catalyst Console** before deploying.

### Table: `BodyMetricEntries`

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `metricType` | Text | ✓ | Any key from the Metric Types Reference above, or a `custom_*` key |
| `value` | Double | ✓ | The measured numeric value |
| `unit` | Text | ✓ | Unit string matching the metric type, e.g. `kg`, `%`, `cm`, `mg/dL`, `µU/mL` |
| `logDate` | Text | ✓ | `YYYY-MM-DD` — stored as text; lexicographic compare works for date range queries |
| `notes` | Text | — | Optional free-text note |

System columns (auto, never create manually): `ROWID`, `CREATORID`, `CREATEDTIME`, `MODIFIEDTIME`.

> Index recommendation: mark `logDate` as **Search Indexed** for efficient date-range queries.

### Table: `CustomMetricDefs`

| Column | Type | Mandatory | Notes |
|---|---|---|---|
| `metricKey` | Text | ✓ | Derived key, e.g. `custom_sgpt`. Unique per user (enforced in service layer). |
| `label` | Text | ✓ | Human-readable name the user typed, e.g. `SGPT` |
| `unit` | Text | — | Unit string, e.g. `U/L`. May be empty string. |

System columns (auto): `ROWID`, `CREATORID`, `CREATEDTIME`, `MODIFIEDTIME`.

---

## Backend Module

### Files to create

```
src/main/kotlin/dev/eknath/GymJournal/
├── model/
│   ├── domain/
│   │   └── BodyMetric.kt          # BodyMetricEntry + CustomMetricDef domain classes
│   └── dto/
│       └── BodyMetricDtos.kt      # All request/response DTOs
└── modules/
    └── metrics/
        ├── BodyMetricController.kt
        ├── BodyMetricService.kt
        ├── BodyMetricRepository.kt
        └── CustomMetricDefRepository.kt
```

### Domain models (`BodyMetric.kt`)

```kotlin
data class BodyMetricEntry(
    val id: Long? = null,
    val metricType: String,      // e.g. "weight", "bodyFat", "custom_sgpt"
    val value: Double,
    val unit: String,            // e.g. "kg", "%", "µU/mL"
    val logDate: String,         // YYYY-MM-DD
    val notes: String = "",
    // Catalyst system columns
    val createdBy: String = "",  // CREATORID
    val createdAt: String = "",  // CREATEDTIME
    val updatedAt: String = ""   // MODIFIEDTIME
)

data class CustomMetricDef(
    val id: Long? = null,
    val metricKey: String,       // e.g. "custom_sgpt"
    val label: String,           // e.g. "SGPT"
    val unit: String,            // e.g. "U/L" — may be empty
    val createdBy: String = "",  // CREATORID
    val createdAt: String = ""   // CREATEDTIME
)
```

### DTOs (`BodyMetricDtos.kt`)

```kotlin
// --- Entries ---

data class LogMetricEntryRequest(
    @NotBlank val metricType: String,
    val value: Double,
    @NotBlank val unit: String,
    val logDate: String = LocalDate.now().toString(),  // default today
    val notes: String = ""
)

data class BatchLogMetricRequest(
    @NotEmpty @Valid val entries: List<LogMetricEntryRequest>
)

data class UpdateMetricEntryRequest(
    val value: Double? = null,
    val unit: String? = null,
    val logDate: String? = null,
    val notes: String? = null
)

data class MetricEntryResponse(
    val id: Long,
    val metricType: String,
    val value: Double,
    val unit: String,
    val logDate: String,
    val notes: String,
    val createdAt: String,
    val updatedAt: String
)

// Used for dashboard snapshot — one item per metric type
data class MetricSnapshotItem(
    val metricType: String,
    val value: Double,
    val unit: String,
    val logDate: String      // date of most-recent measurement
)

// --- Custom defs ---

data class CreateCustomMetricRequest(
    @NotBlank val label: String,
    val unit: String = ""
)

data class CustomMetricDefResponse(
    val id: Long,
    val metricKey: String,
    val label: String,
    val unit: String
)
```

### Key repository patterns

**`BodyMetricRepository`**

```kotlin
// All entries for a given date (for the log form pre-fill / day view)
fun findByDate(userId: String, date: String): List<BodyMetricEntry>
// ZCQL: SELECT * FROM BodyMetricEntries WHERE CREATORID = '...' AND logDate = '2026-02-28'

// History for one metric type, with optional date range (for trend charts)
fun findByType(userId: String, metricType: String, startDate: String, endDate: String): List<BodyMetricEntry>
// ZCQL: SELECT * FROM BodyMetricEntries
//         WHERE CREATORID = '...' AND metricType = 'weight'
//           AND logDate >= '2026-01-01' AND logDate <= '2026-02-28'
//         ORDER BY logDate ASC LIMIT 0,300
// Uses 4 WHERE conditions — within ZCQL's 5-condition limit. Do not add a 5th.

// Most-recent entries for snapshot computation (all types at once)
fun findRecent(userId: String): List<BodyMetricEntry>
// ZCQL: SELECT * FROM BodyMetricEntries WHERE CREATORID = '...'
//         ORDER BY logDate DESC LIMIT 0,300
// Then group in-memory by metricType, take first (most recent) of each.
// BMI and smiComputed are then computed from the stored weight/height/smm values.

// All entries for a given metricType (used for cascade-delete on custom def removal)
fun findAllByType(userId: String, metricType: String): List<BodyMetricEntry>
// ZCQL: SELECT * FROM BodyMetricEntries
//         WHERE CREATORID = '...' AND metricType = 'custom_sgpt'
//         LIMIT 0,300

fun findById(id: Long): BodyMetricEntry?
fun save(entry: BodyMetricEntry): BodyMetricEntry
fun update(id: Long, entry: BodyMetricEntry)
fun delete(id: Long)
```

**`CustomMetricDefRepository`**

```kotlin
// All custom defs for the user
fun findAll(userId: String): List<CustomMetricDef>
// ZCQL: SELECT * FROM CustomMetricDefs WHERE CREATORID = '...' ORDER BY label ASC

// Check if a custom key already exists for this user (uniqueness guard)
fun findByKey(userId: String, metricKey: String): CustomMetricDef?
// ZCQL: SELECT * FROM CustomMetricDefs WHERE CREATORID = '...' AND metricKey = 'custom_sgpt'

fun save(def: CustomMetricDef): CustomMetricDef
fun delete(id: Long)
```

### Service responsibilities

| Method | Logic |
|---|---|
| `batchLog()` | Iterate `BatchLogMetricRequest.entries`, reject `bmi`/`smiComputed` with 400 (computed-only), validate `logDate` is `YYYY-MM-DD`, save each |
| `getEntriesForDate()` | Repo call, return sorted by `metricType` |
| `getHistory()` | Repo call with date range defaults (startDate = 90 days ago, endDate = today), return sorted by `logDate ASC` |
| `getSnapshot()` | `findRecent()` → group by `metricType` in-memory → pick latest-date entry per type → **compute and append `bmi` (from weight + height) and `smiComputed` (from smm + height) if source data available** |
| `addCustomDef()` | Derive `metricKey`, check uniqueness via `findByKey`, save |
| `deleteCustomDef()` | Delete def by key, then `findAllByType` + delete each entry (cascade) |
| `updateEntry()` | Ownership check (`entry.createdBy != userId` → 403), then `repo.update()` |
| `deleteEntry()` | Ownership check, then `repo.delete()` |

### Computed metric formulas (server-side in snapshot)

| metricType | Formula | Source fields | logDate |
|---|---|---|---|
| `bmi` | `weight / (height/100)²` | `weight` (kg) + `height` (cm) | `max(weight.logDate, height.logDate)` |
| `smiComputed` | `smm / (height/100)²` | `smm` (kg) + `height` (cm) | `max(smm.logDate, height.logDate)` |

Values are rounded to 1 decimal place. Both items are omitted from the snapshot if any source field is missing.

---

## API Endpoints

Base path: `/api/v1/metrics`
All endpoints require auth (ZGS injects `x-zc-user-id`).

### Endpoint Summary

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/metrics/entries` | Batch log metric entries |
| GET | `/api/v1/metrics/entries?date=YYYY-MM-DD` | All entries for a date |
| PUT | `/api/v1/metrics/entries/{id}` | Update an entry (creator only) |
| DELETE | `/api/v1/metrics/entries/{id}` | Delete an entry (creator only) |
| GET | `/api/v1/metrics/{metricType}/history` | History for one metric type |
| GET | `/api/v1/metrics/snapshot` | Latest value per metric type + computed BMI/SMI |
| GET | `/api/v1/metrics/custom` | List user's custom metric definitions |
| POST | `/api/v1/metrics/custom` | Create a custom metric definition |
| DELETE | `/api/v1/metrics/custom/{key}` | Delete custom def + cascade-delete its entries |

---

### `POST /api/v1/metrics/entries` — Batch log

Logs one or more metric measurements for a date. The frontend sends all filled-in fields for one date in a single request.

**Request Body**
```json
{
  "entries": [
    { "metricType": "weight",          "value": 82.5,  "unit": "kg",    "logDate": "2026-02-28" },
    { "metricType": "bodyFat",         "value": 18.2,  "unit": "%",     "logDate": "2026-02-28" },
    { "metricType": "waist",           "value": 84.0,  "unit": "cm",    "logDate": "2026-02-28" },
    { "metricType": "smm",             "value": 36.1,  "unit": "kg",    "logDate": "2026-02-28" },
    { "metricType": "cholesterolHDL",  "value": 58.0,  "unit": "mg/dL", "logDate": "2026-02-28" },
    { "metricType": "custom_sgpt",     "value": 32.0,  "unit": "U/L",   "logDate": "2026-02-28" }
  ]
}
```

**Response — 201 Created**
```json
{
  "success": true,
  "data": [
    { "id": 1001, "metricType": "weight",  "value": 82.5, "unit": "kg", "logDate": "2026-02-28", "notes": "", "createdAt": "2026-02-28T10:00:00", "updatedAt": "2026-02-28T10:00:00" },
    { "id": 1002, "metricType": "bodyFat", "value": 18.2, "unit": "%",  "logDate": "2026-02-28", "notes": "", "createdAt": "2026-02-28T10:00:00", "updatedAt": "2026-02-28T10:00:00" }
  ]
}
```

**Error — 400** if `metricType` is a computed-only type (`bmi`, `smiComputed`).
**Error — 400** if `logDate` is not in `YYYY-MM-DD` format.

---

### `GET /api/v1/metrics/entries?date=YYYY-MM-DD` — Entries for a date

All entries logged by the calling user on the given date. Used to pre-fill the log form when a user revisits a date.

**Response — 200 OK**
```json
{
  "success": true,
  "data": [
    { "id": 1001, "metricType": "weight",  "value": 82.5, "unit": "kg", "logDate": "2026-02-28", "notes": "", "createdAt": "...", "updatedAt": "..." },
    { "id": 1002, "metricType": "bodyFat", "value": 18.2, "unit": "%",  "logDate": "2026-02-28", "notes": "", "createdAt": "...", "updatedAt": "..." }
  ]
}
```

---

### `PUT /api/v1/metrics/entries/{id}` — Update entry

All fields optional — only provided fields are updated.

**Request Body**
```json
{ "value": 82.1, "notes": "After morning workout" }
```

**Response — 200 OK** — full `MetricEntryResponse`.
**Error — 404** entry not found. **Error — 403** not the creator.

---

### `DELETE /api/v1/metrics/entries/{id}` — Delete entry

**Response — 204 No Content**
**Error — 404** entry not found. **Error — 403** not the creator.

---

### `GET /api/v1/metrics/{metricType}/history` — History for a type

All entries for one `metricType` sorted by `logDate ASC`. Drives the trend chart in `MetricHistory.js`.

**Path Parameters**

| Parameter | Type | Example |
|---|---|---|
| `metricType` | String | `weight`, `cholesterolHDL`, `custom_sgpt` |

**Query Parameters**

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `startDate` | String | No | 90 days ago | `YYYY-MM-DD` |
| `endDate` | String | No | today | `YYYY-MM-DD` |

**Example**
```
GET /api/v1/metrics/weight/history?startDate=2026-01-01&endDate=2026-02-28
```

**Response — 200 OK**
```json
{
  "success": true,
  "data": [
    { "id": 900, "metricType": "weight", "value": 84.0, "unit": "kg", "logDate": "2026-01-15", "notes": "", "createdAt": "...", "updatedAt": "..." },
    { "id": 1001, "metricType": "weight", "value": 82.5, "unit": "kg", "logDate": "2026-02-28", "notes": "", "createdAt": "...", "updatedAt": "..." }
  ]
}
```

---

### `GET /api/v1/metrics/snapshot` — Latest value per metric type

Returns the most-recent logged value for every metric type the user has ever recorded. **Also appends server-side computed `bmi` and `smiComputed`** when the source metrics (`weight`, `height`, `smm`) are present in the snapshot.

**Response — 200 OK**
```json
{
  "success": true,
  "data": [
    { "metricType": "weight",         "value": 82.5,  "unit": "kg",    "logDate": "2026-02-28" },
    { "metricType": "bodyFat",        "value": 18.2,  "unit": "%",     "logDate": "2026-02-28" },
    { "metricType": "height",         "value": 178.0, "unit": "cm",    "logDate": "2026-01-01" },
    { "metricType": "smm",            "value": 36.1,  "unit": "kg",    "logDate": "2026-02-28" },
    { "metricType": "cholesterolHDL", "value": 58.0,  "unit": "mg/dL", "logDate": "2026-02-10" },
    { "metricType": "custom_sgpt",    "value": 32.0,  "unit": "U/L",   "logDate": "2026-02-28" },
    { "metricType": "bmi",            "value": 26.0,  "unit": "kg/m²", "logDate": "2026-02-28" },
    { "metricType": "smiComputed",    "value": 11.4,  "unit": "kg/m²", "logDate": "2026-02-28" }
  ]
}
```

> Implementation: fetch most-recent 300 entries ordered by `logDate DESC`, group by `metricType` in-memory and take the first (newest) entry per type, then derive and append computed metrics.
>
> `bmi` and `smiComputed` are **omitted** from the response if the required source metrics (`weight`/`height` or `smm`/`height`) are not available in the snapshot.

---

### `GET /api/v1/metrics/custom` — List custom defs

**Response — 200 OK**
```json
{
  "success": true,
  "data": [
    { "id": 5, "metricKey": "custom_sgpt",    "label": "SGPT",    "unit": "U/L"       },
    { "id": 6, "metricKey": "custom_vo2_max", "label": "VO2 Max", "unit": "mL/kg/min" }
  ]
}
```

---

### `POST /api/v1/metrics/custom` — Create custom def

**Request Body**
```json
{ "label": "SGPT", "unit": "U/L" }
```

The `metricKey` is derived server-side using the same algorithm as the frontend:
```
metricKey = "custom_" + label.lowercase().replace(Regex("[^a-z0-9]"), "_")
```

**Response — 201 Created**
```json
{
  "success": true,
  "data": { "id": 5, "metricKey": "custom_sgpt", "label": "SGPT", "unit": "U/L" }
}
```

**Error — 400** if a custom def with the same derived key already exists for this user.

---

### `DELETE /api/v1/metrics/custom/{key}` — Delete custom def

Deletes the definition **and all `BodyMetricEntries`** with that `metricType` for this user (cascade delete).

**Path Parameters**

| Parameter | Type | Example |
|---|---|---|
| `key` | String | `custom_sgpt` |

**Response — 204 No Content**

---

## ZCQL Query Reference

```sql
-- Entries for a specific date
SELECT * FROM BodyMetricEntries
WHERE CREATORID = '<userId>' AND logDate = '2026-02-28'
ORDER BY metricType ASC

-- History for a type with date range (4 conditions — at ZCQL limit)
SELECT * FROM BodyMetricEntries
WHERE CREATORID = '<userId>' AND metricType = 'weight'
  AND logDate >= '2026-01-01' AND logDate <= '2026-02-28'
ORDER BY logDate ASC LIMIT 0,300

-- Most-recent entries for snapshot computation
SELECT * FROM BodyMetricEntries
WHERE CREATORID = '<userId>'
ORDER BY logDate DESC LIMIT 0,300

-- Custom defs for user
SELECT * FROM CustomMetricDefs
WHERE CREATORID = '<userId>'
ORDER BY label ASC

-- Uniqueness check for a custom metric key
SELECT * FROM CustomMetricDefs
WHERE CREATORID = '<userId>' AND metricKey = 'custom_sgpt'

-- Cascade delete: find all entries for a custom type first
SELECT * FROM BodyMetricEntries
WHERE CREATORID = '<userId>' AND metricType = 'custom_sgpt'
LIMIT 0,300
-- Then delete each by ROWID individually (CatalystDataStoreRepository.delete() is ROWID-only)
```

> **ZCQL constraints:**
> - Max **5 WHERE conditions** per query. The history query uses 4 — do not add a 5th.
> - Max **300 rows** per query — always paginate with `LIMIT offset,count`.
> - No bind parameters — all user strings must go through `ZcqlSanitizer.sanitize()` before interpolation.
> - `logDate` stored as `Text` (not `DateTime`) so `>=` / `<=` string comparisons on `YYYY-MM-DD` work correctly.

---

## Frontend Integration Notes

The React app (`react-app/`) currently stores all metrics in **localStorage via Zustand persist** (`gymjournal-metrics`). When wiring to the backend, replace these store methods:

| Store method | Backend call |
|---|---|
| `addEntry(type, val, unit, date)` | `POST /api/v1/metrics/entries` (wrapped in `BatchLogMetricRequest`) |
| `deleteEntry(id)` | `DELETE /api/v1/metrics/entries/{id}` |
| `getLatest(metricType)` | Derive from `GET /api/v1/metrics/snapshot` response |
| `getHistory(metricType)` | `GET /api/v1/metrics/{metricType}/history` |
| `addCustomMetricDef(label, unit)` | `POST /api/v1/metrics/custom` |
| `removeCustomMetricDef(key)` | `DELETE /api/v1/metrics/custom/{key}` |

**Key derivation must match exactly.** Frontend:
```js
'custom_' + label.toLowerCase().replace(/[^a-z0-9]/g, '_')
```
Backend (Kotlin):
```kotlin
"custom_" + label.lowercase().replace(Regex("[^a-z0-9]"), "_")
```

**BMI / SMI are never stored.** The backend computes them server-side in the snapshot:
- `bmi = weight / (height/100)²`
- `smiComputed = smm / (height/100)²`

The snapshot response includes `bmi` and `smiComputed` as `MetricSnapshotItem` entries.
The frontend can read them directly from the snapshot — no client-side computation needed.

**`logDate`** is always `YYYY-MM-DD` (from the HTML date picker) — no timezone conversion needed on either side.

**Units are frontend-canonical.** The unit for each built-in type is fixed (see the Metric Types Reference table above). The frontend never lets users change units for built-in types — it always sends the canonical unit. Custom metric units are whatever the user typed when creating the def.

---

## Implementation Checklist

- [x] Create `features/BodyMetrics.md` with complete spec
- [ ] Create `BodyMetricEntries` table in Catalyst Console (5 columns + system columns)
- [ ] Create `CustomMetricDefs` table in Catalyst Console (3 columns + system columns)
- [x] Create `model/domain/BodyMetric.kt`
- [x] Create `model/dto/BodyMetricDtos.kt`
- [x] Create `BodyMetricRepository.kt`
- [x] Create `CustomMetricDefRepository.kt`
- [x] Create `BodyMetricService.kt` (includes server-side BMI/SMI computation)
- [x] Create `BodyMetricController.kt`
- [x] Update `GlobalExceptionHandler.kt` — `IllegalArgumentException` → 400
- [x] Add Body Metrics endpoints to `PROJECT.md` → API Endpoints section
- [ ] Wire React `metricsStore.js` methods to backend calls
- [ ] Test: batch log with 10+ entries in one request
- [ ] Test: snapshot returns one entry per metric type (most recent wins)
- [ ] Test: snapshot includes computed bmi and smiComputed when weight/height/smm are present
- [ ] Test: cascade delete removes all entries for the custom type
- [ ] Test: computed types (`bmi`, `smiComputed`) are rejected with 400 on batch log
