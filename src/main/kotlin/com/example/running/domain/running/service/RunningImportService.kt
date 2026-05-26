package com.example.running.domain.running.service

import com.example.running.domain.point.enums.PointTypeName
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.point.service.dto.PointUsageDto
import com.example.running.domain.running.controller.dto.ImportRunningBatchRequest
import com.example.running.domain.running.controller.dto.ImportRunningBatchResponse
import com.example.running.domain.running.controller.dto.ImportRunningRecordRequest
import com.example.running.domain.running.controller.dto.ImportRunningRecordStatus
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.entity.RunningRecordItem
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.utils.convertToOffsetDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Service
class RunningImportService(
    private val runningRecordRepository: RunningRecordRepository,
    private val runningRecordItemRepository: RunningRecordItemRepository,
    private val userPointService: UserPointService,
    private val userConfigurationService: UserConfigurationService,
) {

    @Transactional(rollbackFor = [Exception::class])
    fun importBatch(userId: Long, request: ImportRunningBatchRequest): ImportRunningBatchResponse {
        validateBatch(request)

        val configuration = userConfigurationService.getOrCreateForUpdate(userId)
        val previousLastSyncedAt = configuration.healthImportLastSyncedAt
        val requestMaxEndDatetime = request.records
            .map { convertToOffsetDateTime(it.endTimestamp) }
            .maxOrNull()

        val results = request.records.map { recordRequest ->
            importOne(userId, recordRequest, previousLastSyncedAt)
        }
        resolveNextLastSyncedAt(userId, previousLastSyncedAt, requestMaxEndDatetime)
            ?.let { configuration.markHealthImportSynced(it) }

        return ImportRunningBatchResponse(
            createdCount = results.count { it.status == ImportRunningRecordStatus.CREATED },
            updatedCount = results.count { it.status == ImportRunningRecordStatus.UPDATED },
            duplicateCount = results.count { it.status == ImportRunningRecordStatus.DUPLICATE },
            awardedPoint = results.sumOf { it.awardedPoint },
        )
    }

    private fun importOne(
        userId: Long,
        request: ImportRunningRecordRequest,
        previousLastSyncedAt: OffsetDateTime?
    ): ImportResult {
        val startDatetime = convertToOffsetDateTime(request.startTimestamp)
        val endDatetime = convertToOffsetDateTime(request.endTimestamp)

        val sameExternalRecord = runningRecordRepository.findByUserIdAndSourceAndExternalId(
            userId = userId,
            source = RunningRecordSource.HEALTH,
            externalId = request.externalId
        )

        if (sameExternalRecord != null) {
            sameExternalRecord.updateImported(request, startDatetime, endDatetime)
            replaceItems(sameExternalRecord.id, request)
            return ImportResult(
                status = ImportRunningRecordStatus.UPDATED,
                awardedPoint = 0,
            )
        }

        val overlapRecord = runningRecordRepository.findOverlapCandidates(userId, startDatetime, endDatetime)
            .firstOrNull { candidate -> isDuplicateCandidate(candidate, request, startDatetime, endDatetime) }

        if (overlapRecord != null) {
            if (shouldReplaceHealthRepresentative(overlapRecord, request)) {
                overlapRecord.updateImported(request, startDatetime, endDatetime)
                replaceItems(overlapRecord.id, request)
                val awardedPoint = awardPointIfNeeded(userId, overlapRecord)
                return ImportResult(
                    status = ImportRunningRecordStatus.UPDATED,
                    awardedPoint = awardedPoint,
                )
            }

            return ImportResult(
                status = ImportRunningRecordStatus.DUPLICATE,
                awardedPoint = 0,
            )
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val savedRecord = runningRecordRepository.saveAndFlush(
            RunningRecord(
                user = User(id = userId),
                shoe = null,
                distance = request.distance,
                durationSec = request.durationSec,
                cadence = request.cadence,
                heartRate = request.heartRate,
                calorie = request.calorie,
                isStatisticIncluded = true,
                isEnd = true,
                source = RunningRecordSource.HEALTH,
                externalId = request.externalId,
                importedDatetime = now,
                pointEligible = isPointEligibleForNewImport(previousLastSyncedAt, endDatetime),
                pointAwarded = false,
                startDatetime = startDatetime,
                endDatetime = endDatetime,
            )
        )
        replaceItems(savedRecord.id, request)
        val awardedPoint = awardPointIfNeeded(userId, savedRecord)

        return ImportResult(
            status = ImportRunningRecordStatus.CREATED,
            awardedPoint = awardedPoint,
        )
    }

    private fun resolveNextLastSyncedAt(
        userId: Long,
        previousLastSyncedAt: OffsetDateTime?,
        requestMaxEndDatetime: OffsetDateTime?
    ): OffsetDateTime? {
        if (previousLastSyncedAt == null) {
            if (requestMaxEndDatetime != null) return null

            return runningRecordRepository
                .findFirstByUserIdAndSourceAndEndDatetimeIsNotNullOrderByEndDatetimeDesc(
                    userId = userId,
                    source = RunningRecordSource.HEALTH
                )
                ?.endDatetime
                ?: OffsetDateTime.now(ZoneOffset.UTC)
        }

        if (requestMaxEndDatetime == null) return previousLastSyncedAt
        return if (requestMaxEndDatetime.isAfter(previousLastSyncedAt)) requestMaxEndDatetime else previousLastSyncedAt
    }

    private fun isPointEligibleForNewImport(
        previousLastSyncedAt: OffsetDateTime?,
        endDatetime: OffsetDateTime
    ): Boolean {
        return previousLastSyncedAt != null && endDatetime.isAfter(previousLastSyncedAt)
    }

    private fun RunningRecord.updateImported(
        request: ImportRunningRecordRequest,
        startDatetime: OffsetDateTime,
        endDatetime: OffsetDateTime
    ) {
        updateImported(
            distance = request.distance,
            durationSec = request.durationSec,
            cadence = request.cadence,
            heartRate = request.heartRate,
            calorie = request.calorie,
            startDatetime = startDatetime,
            endDatetime = endDatetime,
        )
    }

    private fun replaceItems(runningRecordId: Long, request: ImportRunningRecordRequest) {
        runningRecordItemRepository.deleteAllByRunningRecord_Id(runningRecordId)
        if (request.items.isEmpty()) return

        runningRecordItemRepository.saveInBatch(
            request.items.map { item ->
                RunningRecordItem(
                    runningRecord = RunningRecord(id = runningRecordId),
                    distance = item.distance,
                    durationSec = item.durationSec,
                    cadence = item.cadence,
                    heartRate = item.heartRate,
                    minHeartRate = item.minHeartRate,
                    maxHeartRate = item.maxHeartRate,
                    orderIndex = item.orderIndex,
                    startDatetime = convertToOffsetDateTime(item.startTimestamp),
                    endDatetime = convertToOffsetDateTime(item.endTimestamp),
                    gpsPointsJson = RunningRecordItemGpsCodec.encode(
                        item.gpsPoints.map { gpsPoint ->
                            com.example.running.domain.running.service.dto.RunningRecordItemGpsPointDto(
                                latitude = gpsPoint.latitude,
                                longitude = gpsPoint.longitude,
                                timestampMs = gpsPoint.timestampMs,
                                speed = gpsPoint.speed,
                                altitude = gpsPoint.altitude,
                                accuracy = gpsPoint.accuracy,
                            )
                        }
                    ),
                )
            }
        )
    }

    private fun isDuplicateCandidate(
        candidate: RunningRecord,
        request: ImportRunningRecordRequest,
        startDatetime: OffsetDateTime,
        endDatetime: OffsetDateTime
    ): Boolean {
        val candidateEnd = candidate.endDatetime ?: return false
        val startDiffSeconds = abs(candidate.startDatetime.toEpochSecond() - startDatetime.toEpochSecond())
        val endDiffSeconds = abs(candidateEnd.toEpochSecond() - endDatetime.toEpochSecond())
        val startsOrEndsClose = startDiffSeconds <= SAME_RUN_TIME_DIFF_SECONDS || endDiffSeconds <= SAME_RUN_TIME_DIFF_SECONDS

        val overlapSeconds = min(candidateEnd.toEpochSecond(), endDatetime.toEpochSecond()) -
            max(candidate.startDatetime.toEpochSecond(), startDatetime.toEpochSecond())
        val shorterDuration = min(
            max(1, candidateEnd.toEpochSecond() - candidate.startDatetime.toEpochSecond()),
            max(1, endDatetime.toEpochSecond() - startDatetime.toEpochSecond())
        )
        val mostlyOverlaps = overlapSeconds > 0 && overlapSeconds.toDouble() / shorterDuration >= SAME_RUN_OVERLAP_RATIO

        if (!startsOrEndsClose && !mostlyOverlaps) return false
        return distancesAreSimilar(candidate.distance, request.distance)
    }

    private fun distancesAreSimilar(left: Int, right: Int): Boolean {
        if (left <= 0 || right <= 0) return true
        val denominator = max(left, right)
        return abs(left - right).toDouble() / denominator <= SAME_RUN_DISTANCE_DIFF_RATIO
    }

    private fun shouldReplaceHealthRepresentative(existingRecord: RunningRecord, request: ImportRunningRecordRequest): Boolean {
        if (existingRecord.source == RunningRecordSource.LIVE) return false
        return importQualityScore(request) > recordQualityScore(existingRecord)
    }

    private fun importQualityScore(request: ImportRunningRecordRequest): Int {
        return listOf(
            if (request.items.any { it.gpsPoints.isNotEmpty() }) 100 else 0,
            if (request.heartRate > 0) 10 else 0,
            if (request.calorie > 0) 10 else 0,
            min(request.durationSec / 60, 9).toInt(),
        ).sum()
    }

    private fun recordQualityScore(record: RunningRecord): Int {
        val items = runningRecordItemRepository.findAllByRunningRecord_IdOrderByOrderIndexAsc(record.id)
        return listOf(
            if (items.any { !it.gpsPointsJson.isNullOrBlank() && it.gpsPointsJson != "[]" }) 100 else 0,
            if (record.heartRate > 0) 10 else 0,
            if (record.calorie > 0) 10 else 0,
            min(record.durationSec / 60, 9).toInt(),
        ).sum()
    }

    private fun awardPointIfNeeded(userId: Long, record: RunningRecord): Int {
        val point = record.distance / 100
        if (point <= 0) return 0

        val marked = runningRecordRepository.markPointAwardedIfEligible(userId, record.id)
        if (!marked) return 0

        record.pointAwarded = true
        userPointService.updatePoint(
            PointUsageDto(
                userId = userId,
                runningRecordId = record.id,
                point = point,
                pointTypeId = PointTypeName.RUNNING.id
            )
        )
        return point
    }

    private fun validateBatch(request: ImportRunningBatchRequest) {
        require(request.records.size <= MAX_BATCH_SIZE) { "한 번에 가져올 수 있는 러닝 기록은 최대 ${MAX_BATCH_SIZE}개입니다." }

        val externalIds = request.records.map { it.externalId.trim() }
        require(externalIds.all { it.isNotBlank() }) { "외부 러닝 기록 ID가 필요합니다." }
        require(externalIds.toSet().size == externalIds.size) { "중복된 외부 러닝 기록 ID가 있습니다." }

        request.records.forEach { record ->
            require(record.distance >= 0) { "러닝 거리는 0 이상이어야 합니다." }
            require(record.durationSec >= 0) { "러닝 시간은 0 이상이어야 합니다." }
            require(record.startTimestamp < record.endTimestamp) { "러닝 종료 시각은 시작 시각보다 늦어야 합니다." }
            require(record.items.size <= MAX_ITEM_COUNT_PER_RECORD) { "러닝 구간은 기록당 최대 ${MAX_ITEM_COUNT_PER_RECORD}개입니다." }
            val orderIndexes = record.items.map { it.orderIndex }
            require(orderIndexes.toSet().size == orderIndexes.size) { "러닝 구간 순서가 중복되었습니다." }
            record.items.forEach { item ->
                require(item.distance >= 0) { "러닝 구간 거리는 0 이상이어야 합니다." }
                require(item.durationSec >= 0) { "러닝 구간 시간은 0 이상이어야 합니다." }
                require(item.startTimestamp < item.endTimestamp) { "러닝 구간 종료 시각은 시작 시각보다 늦어야 합니다." }
                require(item.gpsPoints.size <= MAX_GPS_POINT_COUNT_PER_ITEM) {
                    "GPS 포인트는 구간당 최대 ${MAX_GPS_POINT_COUNT_PER_ITEM}개입니다."
                }
            }
        }
    }

    companion object {
        private const val MAX_BATCH_SIZE = 100
        private const val MAX_ITEM_COUNT_PER_RECORD = 1
        private const val MAX_GPS_POINT_COUNT_PER_ITEM = 5000
        private const val SAME_RUN_TIME_DIFF_SECONDS = 300
        private const val SAME_RUN_OVERLAP_RATIO = 0.8
        private const val SAME_RUN_DISTANCE_DIFF_RATIO = 0.2
    }

    private data class ImportResult(
        val status: ImportRunningRecordStatus,
        val awardedPoint: Int,
    )
}
