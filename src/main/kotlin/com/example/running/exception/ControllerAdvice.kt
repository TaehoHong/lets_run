package com.example.running.exception

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ControllerAdvice {

    private val log = KotlinLogging.logger {}

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException,
                                             request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        if (request.requestURI.startsWith("/api/v1/running")) {
            return ResponseEntity(RunningValidationException(RunningValidationReason.RUNNING_INVALID_METRICS).response(), HttpStatus.BAD_REQUEST)
        }
        val errors = mutableMapOf<String, String>()
        ex.bindingResult.allErrors.forEach { error ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.defaultMessage ?: "Invalid value"
            errors[fieldName] = errorMessage
        }

        return ResponseEntity(
            ErrorResponse(ApiError.BAD_REQUEST_FIELD_VALID_ERROR, errors),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(RunningValidationException::class)
    fun handleRunningValidation(ex: RunningValidationException): ResponseEntity<ErrorResponse> =
        ResponseEntity(ex.response(), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class)
    fun handleUnreadableRunning(ex: org.springframework.http.converter.HttpMessageNotReadableException,
                                request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        if (request.requestURI.startsWith("/api/v1/running")) {
            return ResponseEntity(RunningValidationException(RunningValidationReason.RUNNING_INVALID_METRICS).response(), HttpStatus.BAD_REQUEST)
        }
        return ResponseEntity(ErrorResponse(ex.message ?: "Invalid request"), HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> {

        return ResponseEntity(
            ErrorResponse(ex.message!!),
            ex.status
        )
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        ex.printStackTrace()

        return ResponseEntity(
            ErrorResponse(ex.message!!),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }


    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        if (ex is org.springframework.web.HttpMediaTypeNotSupportedException &&
            request.requestURI == "/api/v1/garmin/webhook") {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .header("Cache-Control", "no-store")
                .body(ErrorResponse("Garmin operation unavailable"))
        }


        return ResponseEntity(
            ErrorResponse(ex.message!!),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }
}