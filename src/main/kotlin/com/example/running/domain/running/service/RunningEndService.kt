package com.example.running.domain.running.service

import com.example.running.domain.point.enums.PointTypeName
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.point.service.dto.PointUsageDto
import com.example.running.domain.running.service.dto.EndRunningDto
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class RunningEndService(
    private val runningRecordService: RunningRecordService,
    private val userPointService: UserPointService,
) {

    @Transactional(rollbackFor = [Exception::class])
    fun end(updateRunningDto: RunningRecordUpdateDto): EndRunningDto {
        val runningRecord = runningRecordService.updateRecord(
            RunningRecordUpdateDto(
                userId = updateRunningDto.userId,
                runningRecordId = updateRunningDto.runningRecordId,
                distance = updateRunningDto.distance,
                durationSec = updateRunningDto.durationSec,
                cadence = updateRunningDto.cadence,
                heartRate = updateRunningDto.heartRate,
                calorie = updateRunningDto.calorie,
                endDatetime = updateRunningDto.endDatetime ?: OffsetDateTime.now(ZoneOffset.UTC)
            )
        )

        // 기본 러닝 포인트 지급
        val totalEarnedPoints = calculatePoint(updateRunningDto.distance ?: 0)
        userPointService.updatePoint(
            PointUsageDto(
                userId = updateRunningDto.userId,
                runningRecordId = updateRunningDto.runningRecordId,
                point = totalEarnedPoints,
                pointTypeId = PointTypeName.RUNNING.id
            )
        )

        // 총 획득 포인트 (기본 + 보너스)
        val userPoint = userPointService.getOrCreateByUserId(updateRunningDto.userId)

        return EndRunningDto(runningRecord, userPoint.point)
    }

    /**
        100m당 1 point
        distance unit: meter
     */
    private fun calculatePoint(distance: Long) = distance.toInt()/100
}