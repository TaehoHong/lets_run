package com.example.running.security

import com.example.running.security.filter.AuthenticationFilter
import com.example.running.security.service.CustomAccessDeniedHandler
import com.example.running.security.service.JwtAuthenticationEntryPoint
import com.example.running.security.service.TokenService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val accessDeniedException: CustomAccessDeniedHandler,
    private val tokenService:TokenService
) {

    @Throws(Exception::class)
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .cors { it.disable() }
            .csrf { it.disable() }
            .addFilterBefore(AuthenticationFilter(tokenService), UsernamePasswordAuthenticationFilter::class.java)
            .sessionManagement { sessionManagement ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests { authorizeHttpRequests ->
                authorizeHttpRequests.requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html", "/error").permitAll()

                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "healthy-check", "active-type").permitAll()

                authorizeHttpRequests.requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                authorizeHttpRequests.requestMatchers(HttpMethod.GET,"/api/v1/users/*").permitAll()
                authorizeHttpRequests.requestMatchers(HttpMethod.POST, "/api/v1/users/verification/email").permitAll()

                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "/api/v1/users/points").authenticated()

                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "/api/v1/oauth/google").permitAll()

                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "/api/v1/avatars/main").authenticated()
                authorizeHttpRequests.requestMatchers(HttpMethod.PUT, "/api/v1/avatars/*").authenticated()
                authorizeHttpRequests.requestMatchers(HttpMethod.POST, "/api/v1/avatars/*/items").authenticated()
                authorizeHttpRequests.requestMatchers(HttpMethod.DELETE, "/api/v1/avatars/*/items/*").authenticated()
                authorizeHttpRequests.requestMatchers(HttpMethod.DELETE, "/api/v1/avatars/*/items").authenticated()

                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "/api/v1/items").permitAll()

                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "/api/v1/user-items").permitAll()

                authorizeHttpRequests.requestMatchers(HttpMethod.POST, "/api/v1/running").authenticated()
                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "/api/v1/running").authenticated()

                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "/api/v1/running/statistics").authenticated()

                authorizeHttpRequests.anyRequest().denyAll()
            }
            .exceptionHandling{ handling ->
                handling.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                handling.accessDeniedHandler(accessDeniedException)
            }.logout {
                logout -> logout.disable()
            }
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        return CorsConfiguration()
            .also {
                it.allowedMethods = listOf<String>("*")
                it.allowedHeaders = listOf<String>("*")
                it.allowedOrigins = listOf<String>("*")
                it.allowCredentials = true
            }.let {
                val corsConfigurationSource = UrlBasedCorsConfigurationSource()
                corsConfigurationSource.registerCorsConfiguration("/**", it)
                return corsConfigurationSource
            }


    }
}