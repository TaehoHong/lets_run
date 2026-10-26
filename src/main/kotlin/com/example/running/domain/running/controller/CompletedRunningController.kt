package com.example.running.domain.running.controller

import com.example.running.domain.running.controller.dto.CompletedRunningRequest
import com.example.running.domain.running.controller.dto.CompletedRunningResponse
import com.example.running.domain.running.service.CompletedRunningService
import com.example.running.helper.authenticateWithUser
import com.example.running.exception.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.*
import java.time.Instant
import com.example.running.exception.RunningValidationException
import com.example.running.exception.RunningValidationReason

@RestController
@RequestMapping("/api/v1/running/completed-records")
class CompletedRunningController(private val service: CompletedRunningService) {
    private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    @PostMapping
    fun complete(@RequestBody body: com.fasterxml.jackson.databind.JsonNode,
                 @RequestAttribute(name = "running.receivedAt", required = false) receivedAt: Instant? = null): CompletedRunningResponse =
        authenticateWithUser { userId ->
            val request = try {
                val integers = listOf("schemaVersion", "ownerUserId", "startTimestamp", "endTimestamp",
                    "distance", "durationSec", "cadence", "heartRate", "calorie")
                if (!integers.all { body[it]?.isIntegralNumber == true }) {
                    throw RunningValidationException(RunningValidationReason.RUNNING_INVALID_METRICS)
                }
                if (body["gpsPoints"]?.isArray != true || body["gpsPoints"].size() > 100000) {
                    throw RunningValidationException(RunningValidationReason.RUNNING_INVALID_GPS)
                }
                body["gpsPoints"].forEach { point ->
                    if (point["timestampMs"]?.isIntegralNumber != true ||
                        !listOf("latitude", "longitude", "speed", "altitude").all { point[it]?.isNumber == true } ||
                        !point.has("accuracy") || (!point["accuracy"].isNull && !point["accuracy"].isNumber)) {
                        throw RunningValidationException(RunningValidationReason.RUNNING_INVALID_GPS)
                    }
                }
                mapper.treeToValue(body, CompletedRunningRequest::class.java)
            } catch (error: RunningValidationException) {
                throw error
            } catch (_: Exception) {
                throw RunningValidationException(RunningValidationReason.RUNNING_INVALID_METRICS)
            }
            service.complete(userId, request, receivedAt ?: Instant.now())
        }

    @ExceptionHandler(com.example.running.exception.ApiException::class)
    fun invalidRequest(ex: com.example.running.exception.ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity(ErrorResponse(if (ex.status == HttpStatus.FORBIDDEN) "Not authorized" else "Invalid completed running record"), ex.status)

    @ExceptionHandler(RunningValidationException::class)
    fun invalidRunning(ex: RunningValidationException): ResponseEntity<ErrorResponse> =
        ResponseEntity(ex.response(), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(Exception::class)
    fun failed(): ResponseEntity<ErrorResponse> =
        ResponseEntity(ErrorResponse("Completed running record unavailable"), HttpStatus.INTERNAL_SERVER_ERROR)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun invalidJson(): ResponseEntity<ErrorResponse> =
        ResponseEntity(RunningValidationException(RunningValidationReason.RUNNING_INVALID_METRICS).response(), HttpStatus.BAD_REQUEST)
}
