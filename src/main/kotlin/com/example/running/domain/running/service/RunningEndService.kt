package com.example.running.domain.running.service

import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.EndRunningDto
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

@Service
class RunningEndService(
    private val runningRecordService: RunningRecordService,
    private val userPointService: UserPointService,
    private val runningRecordRepository: RunningRecordRepository,
    private val configurations: UserConfigurationService,
    private val acceptance: RunningAcceptanceService,
) {
    @Transactional(rollbackFor = [Exception::class])
    fun end(updateRunningDto: RunningRecordUpdateDto, receivedAt: Instant = Instant.now()): EndRunningDto {
        configurations.getOrCreateForUpdate(updateRunningDto.userId)
        val stored = runningRecordRepository.findOwnedForUpdate(updateRunningDto.runningRecordId, updateRunningDto.userId)
            ?: throw ApiException(ApiError.NOT_AUTHORIZED)
        val completedAt = updateRunningDto.endDatetime ?: stored.endDatetime ?: receivedAt.atOffset(ZoneOffset.UTC)
        val runningRecord = runningRecordService.updateRecord(RunningRecordUpdateDto(
            userId = updateRunningDto.userId, runningRecordId = updateRunningDto.runningRecordId,
            distance = updateRunningDto.distance, durationSec = updateRunningDto.durationSec,
            cadence = updateRunningDto.cadence, heartRate = updateRunningDto.heartRate,
            calorie = updateRunningDto.calorie, endDatetime = completedAt,
        ), receivedAt)
        acceptance.accept(updateRunningDto.userId, runningRecord, recorded = true,
            eligibleForPoints = true, receivedAt = receivedAt)
        return EndRunningDto(runningRecord, userPointService.getOrCreateByUserId(updateRunningDto.userId).point)
    }
}
