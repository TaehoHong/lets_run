package com.example.running.exception

import com.example.running.domain.garmin.controller.GarminController
import com.example.running.domain.garmin.controller.GarminWebhookController
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [GarminController::class, GarminWebhookController::class])
class GarminControllerAdvice {
    @ExceptionHandler(Exception::class)
    fun error(error: Exception): ResponseEntity<Map<String, String>> {
        LoggerFactory.getLogger(javaClass).warn("Garmin operation failed ({})", error.javaClass.simpleName)
        val status = when (error) {
            is GarminRequestException -> error.status
            is HttpMediaTypeNotSupportedException -> 415
            is MissingServletRequestParameterException, is HttpMessageNotReadableException,
            is MethodArgumentTypeMismatchException -> 400
            else -> 503
        }
        return ResponseEntity.status(status)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(mapOf("message" to "Garmin operation unavailable"))
    }
}
