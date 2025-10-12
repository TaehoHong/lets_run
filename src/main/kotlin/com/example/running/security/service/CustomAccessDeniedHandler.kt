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
): AccessDeniedHandler {

    private val logger = KotlinLogging.logger {  }

    @Throws(IOException::class)
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {

        logger.error { "${"error : {}"} ${accessDeniedException.message}"}

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(objectMapper.writeValueAsString(
            ErrorResponse(
                message = accessDeniedException.message?:"",
            )
        ))
    }
}