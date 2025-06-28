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
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
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

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> {

        return ResponseEntity(
            ErrorResponse(ex.message!!),
            ex.status
        )
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ErrorResponse> {

        return ResponseEntity(
            ErrorResponse(ex.message!!),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }


    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ErrorResponse> {

        return ResponseEntity(
            ErrorResponse(ex.message!!),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }
}