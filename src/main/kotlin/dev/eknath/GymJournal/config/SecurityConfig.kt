package dev.eknath.GymJournal.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val bearerAuthFilter: BearerAuthFilter,
    private val sessionAuthFilter: SessionAuthFilter
) {

    // Public endpoints — no auth required
    @Bean
    @Order(1)
    fun publicFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/api/v1/health")
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }

    // All other endpoints — require a valid Catalyst identity via either:
    //   1. Authorization: Bearer <token>  (API clients / mobile)
    //   2. zcauthtoken cookie             (web app served from AppSail)
    // BearerAuthFilter runs first; SessionAuthFilter skips if auth is already set.
    @Bean
    @Order(2)
    fun protectedFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .addFilterBefore(bearerAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(sessionAuthFilter, BearerAuthFilter::class.java)
            .build()
    }
}
