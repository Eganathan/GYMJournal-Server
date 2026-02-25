package dev.eknath.GymJournal.config

import com.zc.api.APIConstants
import com.zc.common.ZCProject
import com.zc.component.users.ZCUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates requests that originate from a Catalyst-hosted web app.
 *
 * When a user signs in via Catalyst's web auth, Catalyst sets a session cookie
 * named "zcauthtoken" in the browser. This filter reads that cookie and initialises
 * ZCProject with the same USER-scoped call used by BearerAuthFilter, so the resolved
 * principal (Catalyst user ID) is identical regardless of auth method.
 *
 * This filter runs after BearerAuthFilter and skips if authentication is already set.
 */
@Component
class SessionAuthFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        // Skip if BearerAuthFilter already authenticated this request
        if (SecurityContextHolder.getContext().authentication != null) {
            chain.doFilter(request, response)
            return
        }

        val sessionToken = request.cookies
            ?.find { it.name == CATALYST_SESSION_COOKIE }
            ?.value

        if (sessionToken == null) {
            chain.doFilter(request, response)
            return
        }

        try {
            val project = ZCProject.initProject(sessionToken, APIConstants.ZCUserScope.USER)
            val userId = ZCUser.getInstance(project).getCurrentUser().userId.toString()
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null, emptyList())
        } catch (e: Throwable) {
            // Invalid or expired session — SecurityContext stays empty; Spring Security rejects the request
        }

        chain.doFilter(request, response)
    }

    companion object {
        // Cookie name set by Catalyst AppSail after web authentication
        const val CATALYST_SESSION_COOKIE = "zcauthtoken"
    }
}
