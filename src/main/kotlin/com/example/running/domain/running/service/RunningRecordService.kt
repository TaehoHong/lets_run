package com.example.running.domain.running.service

import com.example.running.domain.common.dto.CursorResult
import com.example.running.domain.running.controller.dto.RunningRecordSearchRequest
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.RunningRecordDto
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.domain.running.service.dto.StartRunningDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Service
class RunningRecordService(
    private val runningRecordRepository: RunningRecordRepository
) {

    @Transactional(rollbackFor = [Exception::class])
    fun startRecord(userId: Long, startDateTime: OffsetDateTime): StartRunningDto {
        endRecord(userId)
        return runningRecordRepository.save(
            RunningRecord(userId = userId, startDateTime = startDateTime)
        ).let {
            StartRunningDto(it.id)
        }
    }

    private fun endRecord(userId: Long) {
        runningRecordRepository.updateIsEndById(true, userId)
    }

    @Transactional(readOnly = true)
    fun getDtoCursorPage(userId: Long, request: RunningRecordSearchRequest): CursorResult<RunningRecordDto>  {
        val runningRecordDtos = runningRecordRepository.findAllByCursor(userId, request)
            .map { RunningRecordDto(it) }
        val hasNext = runningRecordRepository.existsByCursor(userId, request)
        val cursor = runningRecordDtos.lastOrNull()?.id

        return CursorResult(runningRecordDtos, cursor, hasNext)
    }


    @Transactional(rollbackFor = [Exception::class])
    fun updateRecord(updateDto: RunningRecordUpdateDto) {

        getByIdAndUserId(updateDto.runningRecordId, updateDto.userId)
            .apply {
                this.endRecord(
                    updateDto.distance,
                    updateDto.durationSec,
                    updateDto.cadence,
                    updateDto.heartRate,
                    updateDto.calorie,
                    updateDto.endDateTime

                )
            }
    }

    private fun getByIdAndUserId(id: Long, userId: Long): RunningRecord {
        return runningRecordRepository.findByIdAndUserId(id, userId)
            ?: run { throw RuntimeException("존재하지 않는 러닝 기록입니다.") }
    }

}