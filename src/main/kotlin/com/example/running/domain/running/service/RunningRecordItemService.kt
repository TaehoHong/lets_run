package com.example.running.domain.running.service

import com.example.running.domain.running.entity.RunningRecordItem
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.service.dto.RunningRecordItemDto
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException
import java.time.ZoneOffset
import java.time.Instant
import com.example.running.domain.user.service.UserConfigurationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RunningRecordItemService(
    private val runningRecordItemRepository: RunningRecordItemRepository,
    private val runningRecordRepository: RunningRecordRepository,
    private val configurations: UserConfigurationService,
    private val validation: RunningValidationService,
) {

    @Transactional(rollbackFor = [Exception::class])
    fun save(userId: Long, runningRecordId: Long, runningRecordItemDtos: List<RunningRecordItemDto>, receivedAt: Instant = Instant.now()) {
        configurations.getOrCreateForUpdate(userId)
        val record = runningRecordRepository.findOwnedForUpdate(runningRecordId, userId)
            ?: throw ApiException(ApiError.NOT_AUTHORIZED)
        runningRecordItemDtos.forEach { item ->
            validation.metrics(!item.startDateTime.isBefore(record.startDatetime))
            record.endDatetime?.let { validation.metrics(!item.endDateTime.isAfter(it)) }
            validation.metrics(item.minHeartRate in 0..255 && item.maxHeartRate in 0..255)
            validation.validate(item.startDateTime, item.endDateTime, item.distance, item.durationSec,
                item.cadence, item.heartRate, 0, receivedAt, item.gpsPoints)
        }
        val stored = runningRecordItemRepository.findAllByRunningRecord_IdOrderByOrderIndexAsc(runningRecordId)

        runningRecordItemDtos.filterNot { item ->
            stored.any { existing -> matches(existing, item) }
        }.map { item ->
            RunningRecordItem(
                runningRecord = record,
                distance = item.distance,
                durationSec = item.durationSec,
                cadence = item.cadence,
                heartRate = item.heartRate,
                minHeartRate = item.minHeartRate,
                maxHeartRate = item.maxHeartRate,
                orderIndex = item.orderIndex,
                startDatetime = item.startDateTime.withOffsetSameInstant(ZoneOffset.UTC).withNano(0),
                endDatetime = item.endDateTime.withOffsetSameInstant(ZoneOffset.UTC).withNano(0),
                gpsPointsJson = RunningRecordItemGpsCodec.encode(item.gpsPoints),
            )
        }.let {
            runningRecordItemRepository.saveInBatch(it)
        }
    }

    @Transactional(readOnly = true)
    fun getItems(userId: Long, runningRecordId: Long): List<RunningRecordItemDto> {
        verifyReadableRecord(userId, runningRecordId)

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

    private fun matches(stored: RunningRecordItem, item: RunningRecordItemDto): Boolean =
        stored.orderIndex == item.orderIndex &&
            stored.distance == item.distance && stored.durationSec == item.durationSec &&
            stored.cadence == item.cadence && stored.heartRate == item.heartRate &&
            stored.minHeartRate == item.minHeartRate && stored.maxHeartRate == item.maxHeartRate &&
            stored.startDatetime.toEpochSecond() == item.startDateTime.toEpochSecond() &&
            stored.endDatetime.toEpochSecond() == item.endDateTime.toEpochSecond() &&
            RunningRecordItemGpsCodec.matches(stored.gpsPointsJson, item.gpsPoints)

    private fun verifyReadableRecord(userId: Long, runningRecordId: Long) {
        if (!runningRecordRepository.existsByIdAndUserIdAndIsEndTrueAndIsStatisticIncludedTrue(runningRecordId, userId)) {
            throw ApiException(ApiError.NOT_AUTHORIZED)
        }
    }
}
