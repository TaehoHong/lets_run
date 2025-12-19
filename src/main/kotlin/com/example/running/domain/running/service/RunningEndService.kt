package com.example.running.domain.running.service

import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.point.enums.PointTypeName
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.point.service.dto.PointUsageDto
import com.example.running.domain.running.service.dto.EndRunningDto
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RunningEndService(
    private val runningRecordService: RunningRecordService,
    private val userPointService: UserPointService,
    private val leagueService: LeagueService
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
                calorie = updateRunningDto.calorie
            )
        )

        val userPoint = userPointService.updatePoint(
            PointUsageDto(
                userId = updateRunningDto.userId,
                runningRecordId = updateRunningDto.runningRecordId,
                point = calculatePoint(updateRunningDto.distance?:0),
                pointTypeId = PointTypeName.RUNNING.id
            )
        )

        // 리그 거리 업데이트
        updateRunningDto.distance?.let { distance ->
            leagueService.addRunningDistance(updateRunningDto.userId, distance)
        }

        return EndRunningDto(runningRecord, userPoint.point)
    }

    /**
        100m당 1 point
        distance unit: meter
     */
    private fun calculatePoint(distance: Long) = distance.toInt()/100
}