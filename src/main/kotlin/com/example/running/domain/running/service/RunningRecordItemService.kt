package com.example.running.domain.running.service

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.entity.RunningRecordItem
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.service.dto.RunningRecordItemDto
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RunningRecordItemService(
    private val runningRecordItemRepository: RunningRecordItemRepository,
    private val runningRecordRepository: RunningRecordRepository,
) {

    @Transactional(rollbackFor = [Exception::class])
    fun save(userId: Long, runningRecordId: Long, runningRecordItemDtos: List<RunningRecordItemDto>) {
        verifyOwnership(userId, runningRecordId)

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
                gpsPointsJson = RunningRecordItemGpsCodec.encode(item.gpsPoints),
            )
        }.let {
            runningRecordItemRepository.saveInBatch(it)
        }
    }

    @Transactional(readOnly = true)
    fun getItems(userId: Long, runningRecordId: Long): List<RunningRecordItemDto> {
        verifyOwnership(userId, runningRecordId)

        return runningRecordItemRepository
            .findAllByRunningRecord_IdOrderByOrderIndexAsc(runningRecordId)
            .map { item ->
                RunningRecordItemDto(
                    distance = item.distance,
                    durationSec = item.durationSec,
                    cadence = item.cadence,
                    heartRate = item.heartRate,
                    minHeartRate = item.minHeartRate,
                    maxHeartRate = item.maxHeartRate,
                    orderIndex = item.orderIndex,
                    startDateTime = item.startDatetime,
                    endDateTime = item.endDatetime,
                    gpsPoints = RunningRecordItemGpsCodec.decode(item.gpsPointsJson),
                )
            }
    }

    private fun verifyOwnership(userId: Long, runningRecordId: Long) {
        if (!runningRecordRepository.existsByIdAndUserId(runningRecordId, userId)) {
            throw ApiException(ApiError.NOT_AUTHORIZED)
        }
    }
}
