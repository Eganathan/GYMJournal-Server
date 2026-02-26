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
| API (AppSail) | Development | `https://gymjournal.development.catalystappsail.com` |
| API (AppSail) | Production | `https://gym.eknath.dev` |
| API (AppSail) | Local | `http://localhost:8080` |
| Web Client | Development | `https://gymjournal-778776887.development.catalystserverless.com/app/index.html` |
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
    │   │   ├── application.properties
    │   │   └── app-config.json
    │   └── kotlin/dev/eknath/GymJournal/
    │       ├── GymJournalApplication.kt          # Spring Boot entry point
    │       ├── config/
    │       │   ├── BearerAuthFilter.kt            # Auth via Authorization: Bearer header
    │       │   ├── SessionAuthFilter.kt           # Auth via zcauthtoken cookie (web app)
    │       │   ├── SecurityConfig.kt              # Spring Security filter chains
    │       │   ├── CatalystConfig.kt              # Catalyst SDK setup (per-request init)
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

Auth is handled by **Catalyst ZGS (the gateway)** — not Spring Security directly.

When a request arrives at AppSail, ZGS sits in front and injects user identity headers before forwarding to Spring. `CatalystAuthFilter` uses `CatalystSDK.init(AuthHeaderProvider)` to read those ZGS-injected headers and resolve the authenticated user via `ZCUser.getInstance().currentUser`. The resolved `userId` (Long → String) is stored as the Spring Security principal and retrieved in controllers via `currentUserId()`.

**No manual token parsing.** Spring never reads `Authorization` headers or cookies directly — ZGS handles all of that.

`/api/v1/health` is public — no auth required. All other endpoints require a valid Catalyst user identity injected by ZGS.

#### Web app auth flow

The web app uses `window.catalyst.auth.getJWTAuthToken()` (Catalyst Web SDK 4.0.0) to obtain a JWT token, which is sent with API requests. ZGS validates it and injects user identity headers into the forwarded AppSail request.

### Security Filter Chains

```
Order 1 — publicFilterChain    → matches /api/v1/health, permits all
Order 2 — protectedFilterChain → all other routes:
           OPTIONS preflight → permitted without auth (CORS handshake)
           CatalystAuthFilter → reads ZGS-injected headers → sets SecurityContext
```

### CORS

**CORS is handled entirely by ZGS** — Spring has CORS disabled (`cors { it.disable() }`).
Do not add Spring CORS configuration. To allow a new origin, configure it in the Catalyst console.

`OPTIONS` preflight requests are explicitly permitted without auth in `SecurityConfig` so the CORS handshake succeeds before ZGS processes credentials.

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

All endpoints require auth — either `Authorization: Bearer <token>` or `zcauthtoken` cookie.

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
| userId | String | Catalyst Bearer token string (principal) |
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
Extension function — retrieves the authenticated user's token/ID from the Spring Security context:
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

# Run tests
./gradlew test

# Run with Catalyst CLI (recommended for local dev — sets up Catalyst env)
catalyst serve

# Clean build
./gradlew clean build
```

> **Note:** Local `catalyst serve` does not provide app-level credentials, so `ZCProject` is
> initialized per-request using the user Bearer token. Pass a valid Catalyst user token in the
> `Authorization: Bearer <token>` header when testing protected endpoints locally.

---

## Conventions & Code Style

- **Kotlin idioms**: data classes, extension functions, companion objects, `buildMap {}`.
- **Naming**: `PascalCase` for classes, `camelCase` for functions/properties, `SCREAMING_SNAKE_CASE` for constants.
- **DTOs**: Separate request and response types; validation annotations on request fields (`@Min`, `@NotBlank`).
- **Domain models**: Plain data classes in `model/domain/`; no persistence annotations.
- **No framework ORM**: All DB access is via the Catalyst SDK and raw ZCQL strings.
- **Ownership enforcement**: Services check `entry.userId != userId` before update/delete.
- **Daily goal**: Hardcoded at `2500 ml` (`DEFAULT_DAILY_GOAL_ML` constant in `WaterIntakeService`).
- **Error handling**: `NoSuchElementException` / `IllegalAccessException` thrown from services; Spring Boot default error handling maps these to appropriate HTTP responses.

---

## Deployment (Zoho Catalyst AppSail)

1. Build: `./gradlew build` → produces `build/libs/GymJournal-0.0.1-SNAPSHOT.jar`
2. Deploy via Catalyst CLI or Catalyst console.
3. AppSail injects `X_ZOHO_CATALYST_LISTEN_PORT` — the app reads this via `CatalystPortCustomizer`.
4. In production, `ZCProject.initProject()` (no-arg) could be used for app-level init; currently deferred to per-request user-scoped init.

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
