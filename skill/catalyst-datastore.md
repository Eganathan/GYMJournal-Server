# Catalyst Java SDK v1 — DataStore & FileStore Reference

Sources:
- https://docs.catalyst.zoho.com/en/sdk/java/v1/overview/
- https://docs.catalyst.zoho.com/en/sdk/java/v1/cloud-scale/data-store/get-table-instance/
- https://docs.catalyst.zoho.com/en/sdk/java/v1/cloud-scale/data-store/get-rows/
- https://docs.catalyst.zoho.com/en/sdk/java/v1/cloud-scale/data-store/insert-rows/
- https://docs.catalyst.zoho.com/en/sdk/java/v1/cloud-scale/data-store/update-rows/
- https://docs.catalyst.zoho.com/en/sdk/java/v1/cloud-scale/data-store/delete-row/
- https://docs.catalyst.zoho.com/en/sdk/java/v1/cloud-scale/data-store/bulk-read/

---

## 1. SDK Initialization — AppSail + Jakarta Servlet

**Do not** call `ZCProject.initProject(token, USER)` with `X-ZC-User-Cred-Token` — it is a ZGS-internal
credential and causes "Environment Variables does not Exists" errors.

The correct initialization (done once per request in `CatalystAuthFilter`):

```kotlin
// CatalystAuthFilter.kt — called inside doFilter()
CatalystSDK.init(AuthHeaderProvider { headerName -> request.getHeader(headerName) })
// After init, ZCProject.getDefaultProjectConfig() returns the correct config
```

This reads ZGS-injected project/credential headers. AppSail sets the environment variables
automatically — **never set them manually**.

### SDK scopes (Admin vs User)

```java
// Admin scope — unrestricted access, uses project service account credentials
ZCProject adminProject = ZCProject.initProject("admin", ZCUserScope.ADMIN);

// User scope — permission-restricted, uses the end-user's credentials
ZCProject userProject  = ZCProject.initProject("user", ZCUserScope.USER);
```

In this project we always use the per-request `AuthHeaderProvider` init (maps to Admin scope via
project credentials). User scope is NOT used because `X-ZC-User-Cred-Token` is unreliable.

---

## 2. Core DataStore Classes

All in the `com.zc.component.object` package.

**In Kotlin, `object` is a keyword — the package must be backtick-escaped:**

```kotlin
import com.zc.component.`object`.ZCObject
import com.zc.component.`object`.ZCTable
import com.zc.component.`object`.ZCRowObject
import com.zc.component.`object`.ZCRowPagedResponse
```

| Class | Role |
|---|---|
| `ZCObject` | SDK entry point; call `ZCObject.getInstance()` |
| `ZCTable` | Table reference; get via `ZCObject.getInstance().getTableInstance(name)` |
| `ZCRowObject` | Single row (read or write) |
| `ZCRowPagedResponse` | Paged result container for `getPagedRows()` |

---

## 3. Get Table Instance

`getTableInstance()` does **not** fire a network call — it returns a local reference object.

```kotlin
// By table name (most common)
val table: ZCTable = ZCObject.getInstance().getTableInstance("Exercises")

// By table ID (Long)
val table: ZCTable = ZCObject.getInstance().getTableInstance(1510000000110121L)
```

Both overloads exist. **Always use the name variant in this project** — IDs change between dev/prod.

---

## 4. Read — Single Row (getRow)

```kotlin
// ALWAYS use this for findById — NOT ZCQL SELECT WHERE ROWID = x
val row: ZCRowObject? = table.getRow(rowId)
```

### ⚠️ CRITICAL: Use getRow — never ZCQL for findById

In AppSail (dev and prod), `ZCQL.getInstance()` (query path) can silently fail while ZCObject writes
succeed. Observed behaviour:
- `db.insert()` (ZCObject) → row IS written ✓
- `db.queryOne("SELECT * FROM T WHERE ROWID = x")` (ZCQL) → returns null silently ✗
- `db.getRow(TABLE, rowId)` (ZCObject) → returns the row correctly ✓

```kotlin
// ✅ Correct
fun findById(id: Long): Exercise? = db.getRow(TABLE, id)?.toExercise()

// ❌ Wrong — silently returns null in AppSail even when the row exists
fun findById(id: Long): Exercise? =
    db.queryOne("SELECT * FROM $TABLE WHERE ROWID = $id")?.toExercise()
```

---

## 5. Read — Paginated (getPagedRows)

`getPagedRows()` is the ZCQL-independent way to page through all rows. Prefer ZCQL queries for
filtered fetches; use this only if you need raw pagination without filters.

```kotlin
// First page
var resp: ZCRowPagedResponse = table.getPagedRows(null, 200L)
val rows: List<ZCRowObject> = resp.rows
val token: String? = resp.nextToken
val hasMore: Boolean = resp.moreRecordsAvailable()

// Subsequent pages
resp = table.getPagedRows(token, 200L)
```

> `getAllRows()` is **deprecated** — use `getPagedRows()` instead (requires SDK v1.7.0+).

---

## 6. Insert Row

```kotlin
val row = ZCRowObject.getInstance()
row.set("name", "Bench Press")
row.set("difficulty", "BEGINNER")
// Do NOT set ROWID, CREATORID, CREATEDTIME, MODIFIEDTIME — auto-set by Catalyst

val insertedRow: ZCRowObject = table.insertRow(row)
```

### Read-after-write pattern

After insert, the returned `ZCRowObject` may have `ROWID` under two different key names depending
on SDK version. Always try both:

```kotlin
val row = db.insert(TABLE, map)  // CatalystDataStoreRepository wraps table.insertRow()
val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
    ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
return if (rowId != null) db.getRow(TABLE, rowId) ?: fallback.copy(id = rowId) else fallback
```

### Batch insert

```kotlin
val rows = listOf(row1, row2)
table.insertRows(rows)
```

---

## 7. Update Row

```kotlin
val row = ZCRowObject.getInstance()
row.set("ROWID", existingId)    // MANDATORY — identifies which row to update
row.set("name", "Incline Press")
table.updateRows(listOf(row))
```

- `ROWID` must be set on every `ZCRowObject` in the list — it identifies the target row
- `MODIFIEDTIME` is auto-updated by Catalyst
- Returns void; success is implicit

---

## 8. Delete Row

```kotlin
table.deleteRow(rowId)
```

- **Permanent — no recovery**
- Only one row at a time; for batch deletes use Bulk Delete API
- Returns void

---

## 9. Reading Values from ZCRowObject

```kotlin
private fun ZCRowObject.toDomain() = MyEntity(
    id        = get("ROWID")?.toString()?.toLongOrNull(),
    name      = get("name")?.toString() ?: "",
    count     = get("count")?.toString()?.toIntOrNull() ?: 0,
    price     = get("price")?.toString()?.toDoubleOrNull() ?: 0.0,
    active    = get("active")?.toString()?.toBooleanStrictOrNull() ?: false,
    // CREATORID is unreliable in AppSail — use explicit userId column instead (see section 12)
    createdBy = get("userId")?.toString()?.takeIf { it.isNotBlank() }
                    ?: get("CREATORID")?.toString() ?: "",
    createdAt = get("CREATEDTIME")?.toString() ?: "",
    updatedAt = get("MODIFIEDTIME")?.toString() ?: ""
)
```

All `get()` calls return `Any?` — always `.toString()` before parsing.

---

## 10. CatalystDataStoreRepository — Project Wrapper

This project wraps the raw SDK in a single repository class. Always use this instead of calling
`ZCObject`/`ZCQL` directly:

```kotlin
db.query("SELECT ...")          // → List<ZCRowObject>  (ZCQL — for filtered list queries)
db.queryOne("SELECT ...")       // → ZCRowObject?       (ZCQL — for filtered single rows, NOT findById)
db.insert(TABLE, map)           // → ZCRowObject        (ZCObject — extract ROWID after)
db.update(TABLE, rowId, map)    // → Unit               (ZCObject — MODIFIEDTIME auto-updated)
db.delete(TABLE, rowId)         // → Unit               (ZCObject)
db.count(TABLE, condition?)     // → Long               (ZCObject / ZCQL)
db.getRow(TABLE, rowId)         // → ZCRowObject?       (ZCObject — ALWAYS use for findById)
```

---

## 11. Bulk Read (Async Job)

For large exports (up to 200,000 rows). Result delivered as a CSV file.

```java
// Simplest — all rows from a table
ZCBulkResult result = ZCBulkReadServices.createBulkReadJob(tableId);

// With filters
ZCBulkQueryDetails query = new ZCBulkQueryDetails();
query.setTableIdentifier(tableId);
query.setMaxRows(50000L);
ZCBulkResult result = ZCBulkReadServices.createBulkReadJob(tableId, query);

// Poll status
ZCBulkReadServices.getBulkReadJobStatus(result.getJobId());
```

Bulk read classes are in `com.zc.component.object.bulk`.

---

## 12. CREATORID Is Unreliable in AppSail — Use Explicit userId Column

### Problem

When `ZCObject.insertRow()` is called from an AppSail function (via `ZCProject.getDefaultProjectConfig()`),
Catalyst sets `CREATORID` to the **project's service account** ZID — NOT the end-user's ZID from
`x-zc-user-id`. This makes `CREATORID` useless for per-user filtering/ownership.

### Solution

Store the user's ZID explicitly in a `userId` column on every table that needs ownership tracking.

```kotlin
// toMap() — always write userId explicitly
private fun MyEntity.toMap(): Map<String, Any> = buildMap {
    put("userId", createdBy)   // explicit column; CREATORID unreliable in AppSail
    put("name", name)
    // ...
}

// toEntry() / toDomain() — read userId first, fall back to CREATORID for old rows
private fun ZCRowObject.toDomain() = MyEntity(
    createdBy = get("userId")?.toString()?.takeIf { it.isNotBlank() }
                    ?: get("CREATORID")?.toString() ?: "",
    // ...
)

// ZCQL WHERE — use userId, not CREATORID
val q = "SELECT * FROM MyTable WHERE userId = '${ZcqlSanitizer.sanitize(userId)}'"
```

### Tables with explicit userId column in this project

All user-owned tables: `WorkoutSessions`, `WaterIntakeLogs`, `Routines`, `Exercises`,
`BodyMetricEntries`, `CustomMetricDefs`.

### Tables without userId (no per-user filtering needed)

Lookup tables shared across all users: `MuscleGroups`, `Equipment`.

---

## 13. FileStore API (ZCFileService)

Used for the media upload endpoint (`POST /api/v1/media/upload`).

```kotlin
import com.zc.component.files.ZCFileService
import com.zc.component.files.ZCFileDetail

// Upload
val config = ZCProject.getDefaultProjectConfig()
val service: ZCFileService = ZCFileService.getInstance(config, "exercises")  // folder name
val detail: ZCFileDetail = file.inputStream.use { stream ->
    service.uploadFile(stream, file.originalFilename ?: "upload")
}

// Get public URL
val url: String = service.constructURL(detail.fileId)

// Delete
service.deleteFile(fileId)
```

### ZCFileDetail methods

| Method | Returns | Notes |
|---|---|---|
| `getFileId()` | `Long` | Unique file ID; store this to delete later |
| `getFileName()` | `String` | Original file name |
| `getFileSize()` | `Long` | Size in bytes |
| `getFolderId()` | `Long` | Parent folder ID |

### Folder setup

Folders must exist in the Catalyst Console before calling `ZCFileService.getInstance(config, folderName)`.
If the folder does not exist, the SDK throws — surface as 500 `UPLOAD_FAILED`.

Folders in this project: `exercises`, `routines`, `misc`.

---

## 14. Exception Handling

```kotlin
import com.zc.common.ZCException
import com.zc.common.ZCServerException
import com.zc.common.ZCClientException
```

All DataStore / FileStore operations throw subclasses of `ZCException`. Wrap with `try/catch`:

```kotlin
fun getRow(tableName: String, rowId: Long): ZCRowObject? {
    return try {
        ZCObject.getInstance().getTableInstance(tableName).getRow(rowId)
    } catch (e: Throwable) {
        logger.warn("[DataStore] getRow failed for $tableName ROWID=$rowId: ${e.message}")
        null
    }
}
```

---

## 15. AppSail SDK Quirks Cheatsheet

| # | Quirk | How to handle |
|---|---|---|
| 1 | `CREATORID` = project service account, not user | Add explicit `userId` Var Char(50) column to user-owned tables |
| 2 | ZCQL reads can silently return null while ZCObject writes succeed | Always use `db.getRow(TABLE, id)` for findById |
| 3 | `X-ZC-User-Cred-Token` is not a Catalyst access token | Never pass to `ZCProject.initProject(token, USER)` |
| 4 | SDK env vars are injected by AppSail automatically | Never set `X_ZOHO_CATALYST_*` env vars manually |
| 5 | `getTableInstance()` has no network call | Safe to call synchronously per-request |
| 6 | `getAllRows()` is deprecated | Use `getPagedRows(null, 200L)` instead |
| 7 | `deleteRow()` is single-row only | Use Bulk Delete API for batch deletions |
| 8 | INSERT result ROWID key name varies | Try `"ROWID"` then `"$TABLE.ROWID"` |
| 9 | FileStore folder must pre-exist | Create folders in Catalyst Console before deploying |
| 10 | FileStore SDK needs `getDefaultProjectConfig()` | Same pattern as DataStore — `ZCProject.getDefaultProjectConfig()` after `CatalystSDK.init(...)` |
