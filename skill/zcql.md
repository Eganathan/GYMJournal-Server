# ZCQL & Catalyst DataStore — Complete Reference

Sources:
- https://docs.catalyst.zoho.com/en/cloud-scale/help/zcql/introduction/
- https://docs.catalyst.zoho.com/en/cloud-scale/help/data-store/columns/

---

## 1. DataStore Column Types

When creating tables in the Catalyst Console, these are the available column types:

| Type | Notes | Max |
|---|---|---|
| `Var Char` | Short strings, fast filtering | 255 chars |
| `Text` | Long strings, instructions, JSON arrays | 10,000 chars |
| `Int` | 4-byte integer | ±9,999,999,999 |
| `BigInt` | 8-byte integer (use for ROWIDs / FKs) | ±9.2 × 10¹⁸ |
| `Double` | Floating point | 17 digits |
| `Boolean` | true/false only | — |
| `Date` | `YYYY-MM-DD` | — |
| `DateTime` | `YYYY-MM-DD HH:MM:SS` | — |
| `Foreign Key` | References another table's ROWID | — |
| `Encrypted Text` | Encrypted at rest; most operators blocked | 10,000 chars |

### Column properties (available for any column)

| Property | Meaning |
|---|---|
| `IsMandatory` | Column cannot be empty on insert |
| `IsUnique` | No duplicate values allowed |
| `Search Index` | Enables full-text search on this column |
| `Max Length` | Var Char only; set to actual max needed |
| `Default Value` | Fallback value when not provided |
| `Foreign Key → Parent Table` | Pick the parent table |
| `On Delete` | `Null` (set FK to null) or `Cascade` (delete child row) |

### System columns (auto-created on every table — never add manually)

| Column | Type | Set by |
|---|---|---|
| `ROWID` | BigInt | Auto-increment on insert |
| `CREATORID` | Var Char | Catalyst — userId of inserting user |
| `CREATEDTIME` | DateTime | Catalyst — timestamp of insert |
| `MODIFIEDTIME` | DateTime | Catalyst — timestamp of last update |

---

## 2. Table Creation (Catalyst Console UI)

**ZCQL has no DDL (no `CREATE TABLE`).** Tables must be created through the Catalyst Console.

### Navigation
`Catalyst Console → Data Store → New Table`

### How to give table creation instructions

Instead of saying "create the table yourself", always provide a full column spec like this:

---

### Table creation recipe format

**Table: `ExampleTable`**

| # | Column Name | Type | Mandatory | Unique | Notes |
|---|---|---|---|---|---|
| 1 | `name` | Var Char (100) | ✓ | — | Display name |
| 2 | `slug` | Var Char (50) | ✓ | ✓ | Identifier, e.g. "LATS" |
| 3 | `description` | Text | — | — | Long description |
| 4 | `bodyRegion` | Var Char (20) | ✓ | — | e.g. UPPER_BODY |
| 5 | `imageUrl` | Var Char (255) | — | — | Optional URL |
| 6 | `parentId` | Foreign Key → `ParentTable` | — | — | FK, On Delete: Null |

> System columns (ROWID, CREATORID, CREATEDTIME, MODIFIEDTIME) are always auto-created.

---

## 3. ZCQL Console

### Access
`Catalyst Console → Data Store → ZCQL Console tab`

> Requires at least one table to exist before the tab is accessible.

### Interface
- **Query editor** with code-completion (auto-suggests table/column names)
- **Execute Query** button
- **Output**: Table View (default) or JSON View (toggle via dropdown)
- **History**: Last 100 executed queries, no time expiry
- **Saved Queries**: Up to 50 per project; searchable via ZCQL Explorer

### What the ZCQL Console can do
- `SELECT` — query and filter data
- `INSERT` — insert rows directly
- `UPDATE` — modify existing rows
- `DELETE` — remove rows
- JOINs, GROUP BY, ORDER BY, LIMIT — all supported

### What it cannot do
- `CREATE TABLE` / `DROP TABLE` — use the Console UI instead
- `ALTER TABLE` — use the Console UI instead
- No transactions / no rollback

---

## 4. SELECT

```sql
SELECT {columns}
FROM {table}
[INNER JOIN | LEFT JOIN {table} ON {condition}]
[WHERE {condition}]
[GROUP BY {column}]
[HAVING {condition}]
[ORDER BY {column} [ASC|DESC]]
[LIMIT {offset},{count}]
```

### Hard limits

| Constraint | Value |
|---|---|
| Max columns per query | 20 |
| **Max rows returned** | **300** |
| Max JOIN clauses | 4 |
| Max ON conditions per JOIN | 1 |
| Max WHERE conditions | 5 |

> Always paginate with LIMIT. 300 is a hard ceiling — no exceptions.

### Column selection

```sql
SELECT * FROM Exercises
SELECT name, difficulty FROM Exercises
SELECT Exercises.name, MuscleGroups.displayName FROM Exercises ...   -- qualified (required when joining)
SELECT DISTINCT difficulty FROM Exercises
```

---

## 5. WHERE

```sql
WHERE column = 'value'
WHERE column != 'value'
WHERE column > 100
WHERE column >= 100
WHERE column < 100
WHERE column <= 100
WHERE column IS NULL
WHERE column IS NOT NULL
WHERE column LIKE 'Bench*'        -- * = zero or more chars
WHERE column LIKE 'B?nch'         -- ? = exactly one char
WHERE column NOT LIKE '*Press'
WHERE column BETWEEN 10 AND 50    -- numeric only
WHERE column IN ('BEGINNER', 'INTERMEDIATE')
WHERE column NOT IN ('ADVANCED')
WHERE cond1 AND cond2
WHERE cond1 OR cond2
-- Subquery (V2 only):
WHERE salary > (SELECT MIN(salary) FROM employees)
```

**Key differences from standard SQL:**
- LIKE wildcards: `*` (not `%`) and `?` (not `_`)
- Single quotes around ALL string values: `WHERE name = 'Pull Up'`
- Boolean columns accept only `TRUE`, `FALSE`, `NULL` — not `'true'`
- Max **5 WHERE conditions** per query
- `IS` / `IS NOT` operators are **only** for NULL checks

### Operator restrictions by type

| Type | Blocked operators |
|---|---|
| Boolean | `>`, `<`, `<=`, `>=`, `LIKE`, `BETWEEN`, `IN` |
| Encrypted Text | All comparisons, `LIKE`, `BETWEEN`, `IN`, aggregate functions |

---

## 6. JOIN

ZCQL V2 supports **INNER JOIN** and **LEFT JOIN** only.

```sql
-- INNER JOIN: rows with matches in both tables
SELECT Exercises.name, MuscleGroups.displayName
FROM Exercises
INNER JOIN MuscleGroups ON Exercises.primaryMuscleId = MuscleGroups.ROWID

-- LEFT JOIN: all parent rows, NULL for unmatched child rows
SELECT Exercises.name, MuscleGroups.displayName
FROM Exercises
LEFT JOIN MuscleGroups ON Exercises.primaryMuscleId = MuscleGroups.ROWID

-- Multiple JOINs (max 4)
SELECT Exercises.name, MuscleGroups.displayName, Equipment.displayName
FROM Exercises
INNER JOIN MuscleGroups ON Exercises.primaryMuscleId = MuscleGroups.ROWID
INNER JOIN Equipment ON Exercises.equipmentId = Equipment.ROWID
WHERE Exercises.CREATORID = 'userId123'
ORDER BY Exercises.name ASC
LIMIT 0,20
```

### JOIN rules
- Must qualify column names as `Table.column` in the SELECT list when joining
- Max **4 JOINs** per query
- Max **1 ON condition** per JOIN (no `ON a = b AND c = d`)
- Only INNER and LEFT — no RIGHT JOIN, no CROSS JOIN

---

## 7. ORDER BY

```sql
ORDER BY name ASC
ORDER BY name DESC
ORDER BY price DESC, name ASC     -- each column can have its own direction
ORDER BY CREATEDTIME DESC         -- system columns work too
```

Default is ascending. ZCQL functions are allowed in ORDER BY (V2).

---

## 8. LIMIT (pagination)

```sql
LIMIT offset, count
LIMIT 0, 20     -- page 1 (rows 1–20)
LIMIT 20, 20    -- page 2 (rows 21–40)
LIMIT 40, 20    -- page 3
```

Offset is **zero-based**.
Formula: `offset = (page - 1) * pageSize`

---

## 9. GROUP BY & HAVING

```sql
SELECT department, COUNT(ROWID)
FROM Employees
GROUP BY department
HAVING COUNT(ROWID) > 5

-- Case-insensitive grouping on text (V2)
SELECT BINARYOF(name), COUNT(ROWID)
FROM Products
GROUP BY BINARYOF(name)
```

`HAVING` always follows `GROUP BY`. It filters aggregated groups (like `WHERE` but after grouping).

---

## 10. Aggregate Functions

```sql
COUNT(column)    -- number of rows
SUM(column)      -- total (numeric columns)
AVG(column)      -- average (Date, DateTime, Boolean in V2)
MIN(column)      -- smallest value
MAX(column)      -- largest value
DISTINCT column  -- deduplicate (use in SELECT)
```

---

## 11. INSERT

```sql
-- All columns (in table-defined order, excluding system columns)
INSERT INTO MuscleGroups VALUES ('LATS', 'Latissimus Dorsi', 'Lats', 'Large flat back muscles', 'UPPER_BODY', '')

-- Specific columns only
INSERT INTO MuscleGroups (slug, displayName, shortName, bodyRegion) VALUES ('LATS', 'Latissimus Dorsi', 'Lats', 'UPPER_BODY')
```

- String values in single quotes
- Do NOT include ROWID, CREATORID, CREATEDTIME, MODIFIEDTIME — Catalyst sets them
- Foreign Key columns take the parent row's ROWID value

---

## 12. UPDATE

```sql
UPDATE Exercises SET difficulty = 'ADVANCED' WHERE ROWID = 12345
UPDATE Exercises SET name = 'Barbell Row', description = 'Compound back exercise' WHERE ROWID = 12345
```

- Multiple columns: comma-separated in SET
- Without WHERE: updates all rows (dangerous)

---

## 13. DELETE

```sql
DELETE FROM Exercises WHERE ROWID = 12345
DELETE FROM Exercises WHERE CREATORID = 'userId123' AND difficulty = 'BEGINNER'
```

- Permanent — no recovery
- Without WHERE: deletes all rows

---

## 14. Kotlin usage in this project

### Always sanitize user input

```kotlin
// No bind params in ZCQL — sanitize all user strings before interpolation
val q = "SELECT * FROM Exercises WHERE CREATORID = '${ZcqlSanitizer.sanitize(userId)}'"
```

### CatalystDataStoreRepository

```kotlin
db.query("SELECT ...")                   // → List<ZCRowObject>
db.queryOne("SELECT ... WHERE ROWID = X") // → ZCRowObject?
db.insert(TABLE, map)                     // → ZCRowObject (extract ROWID for read-after-write)
db.update(TABLE, rowId, map)              // → Unit (MODIFIEDTIME auto-updated)
db.delete(TABLE, rowId)                   // → Unit
db.count(TABLE)                          // → Long
```

### Read-after-write pattern (always do this after insert)

```kotlin
val row = db.insert(TABLE, data)
val rowId = row.get("ROWID")?.toString()?.toLongOrNull()
    ?: row.get("$TABLE.ROWID")?.toString()?.toLongOrNull()
return if (rowId != null) findById(rowId) ?: fallback else fallback
```

### JOIN query pattern

```kotlin
fun buildListQuery(userId: String, page: Int, pageSize: Int): String {
    val conditions = mutableListOf<String>()
    conditions.add("Exercises.CREATORID = '${ZcqlSanitizer.sanitize(userId)}'")
    val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")} "
    return buildString {
        append("SELECT Exercises.ROWID, Exercises.name, Exercises.difficulty, ")
        append("MuscleGroups.displayName AS muscleDisplay, ")
        append("Equipment.displayName AS equipDisplay ")
        append("FROM Exercises ")
        append("INNER JOIN MuscleGroups ON Exercises.primaryMuscleId = MuscleGroups.ROWID ")
        append("INNER JOIN Equipment ON Exercises.equipmentId = Equipment.ROWID ")
        append(where)
        append("ORDER BY Exercises.name ASC ")
        append("LIMIT ${(page - 1) * pageSize},$pageSize")
    }
}
```

### Reading system columns from ZCRowObject

```kotlin
private fun ZCRowObject.toExercise() = Exercise(
    id        = get("ROWID")?.toString()?.toLongOrNull(),
    name      = get("name")?.toString() ?: "",
    // ...
    createdBy = get("CREATORID")?.toString() ?: "",
    createdAt = get("CREATEDTIME")?.toString() ?: "",
    updatedAt = get("MODIFIEDTIME")?.toString() ?: ""
)
```

### ZCQL V2 environment variable (required in function config)

```
ZOHO_CATALYST_ZCQL_PARSER = V2
```

Set this in the AppSail function's environment variables to ensure V2 parser (JOINs, subqueries, etc.).

---

## 15. Gotcha cheatsheet

| # | Gotcha | Correct approach |
|---|---|---|
| 1 | Max **300 rows** returned | Always use `LIMIT offset,pageSize` |
| 2 | LIKE uses `*` and `?` | Not `%` and `_` like standard SQL |
| 3 | Max **5 WHERE conditions** | Combine filters; move extras to in-memory |
| 4 | Max **4 JOINs**, **1 ON each** | Can't compound ON conditions |
| 5 | String values need **single quotes** | `WHERE name = 'Push Up'` |
| 6 | Must **qualify columns** when joining | `Exercises.name` not just `name` |
| 7 | No DDL in ZCQL | Tables created in Catalyst Console UI only |
| 8 | ZCQL Console needs a table to exist | Create at least one table first |
| 9 | INSERT result ROWID key varies | Try `"ROWID"` then `"TableName.ROWID"` |
| 10 | Never write system columns | CREATORID, CREATEDTIME, MODIFIEDTIME are auto-set |
| 11 | Boolean needs `TRUE`/`FALSE` | Not `'true'`/`'false'` strings |
| 12 | `IS`/`IS NOT` only for NULL | Can't use `= NULL` or `!= NULL` |
| 13 | No RIGHT JOIN / CROSS JOIN | Only INNER and LEFT supported |
| 14 | V2 parser must be enabled | Set `ZOHO_CATALYST_ZCQL_PARSER=V2` in env |
