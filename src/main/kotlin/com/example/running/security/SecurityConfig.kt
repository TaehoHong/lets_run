package com.example.running.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig() {

    @Throws(Exception::class)
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .cors { it.disable() }
            .csrf { it.disable() }
            .authorizeHttpRequests { authorizeHttpRequests ->
                authorizeHttpRequests.requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                authorizeHttpRequests.requestMatchers(HttpMethod.GET,"/api/v1/users/*").permitAll()
                authorizeHttpRequests.requestMatchers(HttpMethod.POST, "/api/v1/users/verification/email").permitAll()

                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "/api/v1/oauth/google").permitAll()

                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "/api/v1/avatars/main").permitAll()

                authorizeHttpRequests.anyRequest().denyAll()
            }.build()
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