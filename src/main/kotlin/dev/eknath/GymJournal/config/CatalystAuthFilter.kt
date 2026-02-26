package dev.eknath.GymJournal.config

import com.zc.auth.AuthHeaderProvider
import com.zc.auth.CatalystSDK
import com.zc.component.users.ZCUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Bridges the jakarta.servlet / javax.servlet mismatch:
 * CatalystSDK.init(HttpServletRequest) expects javax.servlet, but Spring Boot 3
 * uses jakarta.servlet. AuthHeaderProvider is a plain interface with no servlet
 * dependency, so we wrap the jakarta request with it instead.
 */
private class RequestHeaderProvider(
    private val request: HttpServletRequest
) : AuthHeaderProvider {
    override fun getHeaderValue(key: String): String? = request.getHeader(key)
}

/**
 * Authenticates requests using the Catalyst SDK's native auth mechanism.
 *
 * CatalystSDK.init(provider) lets the SDK read the ZGS-injected headers
 * automatically — no manual token or cookie parsing required.
 */
@Component
class CatalystAuthFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        try {
            CatalystSDK.init(RequestHeaderProvider(request))
            val user = ZCUser.getInstance().currentUser
            if (user != null) {
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(user.userId.toString(), null, emptyList())
            }
        } catch (e: Throwable) {
            // No valid auth context — SecurityContext stays empty; Spring Security rejects the request
        }

        chain.doFilter(request, response)
    }
}
