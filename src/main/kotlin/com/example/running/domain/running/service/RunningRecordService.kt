package com.example.running.domain.running.service

import com.example.running.domain.common.dto.CursorResult
import com.example.running.domain.running.controller.dto.RunningRecordSearchRequest
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.RunningRecordDto
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.domain.shoe.service.ShoeService
import com.example.running.utils.alsoIf
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RunningRecordService(
    private val runningRecordRepository: RunningRecordRepository,
    private val shoeService: ShoeService,
) {

    @Transactional(rollbackFor = [Exception::class])
    fun save(runningRecord: RunningRecord): RunningRecord {
        return runningRecordRepository.save(runningRecord)
    }
    
    @Transactional(rollbackFor = [Exception::class])
    fun endPreviousRecords(userId: Long) {
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


    @Transactional(rollbackFor = [Exception::class])
    fun updateRecord(updateDto: RunningRecordUpdateDto): RunningRecord {
        return getByIdAndUserId(updateDto.runningRecordId, updateDto.userId)
            .apply {
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
            }.alsoIf({ updateDto.shoeId != null }) {
                val totalDistance = getTotalDistanceByShoeId(updateDto.shoeId!!)
                shoeService.updateTotalDistance(updateDto.shoeId, totalDistance)
            }
    }

    private fun getByIdAndUserId(id: Long, userId: Long): RunningRecord {
        return runningRecordRepository.findByIdAndUserId(id, userId)
            ?: run { throw RuntimeException("존재하지 않는 러닝 기록입니다.") }
    }

    private fun getTotalDistanceByShoeId(shoeId: Long): Int {
        return runningRecordRepository.findAllDistanceByShoeId(shoeId).sum()
    }

}