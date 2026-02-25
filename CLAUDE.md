# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Memory

`PROJECT.md` in the repository root is the **primary memory** for this project. After any change that affects architecture, API endpoints, DataStore tables, module structure, conventions, or deployment — update `PROJECT.md` to reflect the current state. Future Claude instances will use it as the authoritative reference for how the project works.

## Commands

```bash
./gradlew build          # Compile and package
./gradlew bootRun        # Run locally on port 8080
./gradlew test           # Run all tests
./gradlew test --tests "dev.eknath.GymJournal.SomeTest"  # Run a single test class
catalyst serve           # Run via Catalyst CLI (preferred for local dev — sets up Catalyst env)
```

## Architecture

This is a **Kotlin/Spring Boot** REST API backend deployed on **Zoho Catalyst AppSail**. The database is **Zoho Catalyst DataStore**, a NoSQL store queried via ZCQL (a SQL subset). There is no ORM — all DB access goes through `CatalystDataStoreRepository`.

### Module pattern

Features live under `modules/` and follow a strict layered pattern:

```
Controller → Service → Module Repository → CatalystDataStoreRepository (generic base)
```

To add a new feature: create the module directory, add domain model in `model/domain/`, DTOs in `model/dto/`, then the Repository/Service/Controller. Add the matching table in the Catalyst console.

### Authentication

Completely stateless. Two auth paths both resolve to the same principal — the Catalyst numeric user ID (as a String) stored via `currentUserId()`:

- **`BearerAuthFilter`** — reads `Authorization: Bearer <token>` header (mobile / API clients)
- **`SessionAuthFilter`** — reads `zcauthtoken` cookie set by Catalyst after web login (AppSail web app)

Both call `ZCProject.initProject(token, USER)` then fetch the user ID via `ZCUser.getInstance(project).getCurrentUser().getUserId()`. `BearerAuthFilter` runs first; `SessionAuthFilter` skips if auth is already set. `/api/v1/health` is the only public endpoint.

### Catalyst DataStore / ZCQL

- **No bind parameters** — all user-supplied strings must be sanitized with `ZcqlSanitizer.sanitize()` before interpolation into ZCQL queries.
- `ZCProject` is initialized per-request (not at app startup) to support local `catalyst serve` dev, where app-level credentials are unavailable.
- `CatalystDataStoreRepository` provides `query`, `queryOne`, `insert`, `update`, `delete`, and `count`. Module repositories wrap this with table-specific mappers.
- `com.zc.component.object` must be backtick-escaped in Kotlin imports (`\`object\``) because `object` is a keyword.

### Catalyst SDK JARs

The SDK lives in `libs/` as local file dependencies. Three JARs are explicitly excluded in `build.gradle.kts` to avoid conflicts with Spring Boot 4.x: `servlet-api`, `slf4j-api`, and `jackson-*` (Catalyst bundles Jackson 2.7.x; Spring Boot 4.x uses Jackson 3.x).

### Key shared utilities

- `ApiResponse<T>` — wrap all controller responses with `ApiResponse.ok(data)` or `ApiResponse.error("CODE", "message")`.
- `ZcqlSanitizer` — always use before string interpolation into ZCQL.
- `currentUserId()` — retrieves the authenticated Catalyst user ID from the security context (same value regardless of Bearer or session auth).
- `CatalystPortCustomizer` — reads `X_ZOHO_CATALYST_LISTEN_PORT` env var (set by AppSail at runtime); defaults to `8080` locally.
