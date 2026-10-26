package com.example.running.domain.running.service

import com.example.running.domain.common.dto.CursorResult
import com.example.running.domain.running.controller.dto.RunningRecordSearchRequest
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.RunningRecordDetailDto
import com.example.running.domain.running.service.dto.RunningRecordDto
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.domain.shoe.service.ShoeService
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException
import com.example.running.domain.user.service.UserConfigurationService
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RunningRecordService(
    private val runningRecordRepository: RunningRecordRepository,
    private val shoeService: ShoeService,
    private val configurations: UserConfigurationService,
    private val validation: RunningValidationService,
) {

    @Transactional(rollbackFor = [Exception::class])
    fun save(runningRecord: RunningRecord): RunningRecord {
        return runningRecordRepository.save(runningRecord)
    }
    
    @Transactional(rollbackFor = [Exception::class])
    fun endPreviousRecords(userId: Long) {
        configurations.getOrCreateForUpdate(userId)
        runningRecordRepository.updateIsEndById(true, userId)
    }

    @Transactional(readOnly = true)
    fun getDtoCursorPage(userId: Long, request: RunningRecordSearchRequest): CursorResult<RunningRecordDto>  {
        val runningRecordDtos = runningRecordRepository.findAllByCursor(userId, request)
            .map { RunningRecordDto(it) }

        val cursor = runningRecordDtos.lastOrNull()?.id
        val hasNext = runningRecordRepository.existsByCursor(userId, cursor, request)

        return CursorResult(runningRecordDtos, cursor, hasNext)
    }

    @Transactional(readOnly = true)
    fun verifyOwnership(userId: Long, runningRecordId: Long) {
        if (!runningRecordRepository.existsByIdAndUserId(runningRecordId, userId)) {
            throw ApiException(ApiError.NOT_AUTHORIZED)
        }
    }

    @Transactional(readOnly = true)
    fun getDto(userId: Long, runningRecordId: Long): RunningRecordDetailDto {
        return getEndedStatisticRecordByIdAndUserId(runningRecordId, userId)
            .let { RunningRecordDetailDto(it) }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun updateShoe(userId: Long, runningRecordId: Long, shoeId: Long): RunningRecordDetailDto {
        configurations.getOrCreateForUpdate(userId)
        val runningRecord = getEndedStatisticRecordByIdAndUserId(runningRecordId, userId)
        val shoe = shoeService.getActiveShoe(userId, shoeId)
        val previousShoeId = runningRecord.shoe?.id

        if (previousShoeId != shoe.id) {
            runningRecord.shoe = shoe

            previousShoeId?.let { updateShoeTotalDistance(it) }
            shoe.totalDistance = updateShoeTotalDistance(shoe.id)
        }

        return RunningRecordDetailDto(runningRecord)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun updateRecord(updateDto: RunningRecordUpdateDto, receivedAt: Instant = Instant.now()): RunningRecord {
        configurations.getOrCreateForUpdate(updateDto.userId)
        return getByIdAndUserId(updateDto.runningRecordId, updateDto.userId)
            .apply {
                val mergedEnd = updateDto.endDatetime ?: endDatetime
                if (isEnd || mergedEnd != null) {
                    validation.validate(updateDto.startDatetime ?: startDatetime,
                        requireNotNull(mergedEnd), updateDto.distance ?: distance,
                        updateDto.durationSec ?: durationSec, updateDto.cadence ?: cadence,
                        updateDto.heartRate ?: heartRate, updateDto.calorie ?: calorie, receivedAt)
                } else {
                    validation.metrics((updateDto.distance ?: distance) >= 0 && (updateDto.durationSec ?: durationSec) >= 0 &&
                        (updateDto.calorie ?: calorie) >= 0 && (updateDto.cadence ?: cadence) in 0..255 &&
                        (updateDto.heartRate ?: heartRate) in 0..255)
                }
                this.update(
                    updateDto.shoeId,
                    updateDto.distance,
                    updateDto.durationSec,
                    updateDto.cadence,
                    updateDto.heartRate,
                    updateDto.calorie,
                    updateDto.startDatetime,
                    updateDto.endDatetime
                )
            }.also { runningRecord ->
                runningRecord.shoe?.id?.let { updateShoeTotalDistance(it) }
            }
    }

    fun refreshShoeTotals(records: List<RunningRecord>) {
        records.mapNotNull { it.shoe?.id }.distinct().forEach { updateShoeTotalDistance(it) }
    }

    private fun getByIdAndUserId(id: Long, userId: Long): RunningRecord {
        return runningRecordRepository.findOwnedForUpdate(id, userId)
            ?: run { throw RuntimeException("존재하지 않는 러닝 기록입니다.") }
    }

    private fun getEndedStatisticRecordByIdAndUserId(id: Long, userId: Long): RunningRecord {
        return runningRecordRepository.findByIdAndUserIdAndIsEndTrueAndIsStatisticIncludedTrue(id, userId)
            ?: throw ApiException(ApiError.NOT_AUTHORIZED)
    }

    private fun getTotalDistanceByShoeId(shoeId: Long): Int {
        return runningRecordRepository.findAllDistanceByShoeId(shoeId).sum()
    }

    private fun updateShoeTotalDistance(shoeId: Long): Int {
        val totalDistance = getTotalDistanceByShoeId(shoeId)
        shoeService.updateTotalDistance(shoeId, totalDistance)
        return totalDistance
    }

}
