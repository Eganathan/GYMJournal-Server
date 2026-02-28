package dev.eknath.GymJournal.config

import com.zc.auth.AuthHeaderProvider
import com.zc.auth.CatalystSDK
import com.zc.common.ZCProject
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
 * Authenticates requests by reading the x-zc-user-id header injected by ZGS.
 *
 * ZGS validates the user's session/token before forwarding to AppSail and only
 * injects x-zc-user-id for authenticated requests — so we can trust it directly.
 *
 * SDK context is initialised per-request using CatalystSDK.init(AuthHeaderProvider),
 * which reads the ZGS-injected project and credential headers (project ID, environment,
 * X-ZC-User-Cred-Token, etc.) via the Jakarta HttpServletRequest. This is the correct
 * approach for AppSail with Jakarta servlets (servlet API ≥5). It also works under
 * catalyst serve locally since both ZGS and catalyst serve inject the same headers.
 *
 * Do NOT call ZCProject.initProject(token, USER) with X-ZC-User-Cred-Token — it is
 * a ZGS-internal credential, not a plain Catalyst access token.
 */
@Component
class CatalystAuthFilter : OncePerRequestFilter() {

    companion object {
        private val LOGGER = Logger.getLogger(CatalystAuthFilter::class.java.name)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val userId = request.getHeader("x-zc-user-id")

        if (userId == null) {
            LOGGER.log(Level.INFO, "[CatalystAuth] Unauthenticated request → ${request.requestURI}")
            chain.doFilter(request, response)
            return
        }

        LOGGER.log(Level.INFO, "[CatalystAuth] User $userId → ${request.method} ${request.requestURI}")

        // Initialise the Catalyst SDK using request headers forwarded by ZGS.
        // AuthHeaderProvider bridges the javax.servlet → jakarta.servlet gap.
        // After CatalystSDK.init(), also call ZCProject.initProject(config) so that
        // ZCTable.getInstance() (used for inserts/updates/deletes) gets a non-null project.
        // ZCTable.getInstance() reads from ZCProject's internal singleton, not from
        // CatalystSDK's own state — without this second call it has a null project config.
        try {
            LOGGER.log(Level.INFO, "[CatalystAuth] Initialising Catalyst SDK for user $userId")
            CatalystSDK.init(AuthHeaderProvider { headerName -> request.getHeader(headerName) })
            val config = ZCProject.getDefaultProjectConfig()
            if (config != null) {
                ZCProject.initProject(config)
                LOGGER.log(Level.INFO, "[CatalystAuth] SDK ready — project ${config.projectId} for user $userId")
            } else {
                LOGGER.log(Level.WARNING, "[CatalystAuth] getDefaultProjectConfig() is null after SDK init — write operations may fail for user $userId")
            }
        } catch (e: Throwable) {
            LOGGER.log(Level.SEVERE, "[CatalystAuth] SDK init failed for user $userId: ${e.message}", e)
            // Don't block — DataStore will surface its own error if the SDK is broken.
        }

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        LOGGER.log(Level.INFO, "[CatalystAuth] Request authorised — proceeding for user $userId")
        chain.doFilter(request, response)
    }
}
