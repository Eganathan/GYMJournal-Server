# Health API

The Health endpoint is a simple liveness check — it confirms the server is running and reachable. Unlike every other endpoint in the API, it requires **no authentication** and is intentionally kept lightweight.

**Base path:** `/api/v1/health`
**Auth required:** No — this is the only public endpoint.

---

## Purpose in the app lifecycle

Zoho Catalyst AppSail containers are shut down after periods of inactivity and take a few seconds to cold-start on the first request. If the mobile or web client makes an authenticated API call while the container is still starting, that call may time out or fail.

**Recommended pattern:** Call `GET /api/v1/health` on app launch (before any authenticated call) and wait for a `200 OK` response. This warms up the container so that subsequent authenticated calls (snapshot, session list, etc.) succeed immediately without a cold-start delay.

```
App launch
  ↓
GET /api/v1/health        ← wait for 200 before proceeding
  ↓
GET /api/v1/metrics/snapshot
GET /api/v1/workouts?status=IN_PROGRESS
... (rest of app load)
```

---

## GET /api/v1/health

**Check server liveness.**

**Auth required:** No

**Request**
```
GET /api/v1/health
```

**Response — 200 OK**

```json
{
  "status": "UP",
  "service": "GymJournal API"
}
```

> **Note:** This endpoint does **not** use the standard `ApiResponse<T>` envelope — it returns a plain JSON object directly. Do not expect `"success": true` or `"data": ...` here.

**Errors**

| Scenario | Behaviour |
|---|---|
| Container still starting | Request may time out or return a connection error — retry after a short delay |
| Container up, but Spring not yet ready | Returns an HTTP error from the platform layer (not a JSON body) |

---

## When to call this endpoint

| Client event | Action |
|---|---|
| App cold launch | Call health first; show a "connecting…" indicator while waiting |
| App foreground resume after long background | Optionally re-ping to confirm the container is still warm |
| Deep-link into a specific screen | Call health first if the app was not already running |
