package com.eloiza.finrag.infrastructure.security

import com.eloiza.finrag.infrastructure.ratelimit.RateLimitFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val rateLimitFilter: RateLimitFilter,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptionHandling ->
                exceptionHandling.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                }
            }.authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/auth/**",
                        "/actuator/health",
                        "/actuator/prometheus",
                        "/error",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            // Depois do filtro JWT: o rate limit usa o userId autenticado como chave
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter::class.java)

        return http.build()
    }

    // O filtro só pode rodar dentro da cadeia do Security (precisa do SecurityContext);
    // sem isto o Boot o registraria também na cadeia do servlet, antes da autenticação.
    @Bean
    fun rateLimitFilterRegistration(filter: RateLimitFilter): FilterRegistrationBean<RateLimitFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }
}
