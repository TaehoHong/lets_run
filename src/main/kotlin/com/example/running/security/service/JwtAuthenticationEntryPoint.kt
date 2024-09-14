package com.example.running.security.service

import com.example.running.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
): AuthenticationEntryPoint{

    val log = KotlinLogging.logger {  }

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException?
    ) {

        val message = when(authException) {
            null -> "인증에 실패하였습니다. 다시 로그인 해주세요."
            else -> {
                log.error { authException.message }
                "재인증이 필요합니다. 다시 로그인 해주세요."
            }
        }



        response.contentType = "application/json;charset=UTF-8"
        response.status = HttpServletResponse.SC_UNAUTHORIZED

        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse(message)))
    }
}