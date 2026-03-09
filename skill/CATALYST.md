# Catalyst SDK — Correct Usage Patterns (Validated in Production)

Source: Croptor production app (`croptor-catalyst-app`) running on Catalyst AppSail.

---

## 1. Filter Chain — Correct Order

Three separate filters, in this execution order:

```
HTTP Request
    ↓
CatalystSDKFilter  @Order(HIGHEST_PRECEDENCE)
    → CatalystSDK.init(AuthHeaderProvider)   ← SDK init only, nothing else
    ↓
UserFilter  (default order)
    → ZCUser.getInstance().getCurrentUser()  ← AFTER SDK init, BEFORE any ZCProject.initProject()
    → store user in RequestContextHolder (SCOPE_REQUEST)
    → return 401 if user null
    ↓
Controller / Service
    → read user from RequestContextHolder
    → call ZCProject.initProject(adminRole, ADMIN) per DataStore operation
```

**Critical rule**: `ZCUser.getInstance().getCurrentUser()` MUST be called BEFORE any
`ZCProject.initProject()`. Calling `initProject` first resets the SDK context and
kills the user session — `getCurrentUser()` returns null.

---

## 2. CatalystSDKFilter

```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CatalystSDKFilter : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return !uri.startsWith("/api/v1/") || uri == "/api/v1/health"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        CatalystSDK.init { headerName -> request.getHeader(headerName) }
        chain.doFilter(request, response)
    }
}
```

- **Only** does `CatalystSDK.init`. Nothing else.
- No `ZCProject.initProject` here.
- No user resolution here.

---

## 3. UserFilter

```kotlin
@Component
class UserFilter : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return !uri.startsWith("/api/v1/") ||
               uri == "/api/v1/health" ||
               uri.startsWith("/api/v1/admin")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        try {
            // SDK already initialised by CatalystSDKFilter
            // Call getCurrentUser() BEFORE any ZCProject.initProject()
            val zcUser = ZCUser.getInstance().getCurrentUser()
                ?: throw IllegalStateException("getCurrentUser() returned null")

            val userId = zcUser.userId
                ?: throw IllegalStateException("userId is null")

            // Store in request scope — all downstream code reads from here
            val attrs = RequestContextHolder.currentRequestAttributes()
            attrs.setAttribute(CURRENT_USER_ATTR, userId, RequestAttributes.SCOPE_REQUEST)

            chain.doFilter(request, response)
        } catch (e: Exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unable to resolve authenticated user")
        }
    }

    companion object {
        const val CURRENT_USER_ATTR = "currentUserId"
    }
}
```

---

## 4. Accessing Current User in Services

**Never** call `ZCUser.getInstance().getCurrentUser()` in a service or repository.
By the time a service runs, `ZCProject.initProject()` will have been called for
DataStore operations, resetting the SDK context. Read from request attributes instead:

```kotlin
fun currentUserId(): Long {
    val attrs = RequestContextHolder.currentRequestAttributes()
    return attrs.getAttribute(UserFilter.CURRENT_USER_ATTR, RequestAttributes.SCOPE_REQUEST) as? Long
        ?: error("No authenticated user in request context")
}
```

---

## 5. DataStore Operations — ZCProject.initProject per operation

`ZCProject.initProject()` is called **per DataStore operation**, not once in the
filter. In the Croptor production app, every table access calls:

```java
ZCProject.initProject(adminRole, APIConstants.ZCUserScope.ADMIN)
```

where `adminRole` is the admin API key/token from an environment variable.

For our project (using the default service account credentials via ZGS headers),
`ZCProject.initProject(config)` with `ZCProject.getDefaultProjectConfig()` is
called in the service/repository layer, not in the auth filter.

**Key**: `initProject` can be called multiple times per request without issue —
each call sets up the context for the operation that follows it.

---

## 6. ZCProject.initProject — Correct Overloads

```java
// With admin token + scope (Croptor pattern — full admin access)
ZCProject.initProject(String adminToken, APIConstants.ZCUserScope.ADMIN)

// With ZCProjectConfig (our pattern — uses ZGS service account credentials)
ZCProject.initProject(ZCProjectConfig config)

// With explicit development environment
ZCProjectConfig config = ZCProjectConfig.newBuilder()
    .setProjectId(defaultConfig.getProjectId())
    .setProjectKey(defaultConfig.getProjectKey())
    .setProjectDomain(defaultConfig.getProjectDomain())
    .setZcAuth(defaultConfig.getZcAuth())
    .setEnvironment("Development")   // "Development" or "Production"
    .build();
ZCProject.initProject(config);
```

`getDefaultProjectConfig()` defaults to **Production** environment.
Always explicitly set `"Development"` when running in the development AppSail.

---

## 7. Cross-Domain Auth Issue (Known)

**Setup**: React frontend on `catalystserverless.com`, API on `catalystappsail.com`.

**Problem**: ZGS session cookie is scoped to `catalystserverless.com`.
Cross-domain requests to AppSail don't carry the session cookie, so
`ZCUser.getInstance().getCurrentUser()` returns null even after `CatalystSDK.init`.

**Evidence**:
- `getCurrentProjectUser()` on the client returns unique `user_id` per user ✓
- Server logs: `SDK currentUser: null` for all users ✗

**Current workaround**: Client sends `X-Catalyst-Uid` header (from `getCurrentProjectUser()`).
Server falls back to this header if `getCurrentUser()` returns null.

**Status**: Reported to Zoho developer support — awaiting platform guidance.

**Long-term fix**: Either
1. Serve frontend from AppSail (same domain — cookies work), or
2. Use Bearer token auth (`Authorization: Zoho-oauthtoken <token>`) instead of cookies.

---

## 8. Port Configuration

AppSail injects `X_ZOHO_CATALYST_LISTEN_PORT` at runtime.
Read it in `main()`:

```kotlin
val port = System.getenv("X_ZOHO_CATALYST_LISTEN_PORT") ?: "8080"
```

Or use the existing `CatalystPortCustomizer` bean in this project.

---

## 9. Health Endpoint

`/api/v1/health` must be excluded from ALL auth filters via `shouldNotFilter()`.
It is the only public endpoint and is used by AppSail warmup checks.

---

## 10. Admin Endpoints

Admin endpoints (`/api/v1/admin/*`) bypass `UserFilter` and are protected by
a separate `APIKeyFilter` that validates a `C-API-Key` header against the
`C_API_KEY` environment variable.
