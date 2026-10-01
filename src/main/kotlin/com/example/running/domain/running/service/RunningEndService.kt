package com.example.running.domain.running.service

import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.point.enums.PointTypeName
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.point.service.dto.PointUsageDto
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.EndRunningDto
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.utils.alsoIf
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class RunningEndService(
    private val runningRecordService: RunningRecordService,
    private val userPointService: UserPointService,
    private val runningRecordRepository: RunningRecordRepository,
    private val leagueService: LeagueService,
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
        calculatePoint(updateRunningDto.distance ?: 0)
            .alsoIf({ it > 0 && runningRecordRepository.markPointAwardedIfEligible(updateRunningDto.userId, runningRecord.id) }) { totalEarnedPoints ->
                runningRecord.pointAwarded = true
                userPointService.updatePoint(
                    PointUsageDto(
                        userId = updateRunningDto.userId,
                        runningRecordId = runningRecord.id,
                        point = totalEarnedPoints,
                        pointTypeId = PointTypeName.RUNNING.id
                    )
                )
            }

        // 총 획득 포인트 (기본 + 보너스)
        val userPoint = userPointService.getOrCreateByUserId(updateRunningDto.userId)

        // 업로드 시각이 아닌 저장된 러닝 종료 시각으로 현재 시즌 거리를 재집계한다.
        leagueService.raiseCurrentLeagueDistance(updateRunningDto.userId)

        return EndRunningDto(runningRecord, userPoint.point)
    }

    /**
        100m당 1 point
        distance unit: meter
     */
    private fun calculatePoint(distance: Int) = distance.toInt()/100
}
