package com.example.running.security.filter

import com.example.running.config.properties.JwtProperties
import com.example.running.security.service.TokenService
import com.example.running.security.vo.AuthenticationVo
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class AuthenticationFilter(
    private val tokenService: TokenService
): OncePerRequestFilter() {

    private val log = KotlinLogging.logger{}

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        log.info{"JwtAuthorizationFilter"}
        val accessToken = request.getHeader(JwtProperties.ACCESS_TOKEN_HEADER)
        val refreshToken = request.getHeader(JwtProperties.REFRESH_TOKEN_HEADER)

        if (accessToken != null) {

            tokenService.verifyToken(accessToken)

            val token = accessToken.replace(JwtProperties.TOKEN_PREFIX,"")

            val authentication = SecurityContextHolder.getContext().authentication
            if(authentication == null) {
                SecurityContextHolder.getContext().authentication = getAuthentication(token)
            }
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
}