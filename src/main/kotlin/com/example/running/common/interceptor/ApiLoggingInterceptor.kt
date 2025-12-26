package com.example.running.common.interceptor

import com.example.running.common.MultipleReadableRequestWrapper
import com.example.running.config.properties.JwtProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.util.ContentCachingResponseWrapper
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class ApiLoggingInterceptor: HandlerInterceptor {

    private val objectMapper = ObjectMapper()

    val log = KotlinLogging.logger {}

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        log.info { "Time: ${OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))}" }
        log.info { "Requests: ${request.method} - ${request.requestURL}${if (request.queryString != null) "?" + request.queryString else ""}" }
        log.info { "AccessToken: ${request.getHeader(JwtProperties.ACCESS_TOKEN_HEADER)}" }
        log.info { "RefreshToken: ${request.getHeader(JwtProperties.REFRESH_TOKEN_HEADER)}" }

        if (request is MultipleReadableRequestWrapper) {
            log.info { "Request Body: ${objectMapper.readTree(request.contents.toString(charset(request.characterEncoding)))}" }
        } else {
            log.info { "Request Body: [Multipart request - body not logged]" }
        }

        return true
    }

    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        (response as ContentCachingResponseWrapper).also {
            log.info {
                """
                    Time: ${OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))}
                    Statue: ${response.status}
                    Response Body: ${objectMapper.readTree(response.contentAsByteArray.toString(charset(request.characterEncoding)))}
                """.trimIndent()
            }
        }
    }
}