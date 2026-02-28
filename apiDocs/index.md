# GymJournal API Documentation

**Base URL (Development):** `https://gymjournal.development.catalystappsail.com`
**Base URL (Local):** `http://localhost:8080`

## Authentication

All endpoints except `/api/v1/health` require authentication via one of:

| Method | Header / Cookie | Client |
|---|---|---|
| Bearer token | `Authorization: Bearer <catalyst_token>` | Mobile / API |
| Session cookie | `zcauthtoken=<value>` (set automatically by Catalyst web auth) | Web app |

Unauthenticated requests to protected endpoints return `401 Unauthorized`.

## Response Envelope

All endpoints return a consistent JSON envelope:

```json
// Success
{
  "success": true,
  "data": { ... }
}

// Error
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message"
  }
}
```

## Modules

| File | Endpoints |
|---|---|
| [health.md](./health.md) | `GET /api/v1/health` |
| [hydration.md](./hydration.md) | `POST /api/v1/water`, `GET /api/v1/water/today`, etc. |
| [exercises.md](./exercises.md) | `GET/POST /api/v1/exercises`, categories, equipment, `POST /api/v1/admin/seed` |
