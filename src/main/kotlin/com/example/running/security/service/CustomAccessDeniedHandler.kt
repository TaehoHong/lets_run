package com.example.running.security.service

import com.example.running.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class CustomAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    private val logger = KotlinLogging.logger { }

    @Throws(IOException::class)
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        // 상세한 Access Denied 정보 로깅
        val requestUri = request.requestURI
        val requestMethod = request.method
        val authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal ?: "Anonymous"
        val authorities = authentication?.authorities?.joinToString(", ") { it.authority } ?: "No authorities"
        val headers = request.headerNames.toList().associateWith { request.getHeader(it) }

        logger.error {
            """
            |===== Access Denied =====
            |Request URI: $requestMethod $requestUri
            |Principal: $principal
            |Authorities: $authorities
            |Exception: ${accessDeniedException.message}
            |Exception Type: ${accessDeniedException.javaClass.simpleName}
            |Request Headers: ${headers.filterKeys { it.lowercase() in listOf("authorization", "content-type", "user-agent") }}
            |Stack Trace: ${accessDeniedException.stackTraceToString().split("\n").take(10).joinToString("\n\t")}
            |=========================
            """.trimMargin()
        }

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse(
                    message = "Access Denied: ${accessDeniedException.message}",
                )
            )
        )
    }
}