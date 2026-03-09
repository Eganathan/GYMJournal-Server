package dev.eknath.GymJournal.config

import com.zc.auth.CatalystSDK
import com.zc.component.users.ZCUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Per-request Catalyst SDK init + user identity resolution.
 *
 * Must run at HIGHEST_PRECEDENCE so the SDK context is ready before any
 * other filter or interceptor.
 *
 * Correct order (confirmed from production — croptor-catalyst-app):
 *  1. CatalystSDK.init(AuthHeaderProvider)          — SDK context from ZGS headers
 *  2. ZCUser.getInstance().getCurrentUser()          — resolve user BEFORE any initProject
 *  3. Store userId in Spring Security context        — services read from here
 *  4. ZCProject.initProject per DataStore operation  — called in CatalystDataStoreRepository
 *
 * CRITICAL: ZCProject.initProject() resets the SDK context and kills the user
 * session. getCurrentUser() MUST be called before any initProject() call.
 *
 * Cross-domain fallback: if getCurrentUser() returns null (known issue when
 * the frontend is on catalystserverless.com and the API is on catalystappsail.com —
 * the session cookie is not forwarded cross-domain), fall back to X-Catalyst-Uid
 * header sent by the client from getCurrentProjectUser(). Reported to Zoho support.
 */
@Component
class CatalystAuthFilter : OncePerRequestFilter() {

    companion object {
        private val LOGGER = Logger.getLogger(CatalystAuthFilter::class.java.name)
        private const val PUBLIC_PATH = "/api/v1/health"
    }

    /** Skip auth for the health/warmup endpoint. */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI == PUBLIC_PATH

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        var userId: Long? = null

        try {
            // Step 1: init SDK context from ZGS-injected headers
            CatalystSDK.init { headerName -> request.getHeader(headerName) }

            // Step 2: resolve current user IMMEDIATELY after SDK init.
            // MUST come before any ZCProject.initProject() — initProject resets
            // the SDK context and causes getCurrentUser() to return null.
            userId = ZCUser.getInstance().getCurrentUser()?.userId
            LOGGER.log(Level.INFO, "[CatalystAuth] SDK init done — currentUser: $userId")

        } catch (e: Throwable) {
            LOGGER.log(Level.SEVERE, "[CatalystAuth] SDK init / getCurrentUser failed: ${e.message}", e)
        }

        // Diagnostic: compare SDK-resolved ID with client-supplied X-Catalyst-Uid.
        // X-Catalyst-Uid is sent by the client from getCurrentProjectUser() as a
        // cross-domain workaround (session cookie not forwarded to AppSail domain).
        val clientUserId = request.getHeader("X-Catalyst-Uid") ?: "not-sent"
        LOGGER.log(
            Level.INFO,
            "[CatalystAuth] SDK currentUser: ${userId ?: "null"} | X-Catalyst-Uid (client): $clientUserId | match: ${userId?.toString() == clientUserId}"
        )

        // Cross-domain fallback: use X-Catalyst-Uid when SDK cannot resolve the user
        if (userId == null) {
            userId = clientUserId.toLongOrNull()
        }

        if (userId == null) {
            LOGGER.log(Level.WARNING, "[CatalystAuth] Could not resolve user — rejecting ${request.requestURI}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unable to resolve authenticated user")
            return
        }

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())

        LOGGER.log(Level.INFO, "[CatalystAuth] Authorised — user $userId → ${request.method} ${request.requestURI}")
        chain.doFilter(request, response)
    }
}
