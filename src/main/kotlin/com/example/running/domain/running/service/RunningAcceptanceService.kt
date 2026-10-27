package com.example.running.domain.running.service

import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.notification.service.NotificationService
import com.example.running.domain.point.enums.PointTypeName
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.point.service.dto.PointUsageDto
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import com.example.running.domain.running.enums.RunningPointExclusionReason
import com.example.running.domain.running.enums.RunningRecordingMethod
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.exception.ApiError
import com.example.running.exception.ApiException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class RunningAcceptanceService(
    private val records: RunningRecordRepository,
    private val items: RunningRecordItemRepository,
    private val configurations: UserConfigurationService,
    private val points: UserPointService,
    private val league: LeagueService,
    private val notifications: NotificationService,
    private val recordService: RunningRecordService,
    private val validation: RunningValidationService,
) {
    data class Result(val awardedPoint: Int = 0, val leagueDistanceChanged: Boolean = false)

    @Transactional(rollbackFor = [Exception::class])
    fun accept(userId: Long, record: RunningRecord, recorded: Boolean, eligibleForPoints: Boolean, receivedAt: Instant): Result {
        // Entry points acquire this lock before loading or creating any record.
        configurations.getOrCreateForUpdate(userId)
        if (record.user.id != userId) throw ApiException(ApiError.NOT_AUTHORIZED)
        if (record.acceptanceStatus != RunningAcceptanceStatus.PENDING) return Result()
        val end = requireNotNull(record.endDatetime)
        validation.validate(record.startDatetime, end, record.distance, record.durationSec,
            record.cadence, record.heartRate, record.calorie, receivedAt)
        items.findAllByRunningRecord_IdOrderByOrderIndexAsc(record.id).forEach { item ->
            validation.metrics(!item.startDatetime.isBefore(record.startDatetime) && !item.endDatetime.isAfter(end))
            validation.validate(item.startDatetime, item.endDatetime, item.distance, item.durationSec,
                item.cadence, item.heartRate, 0, receivedAt, RunningRecordItemGpsCodec.decode(item.gpsPointsJson))
        }
        val overlaps = records.findOverlapCandidates(userId, record.startDatetime, end).filter { candidate ->
            record.startDatetime.isBefore(end) && candidate.id != record.id && candidate.originalEndDatetime?.let { candidateEnd ->
                val candidateStart = candidate.originalStartDatetime ?: candidate.startDatetime
                candidateStart.isBefore(end) && candidateEnd.isAfter(record.startDatetime)
            } == true
        }
        if (record.recordingMethod == null) {
            record.recordingMethod = if (recorded) RunningRecordingMethod.RECORDED else RunningRecordingMethod.UNKNOWN
        }
        if (overlaps.any { it.acceptanceStatus == RunningAcceptanceStatus.ACCEPTED }) {
            record.finishAcceptance(RunningAcceptanceStatus.DUPLICATE, false, RunningPointExclusionReason.OVERLAPPING_ACTIVITY)
            records.flush()
            recordService.refreshShoeTotals(listOf(record))
            return Result()
        }
        val status = if (recorded) RunningAcceptanceStatus.ACCEPTED else RunningAcceptanceStatus.PERSONAL_ONLY
        val pointExclusionReason = when {
            !recorded && record.recordingMethod == RunningRecordingMethod.MANUAL -> RunningPointExclusionReason.MANUAL_ENTRY
            !recorded -> RunningPointExclusionReason.RECORDING_METHOD_UNKNOWN
            !eligibleForPoints -> RunningPointExclusionReason.BEFORE_POINT_CUTOFF
            else -> null
        }
        record.finishAcceptance(status, eligibleForPoints, pointExclusionReason)
        val replaced = if (recorded) overlaps.filter { it.acceptanceStatus == RunningAcceptanceStatus.PERSONAL_ONLY } else emptyList()
        replaced.forEach { it.markDuplicate() }
        records.flush()
        recordService.refreshShoeTotals(replaced)
        var awarded = 0
        val point = record.distance / 100
        if (record.pointEligible && point > 0 && records.markPointAwardedIfEligible(userId, record.id)) {
            record.pointAwarded = true
            points.updatePoint(PointUsageDto(userId = userId, runningRecordId = record.id,
                point = point, pointTypeId = PointTypeName.RUNNING.id))
            awarded = point
        }
        recordService.refreshShoeTotals(listOf(record))
        notifications.recordCompletion(userId, end.toInstant())
        return Result(awarded, if (recorded) league.raiseCurrentLeagueDistance(userId) else false)
    }
}
