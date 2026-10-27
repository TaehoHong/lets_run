package com.example.running.domain.running.service.dto

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import com.example.running.domain.running.enums.RunningPointExclusionReason
import com.example.running.domain.running.enums.RunningRecordingMethod
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.shoe.service.dto.ShoeDto
import java.time.OffsetDateTime

class RunningRecordDto(
    val id: Long,
    val distance: Int,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val calorie: Int,
    val isUserInput: Boolean,
    val startDatetime: OffsetDateTime,
    val source: RunningRecordSource = RunningRecordSource.LIVE,
    val sourceAppId: String? = null,
    val sourceAppName: String? = null,
    val recordingMethod: RunningRecordingMethod? = null,
    val pointExclusionReason: RunningPointExclusionReason? = null,
    val acceptanceStatus: RunningAcceptanceStatus = RunningAcceptanceStatus.PENDING,
    val pointEligible: Boolean = true,
    val pointAwarded: Boolean = false,
) {
    constructor(runningRecord: RunningRecord): this(
        id = runningRecord.id,
        distance = runningRecord.distance,
        durationSec = runningRecord.durationSec,
        cadence = runningRecord.cadence,
        heartRate = runningRecord.heartRate,
        calorie = runningRecord.calorie,
        isUserInput = runningRecord.isUserInput,
        startDatetime = runningRecord.startDatetime,
        source = runningRecord.source,
        sourceAppId = runningRecord.sourceAppId,
        sourceAppName = runningRecord.sourceAppName,
        recordingMethod = runningRecord.recordingMethod,
        pointExclusionReason = runningRecord.pointExclusionReason,
        acceptanceStatus = runningRecord.acceptanceStatus,
        pointEligible = runningRecord.pointEligible,
        pointAwarded = runningRecord.pointAwarded,
    )
}

class RunningRecordDetailDto(
    val id: Long,
    val distance: Int,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val calorie: Int,
    val isUserInput: Boolean,
    val startDatetime: OffsetDateTime,
    val shoeId: Long?,
    val connectedShoe: ShoeDto?,
    val source: RunningRecordSource = RunningRecordSource.LIVE,
    val sourceAppId: String? = null,
    val sourceAppName: String? = null,
    val recordingMethod: RunningRecordingMethod? = null,
    val pointExclusionReason: RunningPointExclusionReason? = null,
    val acceptanceStatus: RunningAcceptanceStatus = RunningAcceptanceStatus.PENDING,
    val pointEligible: Boolean = true,
    val pointAwarded: Boolean = false,
) {
    constructor(runningRecord: RunningRecord): this(
        id = runningRecord.id,
        distance = runningRecord.distance,
        durationSec = runningRecord.durationSec,
        cadence = runningRecord.cadence,
        heartRate = runningRecord.heartRate,
        calorie = runningRecord.calorie,
        isUserInput = runningRecord.isUserInput,
        startDatetime = runningRecord.startDatetime,
        shoeId = runningRecord.shoe?.id,
        connectedShoe = runningRecord.shoe
            ?.takeUnless { it.isDeleted }
            ?.let { ShoeDto(it) },
        source = runningRecord.source,
        sourceAppId = runningRecord.sourceAppId,
        sourceAppName = runningRecord.sourceAppName,
        recordingMethod = runningRecord.recordingMethod,
        pointExclusionReason = runningRecord.pointExclusionReason,
        acceptanceStatus = runningRecord.acceptanceStatus,
        pointEligible = runningRecord.pointEligible,
        pointAwarded = runningRecord.pointAwarded,
    )
}
