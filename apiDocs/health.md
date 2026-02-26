# Health API

## GET /api/v1/health

Checks whether the server is running. Use this to warm up the AppSail container on app launch.

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

> Note: This endpoint does not use the standard `ApiResponse<T>` envelope — it returns the map directly.
