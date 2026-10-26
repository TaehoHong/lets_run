package com.example.running.security.filter

import com.example.running.config.properties.JwtProperties
import com.example.running.exception.ErrorResponse
import com.example.running.security.exception.TokenException
import com.example.running.security.service.TokenService
import com.example.running.security.vo.AuthenticationVo
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AuthenticationFilter(
    private val tokenService: TokenService,
    private val objectMapper: ObjectMapper
): OncePerRequestFilter() {

    private val log = KotlinLogging.logger{}

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        (request.method == "GET" && request.requestURI == "/api/v1/garmin/callback") ||
        (request.method == "POST" && request.requestURI == "/api/v1/garmin/webhook")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestUri = request.requestURI
        val requestMethod = request.method

        log.debug { "AuthenticationFilter - Processing: $requestMethod $requestUri" }

        val accessToken = request.getHeader(JwtProperties.ACCESS_TOKEN_HEADER)
        val refreshToken = request.getHeader(JwtProperties.REFRESH_TOKEN_HEADER)

        if (accessToken != null) {
            log.debug { "AccessToken found for: $requestMethod $requestUri" }
            if (!verifyToken(accessToken, response)) return

            val token = accessToken.replace(JwtProperties.TOKEN_PREFIX,"")

            val authentication = SecurityContextHolder.getContext().authentication
            if(authentication == null) {
                val auth = getAuthentication(token)
                SecurityContextHolder.getContext().authentication = auth
                log.debug { "Authentication set" }
            } else {
                log.debug { "Authentication already exists" }
            }
        } else {
            log.debug { "No AccessToken for: $requestMethod $requestUri" }
        }

        filterChain.doFilter(request, response)
    }

    private fun getAuthentication(token: String): UsernamePasswordAuthenticationToken {

        val id = tokenService.getId(token)
        val email = tokenService.getEmail(token)
        val role = tokenService.getAuthorityType(token)

        return UsernamePasswordAuthenticationToken(email, "", listOf(SimpleGrantedAuthority(role.role)))
            .also {
                it.details = AuthenticationVo(id.toLong())
            }
    }

    private fun verifyToken(token: String, response: HttpServletResponse): Boolean {
        runCatching {
            tokenService.verify(token)
        }.onFailure { exception ->
            log.warn { "Token verification failed (${exception.javaClass.simpleName})" }

            val errorResponse = when (exception) {
                is TokenException -> ErrorResponse(message = exception.message)
                else -> ErrorResponse(message = "Invalid authentication token")
            }

            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(objectMapper.writeValueAsString(errorResponse))
            response.writer.flush()
            return false
        }
        return true
    }
}
