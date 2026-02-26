# GymJournal Server — Project Documentation

## Overview

**GymJournal** is a Kotlin/Spring Boot backend API for a personal gym and fitness journaling
application. It is designed to run on **Zoho Catalyst AppSail** (a serverless Java hosting platform)
and uses **Zoho Catalyst DataStore** as its database. The app currently implements hydration
(water intake) tracking and is structured to grow into a full gym journaling platform.

---

## Base URLs

| Environment | URL |
|---|---|
| Development (AppSail) | `https://gymjournal.development.catalystappsail.com` |
| Local | `http://localhost:8080` |

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
| Security | Spring Security (stateless, Bearer token + session cookie) |
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

Completely stateless — no Spring session, no JWT. Two auth paths are supported and produce
the **same principal** (Catalyst user ID as a String stored via `currentUserId()`):

| Path | Client | Mechanism | Works cross-domain? |
|---|---|---|---|
| `Authorization: Bearer <token>` | Mobile / Web / API | `BearerAuthFilter` | Yes |
| `zcauthtoken` cookie | Web app on **same AppSail instance** only | `SessionAuthFilter` | No |

> **Important — session cookie limitation**: The `zcauthtoken` cookie is scoped to the domain
> it was set on. Since the web app (`gymjournal-778776887.development.catalystserverless.com`)
> and the API (`gymjournal.development.catalystappsail.com`) are on **different domains**, the
> browser will never send the cookie to the API. **The web app must use Bearer token auth.**
> Only `ZD_CSRF_TOKEN` crosses the domain boundary — that is not an auth token.

Both filters call `ZCProject.initProject(token, USER)` then resolve the stable Catalyst numeric
user ID via `ZCUser.getInstance(project).getCurrentUser().getUserId()`. This ID is stored as the
Spring Security principal and written to every DataStore row as `userId`.

`BearerAuthFilter` runs first. `SessionAuthFilter` skips if authentication is already set.
`/api/v1/health` is public — no auth required.

#### Web app — how to get the Bearer token (Catalyst JS SDK)

```js
const token = await catalyst.auth.getToken()

// Attach to every API request
fetch(`${API_BASE}/api/v1/water/today`, {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
})
```

### Security Filter Chains

```
Order 1 — publicFilterChain    → matches /api/v1/health, permits all
Order 2 — protectedFilterChain → all other routes:
           BearerAuthFilter → SessionAuthFilter → UsernamePasswordAuthenticationFilter → ...
```

### CORS

Configured in `SecurityConfig.corsConfigurationSource()`, applied to `/api/**` on both filter chains.

| Setting | Value |
|---|---|
| Allowed origins | `https://gymjournal-778776887.development.catalystserverless.com`, `http://localhost:3000`, `http://localhost:5173` |
| Allowed methods | GET, POST, PUT, DELETE, OPTIONS |
| Allowed headers | `Authorization`, `Content-Type`, `Accept` |
| Allow credentials | `true` — required for both auth flows |

> To add a new allowed origin, update `corsConfigurationSource()` in `SecurityConfig.kt`.

#### What client developers must do

**Bearer token (mobile / API clients)**

Every protected request must include the `Authorization` header. The header is not forwarded or injected by the server — the client is fully responsible for attaching it:

```http
Authorization: Bearer <catalyst_token>
```

```js
// fetch
fetch(url, {
  headers: { 'Authorization': `Bearer ${token}` }
})

// axios
axios.get(url, {
  headers: { Authorization: `Bearer ${token}` }
})
```

**Session cookie (web app) — does not work cross-domain**

The `zcauthtoken` cookie is scoped to the serverless domain and will not be sent to the AppSail API domain. The web app must use Bearer token instead (see above).

**Headers NOT set by the server** — clients must set these themselves:

| Header | Required by | Notes |
|---|---|---|
| `Authorization` | Bearer token flow | Must be attached manually on every protected request |
| `Content-Type: application/json` | POST / PUT requests | Must be set when sending a JSON body |

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
