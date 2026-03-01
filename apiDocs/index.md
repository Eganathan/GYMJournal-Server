# GymJournal API Documentation

> This is the reference for the GymJournal REST API. All endpoints are served by the Spring Boot backend deployed on Zoho Catalyst AppSail.

---

## Base URLs

| Environment | URL |
|---|---|
| Development (AppSail) | `https://appsail-10119736618.development.catalystappsail.com` |
| Local | `http://localhost:8080` |

---

## Authentication

All endpoints **except** `GET /api/v1/health` require the user to be authenticated via Catalyst ZGS (Zoho Gateway Services).

The web client uses **Catalyst session cookies** — the browser sends the `zcauthtoken` cookie automatically after the user logs in via the Catalyst-hosted login page. No extra work is needed on the API call side as long as `credentials: 'include'` is set on fetch requests.

Mobile or third-party clients use a **Bearer token**:
```
Authorization: Bearer <catalyst_access_token>
```

Unauthenticated requests to protected endpoints return `401 Unauthorized`.

> **Important:** The backend never handles login itself. Login is owned entirely by Catalyst's auth layer. The backend only trusts the `x-zc-user-id` header that ZGS injects into every authenticated request.

---

## Response Envelope

Every endpoint (except `GET /api/v1/health`) returns a consistent JSON wrapper:

```json
// Success
{
  "success": true,
  "data": { ... }        // object, array, or null (204 responses have no body)
}

// Error
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable description of what went wrong"
  }
}
```

### Standard error codes

| HTTP Status | Code | When it happens |
|---|---|---|
| 400 | `VALIDATION_ERROR` | A required field is missing or fails `@NotBlank` / `@NotEmpty` validation |
| 400 | `INVALID_REQUEST` | Malformed JSON body, invalid date format, or business rule violation (e.g. submitting a computed metric type) |
| 403 | `FORBIDDEN` | The resource exists but belongs to a different user |
| 404 | `NOT_FOUND` | The resource ID does not exist |

---

## Modules

| File | Covers |
|---|---|
| [health.md](./health.md) | `GET /api/v1/health` — liveness check |
| [hydration.md](./hydration.md) | Water intake logging, daily summaries, history |
| [exercises.md](./exercises.md) | Community exercise library, muscle groups, equipment |
| [metrics.md](./metrics.md) | Body metrics logging, snapshot, history, custom types, health insights |
| [routines.md](./routines.md) | Routine templates — create, browse, clone public routines |
| [workouts.md](./workouts.md) | Workout session logging — start/complete sessions, log sets, PBs, history |

---

## Pagination (exercises, routines, sessions)

List responses that support pagination include a `meta` block:

```json
{
  "success": true,
  "data": [ ... ],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 87
  }
}
```

---

## Data ownership

All user-generated records (water entries, exercises, metric entries, routines, sessions) are owned by the user who created them. The backend enforces this:

- Any authenticated user can **read** public data (exercises are public; routines support `isPublic`; water, metrics, and workout sessions are private).
- Only the **creator** can update or delete their own records — other users receive `403 FORBIDDEN`.
