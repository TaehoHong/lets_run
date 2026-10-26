package com.example.running.domain.running.service

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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class RunningImportService(
    private val runningRecordRepository: RunningRecordRepository,
    private val runningRecordItemRepository: RunningRecordItemRepository,
    private val userConfigurationService: UserConfigurationService,
    private val acceptance: RunningAcceptanceService,
    private val validation: RunningValidationService,
) {

    @Transactional(rollbackFor = [Exception::class])
    fun importBatch(userId: Long, request: ImportRunningBatchRequest, receivedAt: Instant = Instant.now()): ImportRunningBatchResponse =
        importRecords(userId, request, RunningRecordSource.HEALTH, null, receivedAt = receivedAt)

    @Transactional(rollbackFor = [Exception::class])
    fun importGarmin(userId: Long, request: ImportRunningBatchRequest, connectedAt: OffsetDateTime, manual: Boolean = false, receivedAt: Instant = Instant.now()): ImportRunningBatchResponse =
        importRecords(userId, request, RunningRecordSource.GARMIN, connectedAt, manual, receivedAt)

    private fun importRecords(userId: Long, request: ImportRunningBatchRequest,
                              source: RunningRecordSource, connectedAt: OffsetDateTime?, manual: Boolean = false, receivedAt: Instant): ImportRunningBatchResponse {
        validateBatch(request, receivedAt)
        logger.info(
            "Running import batch received: recordCount={}, recordsWithItems={}, totalGpsPoints={}",
            request.records.size,
            request.records.count { it.items.isNotEmpty() },
            request.records.sumOf { record -> record.items.sumOf { item -> item.gpsPoints.size } },
        )

        val configuration = userConfigurationService.getOrCreateForUpdate(userId)
        if (source == RunningRecordSource.HEALTH && !configuration.healthImportEnabled) {
            logger.info("Health import batch skipped: disabled")
            return ImportRunningBatchResponse(
                createdCount = 0,
                updatedCount = 0,
                duplicateCount = 0,
                awardedPoint = 0,
                leagueDistanceChanged = false,
                processed = false,
            )
        }

        val previousLastSyncedAt = configuration.healthImportLastSyncedAt
        val rewardCutoffAt = if (source == RunningRecordSource.GARMIN) connectedAt else
            configuration.ensureHealthImportRewardCutoff(receivedAt.atOffset(ZoneOffset.UTC))
        val requestMaxEndDatetime = request.records
            .map { validation.timestamp(it.endTimestamp) }
            .maxOrNull()

        val results = request.records.map { recordRequest ->
            importOne(userId, recordRequest, rewardCutoffAt, source, manual, receivedAt)
        }
        if (source == RunningRecordSource.HEALTH) {
            resolveNextLastSyncedAt(userId, previousLastSyncedAt, requestMaxEndDatetime)
                ?.let { configuration.markHealthImportLastSynced(it) }
        }
        val leagueDistanceChanged = results.any { it.leagueDistanceChanged }

        logger.info(
            "Running import batch completed: recordCount={}, createdCount={}, updatedCount={}, duplicateCount={}, leagueDistanceChanged={}",
            request.records.size,
            results.count { it.status == ImportRunningRecordStatus.CREATED },
            results.count { it.status == ImportRunningRecordStatus.UPDATED },
            results.count { it.status == ImportRunningRecordStatus.DUPLICATE },
            leagueDistanceChanged,
        )

        return ImportRunningBatchResponse(
            createdCount = results.count { it.status == ImportRunningRecordStatus.CREATED },
            updatedCount = results.count { it.status == ImportRunningRecordStatus.UPDATED },
            duplicateCount = results.count { it.status == ImportRunningRecordStatus.DUPLICATE },
            awardedPoint = results.sumOf { it.awardedPoint },
            leagueDistanceChanged = leagueDistanceChanged,
            personalOnlyCount = results.count { it.personalOnly },
        )
    }

    private fun importOne(
        userId: Long,
        request: ImportRunningRecordRequest,
        rewardCutoffAt: OffsetDateTime?,
        source: RunningRecordSource,
        manual: Boolean,
        receivedAt: Instant,
    ): ImportResult {
        val startDatetime = validation.timestamp(request.startTimestamp)
        val endDatetime = validation.timestamp(request.endTimestamp)

        val sameExternalRecord = runningRecordRepository.findByUserIdAndSourceAndExternalId(
            userId = userId,
            source = source,
            externalId = request.externalId
        )

        if (sameExternalRecord != null) {
            sameExternalRecord.updateImported(request, startDatetime, endDatetime)
            if (request.items.isNotEmpty()) {
                replaceItems(sameExternalRecord.id, request)
            } else {
                logger.info("Running import update preserved existing items: incomingItemsEmpty=true")
            }
            return ImportResult(
                status = if (sameExternalRecord.acceptanceStatus == RunningAcceptanceStatus.DUPLICATE) ImportRunningRecordStatus.DUPLICATE else ImportRunningRecordStatus.UPDATED,
                awardedPoint = 0,
                personalOnly = sameExternalRecord.acceptanceStatus == RunningAcceptanceStatus.PERSONAL_ONLY,
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
                isEnd = false,
                source = source,
                externalId = request.externalId,
                importedDatetime = now,
                pointEligible = false,
                pointAwarded = false,
                startDatetime = startDatetime,
                endDatetime = endDatetime,
            )
        )
        replaceItems(savedRecord.id, request)
        val recorded = if (source == RunningRecordSource.GARMIN) !manual else request.recordingMethod == "RECORDED"
        val result = acceptance.accept(userId, savedRecord, recorded,
            isPointEligibleForNewImport(rewardCutoffAt, endDatetime), receivedAt)

        return ImportResult(
            status = if (savedRecord.acceptanceStatus == RunningAcceptanceStatus.DUPLICATE) ImportRunningRecordStatus.DUPLICATE else ImportRunningRecordStatus.CREATED,
            awardedPoint = result.awardedPoint,
            leagueDistanceChanged = result.leagueDistanceChanged,
            personalOnly = savedRecord.acceptanceStatus == RunningAcceptanceStatus.PERSONAL_ONLY,
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
        rewardCutoffAt: OffsetDateTime?,
        endDatetime: OffsetDateTime
    ): Boolean {
        return rewardCutoffAt != null && endDatetime.isAfter(rewardCutoffAt)
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
                    startDatetime = validation.timestamp(item.startTimestamp),
                    endDatetime = validation.timestamp(item.endTimestamp),
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

    private fun validateBatch(request: ImportRunningBatchRequest, receivedAt: Instant) {
        validation.metrics(request.records.size <= MAX_BATCH_SIZE)
        val ids = request.records.map { it.externalId.trim() }
        validation.metrics(ids.all { it.isNotBlank() && it.length <= 191 } && ids.toSet().size == ids.size)
        request.records.forEach { record ->
            validation.metrics(record.recordingMethod == null || record.recordingMethod in setOf("RECORDED", "MANUAL", "UNKNOWN"))
            validation.metrics(record.sourceBundleId == null || record.sourceBundleId.length <= 255)
            validation.metrics(record.startTimestamp in 0..253402300799 && record.endTimestamp in 0..253402300799)
            val start = validation.timestamp(record.startTimestamp)
            val end = validation.timestamp(record.endTimestamp)
            validation.validate(start, end, record.distance, record.durationSec, record.cadence, record.heartRate, record.calorie, receivedAt)
            validation.metrics(record.items.size <= MAX_ITEM_COUNT_PER_RECORD)
            validation.metrics(record.items.map { it.orderIndex }.toSet().size == record.items.size)
            record.items.forEach { item ->
                validation.metrics(item.startTimestamp >= record.startTimestamp && item.endTimestamp <= record.endTimestamp)
                validation.metrics(item.minHeartRate in 0..255 && item.maxHeartRate in 0..255)
                validation.validate(validation.timestamp(item.startTimestamp), validation.timestamp(item.endTimestamp),
                    item.distance, item.durationSec, item.cadence, item.heartRate, 0, receivedAt,
                    item.gpsPoints.map { com.example.running.domain.running.service.dto.RunningRecordItemGpsPointDto(
                        it.latitude, it.longitude, it.timestampMs, it.speed, it.altitude, it.accuracy) })
            }
        }
    }

    companion object {
        private const val MAX_BATCH_SIZE = 100
        private const val MAX_ITEM_COUNT_PER_RECORD = 1
        private val logger = LoggerFactory.getLogger(RunningImportService::class.java)
    }

    private data class ImportResult(
        val status: ImportRunningRecordStatus,
        val awardedPoint: Int,
        val leagueDistanceChanged: Boolean = false,
        val personalOnly: Boolean = false,
    )
}
