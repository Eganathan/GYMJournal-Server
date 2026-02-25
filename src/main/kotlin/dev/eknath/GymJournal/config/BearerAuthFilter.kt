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
 * Authenticates requests that carry an Authorization: Bearer <token> header.
 *
 * Resolves the stable Catalyst user ID (Long → String) and stores it as the
 * Spring Security principal, so currentUserId() is consistent across auth methods.
 *
 * Runs before SessionAuthFilter in the security chain.
 */
@Component
class BearerAuthFilter : OncePerRequestFilter() {

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
            val project = ZCProject.initProject(token, APIConstants.ZCUserScope.USER)
            val userId = ZCUser.getInstance(project).getCurrentUser().userId.toString()
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null, emptyList())
        } catch (e: Throwable) {
            // Invalid or expired token — SecurityContext stays empty; Spring Security rejects the request
        }

        chain.doFilter(request, response)
    }
}
