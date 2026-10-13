package com.example.running.domain.running.service

import com.example.running.domain.running.service.dto.RunningRecordItemGpsPointDto
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging

object RunningRecordItemGpsCodec {
    private val log = KotlinLogging.logger {}
    private val objectMapper = jacksonObjectMapper()
    private val gpsPointsTypeRef = object : TypeReference<List<RunningRecordItemGpsPointDto>>() {}

    fun encode(points: List<RunningRecordItemGpsPointDto>): String? {
        if (points.isEmpty()) return null
        return try {
            objectMapper.writeValueAsString(points)
        } catch (ex: Exception) {
            log.warn(ex) { "[RunningRecordItemGpsCodec] Failed to encode gps points" }
            null
        }
    }

    fun matches(raw: String?, points: List<RunningRecordItemGpsPointDto>): Boolean {
        if (raw == null) return points.isEmpty()
        return try {
            objectMapper.readValue(raw, gpsPointsTypeRef) == points
        } catch (ex: Exception) {
            // Corrupt stored JSON must never be treated as a valid empty route.
            false
        }
    }

    fun decode(raw: String?): List<RunningRecordItemGpsPointDto> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            objectMapper.readValue(raw, gpsPointsTypeRef)
        } catch (ex: Exception) {
            log.warn(ex) { "[RunningRecordItemGpsCodec] Failed to decode gps points" }
            emptyList()
        }
    }
}
