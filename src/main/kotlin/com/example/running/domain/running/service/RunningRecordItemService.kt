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
    fun save(runningRecordId: Long, runningRecordItemDtos: List<RunningRecordItemDto>) {

        runningRecordItemDtos.map { item ->
            RunningRecordItem(
                runningRecord = RunningRecord(id = runningRecordId),
                distance = item.distance,
                durationSec = item.durationSec,
                cadence = item.cadence,
                heartRate = item.heartRate,
                minHeartRate = item.minHeartRate,
                maxHeartRate = item.maxHeartRate,
                orderIndex = item.orderIndex,
                startDatetime = item.startDateTime,
                endDatetime = item.endDateTime,
            )
        }.let {
            runningRecordItemRepository.saveInBatch(it)
        }
    }
}