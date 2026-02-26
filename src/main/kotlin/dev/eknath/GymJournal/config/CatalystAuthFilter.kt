package dev.eknath.GymJournal.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates requests by reading the x-zc-user-id header injected by ZGS.
 * ZGS validates the user's session/token before forwarding to AppSail and only
 * injects this header for authenticated requests — so we can trust it directly.
 * Works for both web (via proxy function) and mobile (direct AppSail call).
 */
@Component
class CatalystAuthFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        // ZGS injects x-zc-user-id for every authenticated request — no SDK needed.
        // This works for both web (via proxy function) and mobile (direct AppSail call).
        val userId = request.getHeader("x-zc-user-id")
        if (userId != null) {
            println("[CatalystAuth] Authenticated user: $userId")
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null, emptyList())
        } else {
            println("[CatalystAuth] No x-zc-user-id — unauthenticated")
        }

        chain.doFilter(request, response)
    }
}
