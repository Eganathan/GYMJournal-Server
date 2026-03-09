package dev.eknath.GymJournal.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(private val catalystAuthFilter: CatalystAuthFilter) {

    /**
     * Disable Spring Boot's automatic servlet-filter registration of CatalystAuthFilter.
     *
     * When a @Component filter is present Spring Boot registers it as a standalone servlet
     * filter (outside the Spring Security FilterChainProxy). Because CatalystAuthFilter is
     * also added to the security chain via addFilterBefore, it would run twice — but
     * OncePerRequestFilter prevents the second execution. The result is that the first
     * (standalone) run sets the SecurityContext, then Spring Security's
     * SecurityContextHolderFilter overwrites it with a fresh empty context, and the
     * authorisation check sees no auth → 403.
     *
     * By disabling auto-registration here the filter runs ONLY inside the Spring Security
     * chain where it sets auth on the correct SecurityContext.
     */
    @Bean
    fun catalystAuthFilterRegistration(filter: CatalystAuthFilter): FilterRegistrationBean<CatalystAuthFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    // Public API endpoint — health check, no auth required
    @Bean
    @Order(1)
    fun publicApiChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/api/v1/health")
            .csrf { it.disable() }
            .cors { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }

    // Protected API endpoints — CatalystAuthFilter reads x-zc-user-id injected by ZGS.
    // CORS is handled by ZGS — Spring must not add its own CORS headers or they will
    // duplicate ZGS's headers and cause the browser to reject them.
    @Bean
    @Order(2)
    fun protectedApiChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/api/**")
            .csrf { it.disable() }
            .cors { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(catalystAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }
}
