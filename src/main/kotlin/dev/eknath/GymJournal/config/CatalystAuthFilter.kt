package dev.eknath.GymJournal.config

import com.zc.api.APIConstants
import com.zc.common.ZCProject
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class CatalystAuthFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response)
            return
        }

        val token = authHeader.removePrefix("Bearer ").trim()

        try {
            // Initialize a user-scoped project to validate the Catalyst auth token.
            // The token string is used as the key to retrieve this project later.
            ZCProject.initProject(token, APIConstants.ZCUserScope.USER)

            // Store the token as principal — controllers call currentUserId() to retrieve it.
            // For a single-user personal app this is sufficient; expand to fetch userId from
            // Catalyst user details if multi-user support is needed later.
            val auth = UsernamePasswordAuthenticationToken(token, token, emptyList())
            SecurityContextHolder.getContext().authentication = auth
        } catch (e: Throwable) {
            // Invalid or expired token — Spring Security will reject the protected request
        }

        chain.doFilter(request, response)
    }
}
