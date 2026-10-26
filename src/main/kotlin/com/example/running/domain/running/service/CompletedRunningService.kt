package com.example.running.domain.running.service

import com.example.running.domain.running.controller.dto.CompletedRunningRequest
import com.example.running.domain.running.controller.dto.CompletedRunningResponse
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.RunningRecordItemDto
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Service
class CompletedRunningService(
    private val records: RunningRecordRepository,
    private val configurations: UserConfigurationService,
    private val items: RunningRecordItemService,
    private val endService: RunningEndService,
    private val validation: RunningValidationService,
) {
    @Transactional(rollbackFor = [Exception::class])
    fun complete(userId: Long, request: CompletedRunningRequest, receivedAt: Instant = Instant.now()): CompletedRunningResponse {
        if (request.ownerUserId != userId) throw ApiException(ApiError.NOT_AUTHORIZED)
        validate(request, receivedAt)
        val source = RunningRecordSource.valueOf(request.source)
        configurations.getOrCreateForUpdate(userId)
        records.findByUserIdAndSourceAndExternalId(userId, source, request.sessionId)?.let {
            return CompletedRunningResponse(request.sessionId, it.id, "DUPLICATE")
        }
        val start = Instant.ofEpochSecond(request.startTimestamp).atOffset(ZoneOffset.UTC)
        val end = Instant.ofEpochSecond(request.endTimestamp).atOffset(ZoneOffset.UTC)
        val record = records.saveAndFlush(RunningRecord(
            user = User(id = userId), source = source, externalId = request.sessionId,
            startDatetime = start, endDatetime = end,
        ))
        items.save(userId, record.id, listOf(RunningRecordItemDto(
            distance = request.distance, durationSec = request.durationSec,
            cadence = request.cadence.toShort(), heartRate = request.heartRate.toShort(),
            minHeartRate = 0, maxHeartRate = 0, orderIndex = 0,
            startDateTime = start, endDateTime = end, gpsPoints = request.gpsPoints,
        )), receivedAt)
        endService.end(RunningRecordUpdateDto(
            userId = userId, runningRecordId = record.id,
            distance = request.distance, durationSec = request.durationSec,
            cadence = request.cadence.toShort(), heartRate = request.heartRate.toShort(),
            calorie = request.calorie, endDatetime = end,
        ), receivedAt)
        return CompletedRunningResponse(request.sessionId, record.id,
            if (record.acceptanceStatus == com.example.running.domain.running.enums.RunningAcceptanceStatus.DUPLICATE) "DUPLICATE" else "CREATED")
    }

    private fun validate(request: CompletedRunningRequest, receivedAt: Instant) {
        fun check(valid: Boolean) {
            validation.metrics(valid)
        }
        check(request.schemaVersion == 1 && request.ownerUserId > 0)
        val uuid = try { UUID.fromString(request.sessionId) } catch (_: IllegalArgumentException) { null }
        check(uuid?.toString() == request.sessionId)
        check(request.source == "APPLE_WATCH" || request.source == "WEAR_OS")
        // MariaDB DATETIME bounds also keep epoch-to-millisecond conversion safe.
        validation.metrics(request.startTimestamp in 0..253402300799 && request.endTimestamp in 0..253402300799)
        check(request.gpsPoints.size <= 100000)
        validation.metrics(request.cadence in 0..255 && request.heartRate in 0..255)
        validation.validate(Instant.ofEpochSecond(request.startTimestamp).atOffset(ZoneOffset.UTC),
            Instant.ofEpochSecond(request.endTimestamp).atOffset(ZoneOffset.UTC),
            request.distance, request.durationSec, request.cadence.toShort(), request.heartRate.toShort(),
            request.calorie, receivedAt, request.gpsPoints)
    }
}
