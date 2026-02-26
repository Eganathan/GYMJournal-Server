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
class SecurityConfig(
    private val catalystAuthFilter: CatalystAuthFilter
) {

    // Public endpoints — no auth required
    @Bean
    @Order(1)
    fun publicFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/api/v1/health")
            .csrf { it.disable() }
            .cors { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }

    // All other endpoints — CatalystSDK.init(request) reads ZGS-injected headers
    // automatically; no manual token or cookie parsing needed.
    // CORS headers are added by the Catalyst ZGS gateway — Spring must not add its own.
    // OPTIONS preflight requests are permitted without auth so CORS handshake succeeds.
    @Bean
    @Order(2)
    fun protectedFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .cors { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS).permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(catalystAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }
}
