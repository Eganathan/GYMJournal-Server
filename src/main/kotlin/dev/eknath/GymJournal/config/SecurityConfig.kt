package dev.eknath.GymJournal.config

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
    // CORS is intentionally disabled in Spring — ZGS (the gateway in front of AppSail)
    // already adds Access-Control-Allow-Origin. If Spring also adds it, the browser receives
    // duplicate headers and rejects the response. Let ZGS own CORS entirely.
    // OPTIONS preflight requests are permitted without auth so the CORS handshake succeeds.
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
