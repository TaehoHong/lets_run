package com.example.running.domain.running.service

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.entity.RunningRecordItem
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.service.dto.RunningRecordItemDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RunningRecordItemService(
    private val runningRecordItemRepository: RunningRecordItemRepository
) {

    @Transactional(rollbackFor = [Exception::class])
    fun save(runningRecordId: Long, runningRecordItemDto: RunningRecordItemDto) {
     
        RunningRecordItem(
            runningRecord = RunningRecord(runningRecordId),
            distance = runningRecordItemDto.distance,
            durationSec = runningRecordItemDto.durationSec,
            cadence = runningRecordItemDto.cadence,
            heartRate = runningRecordItemDto.heartRate,
            minHeartRate = runningRecordItemDto.minHeartRate,
            maxHeartRate = runningRecordItemDto.maxHeartRate,
            orderIndex = runningRecordItemDto.orderIndex,
            startDatetime = runningRecordItemDto.startDateTime,
            endDatetime = runningRecordItemDto.endDateTime,
        ).let {
            runningRecordItemRepository.save(it)
        }
    }
}