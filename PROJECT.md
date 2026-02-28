# GymJournal Server — Project Documentation

## Overview

**GymJournal** is a Kotlin/Spring Boot backend API for a personal gym and fitness journaling
application. It is designed to run on **Zoho Catalyst AppSail** (a serverless Java hosting platform)
and uses **Zoho Catalyst DataStore** as its database. The app currently implements hydration
(water intake) tracking and is structured to grow into a full gym journaling platform.

---

## Base URLs

| Service | Environment | URL |
|---|---|---|
| API (AppSail) | Development | `https://appsail-10119736618.development.catalystappsail.com` |
| API (AppSail) | Production | `https://gym.eknath.dev` |
| API (AppSail) | Local | `http://localhost:8080` |
| Web Client | Development | `https://gymjournal-778776887.development.catalystserverless.com` |
| Web Client | Production | `https://app.gym.eknath.dev` |

## API Documentation

Detailed REST API docs (request/response shapes, params, error codes) live in [`apiDocs/`](./apiDocs/index.md):

| File | Coverage |
|---|---|
| [`apiDocs/health.md`](./apiDocs/health.md) | `GET /api/v1/health` |
| [`apiDocs/hydration.md`](./apiDocs/hydration.md) | All `/api/v1/water` endpoints |

When adding a new module, add a corresponding `.md` file in `apiDocs/` and link it here.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.3.10 |
| Framework | Spring Boot 4.1.0-SNAPSHOT |
| Security | Spring Security (stateless, ZGS-injected headers via CatalystSDK) |
| Database | Zoho Catalyst DataStore (queried via ZCQL) |
| DB SDK | Zoho Catalyst Java SDK 2.2.0 (bundled JARs in `libs/`) |
| Build | Gradle with Kotlin DSL |
| JVM | Java 17 |
| Testing | JUnit 5 |
| Deployment | Zoho Catalyst AppSail |

---

## Project Structure

```
GymJournal/Server/
├── build.gradle.kts                  # Gradle build config
├── settings.gradle.kts               # Root project name
├── libs/                             # Catalyst SDK JARs (local file dependencies)
├── app-config.json                   # Catalyst app configuration
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.properties
    │   └── kotlin/dev/eknath/GymJournal/
    │       ├── GymJournalApplication.kt          # Spring Boot entry point
    │       ├── config/
    │       │   ├── CatalystAuthFilter.kt          # Auth: reads x-zc-user-id injected by ZGS
    │       │   ├── SecurityConfig.kt              # Spring Security filter chains
    │       │   ├── CatalystConfig.kt              # Catalyst SDK notes (no startup init)
    │       │   └── CatalystPortCustomizer.kt      # Reads port from env var
    │       ├── repository/
    │       │   └── CatalystDataStoreRepository.kt # Generic DataStore CRUD wrapper
    │       ├── model/
    │       │   ├── domain/
    │       │   │   └── WaterIntake.kt             # Domain entities
    │       │   └── dto/
    │       │       └── WaterIntakeDtos.kt         # Request/response DTOs
    │       ├── util/
    │       │   ├── ApiResponse.kt                 # Unified response envelope
    │       │   ├── GlobalExceptionHandler.kt      # @RestControllerAdvice — maps exceptions to ApiResponse
    │       │   ├── ZcqlSanitizer.kt               # ZCQL injection prevention
    │       │   └── SecurityContextExtensions.kt   # currentUserId() helper
    │       └── modules/
    │           ├── health/
    │           │   └── HealthController.kt        # Public health-check endpoint
    │           └── hydration/
    │               ├── WaterIntakeController.kt   # REST endpoints
    │               ├── WaterIntakeService.kt      # Business logic
    │               └── WaterIntakeRepository.kt   # DataStore queries
    └── test/
        └── kotlin/dev/eknath/GymJournal/
            └── GymJournalApplicationTests.kt
```

---

## Architecture

### Module Pattern

Each feature is a self-contained **module** under `modules/`, following a layered pattern:

```
Controller  →  Service  →  Module Repository  →  CatalystDataStoreRepository (generic)
```

- **Controller**: Handles HTTP, validation, extracts `currentUserId()`, returns `ApiResponse<T>`.
- **Service**: Business logic — computes summaries, enforces ownership, defaults dates/times.
- **Repository**: Module-specific DataStore queries using ZCQL (sanitized via `ZcqlSanitizer`).
- **`CatalystDataStoreRepository`**: Generic base — `query`, `queryOne`, `insert`, `update`, `delete`, `count`.

### Authentication

Auth is handled by **`CatalystAuthFilter`** which runs on every protected request:

1. **User identity** — reads `x-zc-user-id` header injected by ZGS (trusted, no SDK call needed). Stored as the Spring Security principal; accessible via `currentUserId()`.
2. **SDK init** — calls `CatalystSDK.init(AuthHeaderProvider)` which reads ZGS-injected project/credential headers to give the DataStore SDK a request context. Then calls `ZCProject.initProject(config)` with the default project config so `ZCTable.getInstance()` has a non-null project.

If `x-zc-user-id` is absent → unauthenticated (chain continues, Spring Security rejects).
If SDK init throws → logged as warning; DataStore will surface its own error downstream.

`/api/v1/health` is public — no auth required. All other endpoints require `x-zc-user-id` to be present.

#### Web app auth flow

The web client (on Catalyst Serverless) authenticates via the `zcauthtoken` cookie set by Catalyst's web auth. ZGS validates the cookie and injects `x-zc-user-id` into the forwarded AppSail request. `ZD_CSRF_TOKEN` / `X-ZCSRF-TOKEN` are handled by the Catalyst web SDK.

### Security Filter Chains

```
Order 1 — publicApiChain    → matches /api/v1/health, permits all
Order 2 — protectedApiChain → matches /api/**, stateless, CatalystAuthFilter, requires auth
```

### CORS

CORS is handled entirely by **ZGS** (the gateway in front of AppSail) — not by Spring. Spring CORS is explicitly disabled to prevent duplicate `Access-Control-Allow-Origin` headers (ZGS adds its own; if Spring also adds one the browser rejects the response). To allow a new web client origin, configure it in the Catalyst Console under the AppSail service CORS settings.

### Database (Catalyst DataStore)

- Zoho Catalyst DataStore is a NoSQL-like store queried with **ZCQL** (a SQL subset).
- There are **no bind parameters** in ZCQL — all user input is sanitized manually via
  `ZcqlSanitizer` (escapes single quotes, strips semicolons).
- `ZCProject` is initialized per-request (not at startup) to support local development with
  `catalyst serve`.

---

## API Endpoints

### Health

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/health` | None | Health check; returns `{ status: "UP" }` |

### Water Intake (Hydration)

All endpoints require auth — `zcauthtoken` cookie (validated by ZGS, which injects `x-zc-user-id`).

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/water` | Log a water intake entry |
| GET | `/api/v1/water/today` | Today's summary (total ml, goal, entries) |
| GET | `/api/v1/water/daily?date=YYYY-MM-DD` | Specific day summary |
| GET | `/api/v1/water/history?startDate=...&endDate=...` | Date range daily totals |
| PUT | `/api/v1/water/{id}` | Update an existing entry |
| DELETE | `/api/v1/water/{id}` | Delete an entry |

#### POST /api/v1/water — Request Body
```json
{
  "amountMl": 250,
  "logDateTime": "2025-01-15T08:30:00",   // optional, defaults to now
  "notes": "Morning glass"                 // optional
}
```

#### Daily Summary Response
```json
{
  "success": true,
  "data": {
    "date": "2025-01-15",
    "totalMl": 1500,
    "goalMl": 2500,
    "progressPercent": 60,
    "entries": [...]
  }
}
```

---

## DataStore Tables

### WaterIntakeLogs

| Column | Type | Notes |
|---|---|---|
| ROWID | Long | Auto-generated by Catalyst |
| userId | String | Catalyst user ID (from x-zc-user-id) |
| logDateTime | String | ISO-8601 datetime: `2025-01-15T08:30:00` |
| amountMl | Int | Volume in millilitres (min 1) |
| notes | String | Optional free text |

---

## Key Utilities

### `ApiResponse<T>`
Unified response envelope for all endpoints:
```kotlin
ApiResponse.ok(data)                      // { success: true, data: ... }
ApiResponse.error("CODE", "message")      // { success: false, error: { code, message } }
```

### `ZcqlSanitizer`
Prevents ZCQL injection since prepared statements are not supported:
```kotlin
ZcqlSanitizer.sanitize(userInput)   // escapes ' → '' and strips ;
```

### `currentUserId()`
Extension function — retrieves the authenticated user's ID from the Spring Security context:
```kotlin
val userId = currentUserId()
```

### `CatalystPortCustomizer`
Reads `X_ZOHO_CATALYST_LISTEN_PORT` env var (set by Catalyst AppSail at runtime); falls back to `8080` locally.

---

## Development Commands

```bash
# Build the project
./gradlew build

# Run locally (standalone Spring Boot)
./gradlew bootRun

# Run with Catalyst CLI (recommended for local dev — sets up Catalyst env)
catalyst serve

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```

---

## Conventions & Code Style

- **Kotlin idioms**: data classes, extension functions, companion objects, `buildMap {}`.
- **Naming**: `PascalCase` for classes, `camelCase` for functions/properties, `SCREAMING_SNAKE_CASE` for constants.
- **DTOs**: Separate request and response types; validation annotations on request fields (`@Min`, `@NotBlank`).
- **Domain models**: Plain data classes in `model/domain/`; no persistence annotations.
- **No framework ORM**: All DB access is via the Catalyst SDK and raw ZCQL strings.
- **Ownership enforcement**: Services check `entry.userId != userId` before update/delete.
- **Daily goal**: Hardcoded at `2500 ml` (`DEFAULT_DAILY_GOAL_ML` constant in `WaterIntakeService`).
- **Error handling**: Centralised in `GlobalExceptionHandler` (`util/GlobalExceptionHandler.kt`). Maps `HttpMessageNotReadableException` → 400, `MethodArgumentNotValidException` → 400, `NoSuchElementException` → 404, `IllegalAccessException` → 403. All error responses use the `ApiResponse` envelope.

---

## Deployment (Zoho Catalyst AppSail)

1. Build: `./gradlew build` → produces `build/libs/GymJournal-0.0.1-SNAPSHOT.jar`
2. Deploy via Catalyst CLI or Catalyst console.
3. AppSail injects `X_ZOHO_CATALYST_LISTEN_PORT` — the app reads this via `CatalystPortCustomizer`.
4. `ZCProject` is initialised per-request in `CatalystAuthFilter`. The no-arg `initProject()` does **not** work in AppSail — AppSail does not provide a `catalyst-config.json` at runtime.

---

## Extending the Project

To add a new feature module:

1. Create `src/main/kotlin/.../modules/<feature>/` directory.
2. Add a domain model in `model/domain/`.
3. Add DTOs in `model/dto/`.
4. Create `<Feature>Repository` using `CatalystDataStoreRepository`.
5. Create `<Feature>Service` with business logic.
6. Create `<Feature>Controller` with `@RestController` and `@RequestMapping("/api/v1/<feature>")`.
7. Add the DataStore table in the Catalyst console with matching column names.
