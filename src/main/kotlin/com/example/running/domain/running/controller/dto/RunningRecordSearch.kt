package com.example.running.domain.running.controller.dto

import com.example.running.domain.running.service.dto.RunningRecordDetailDto
import com.example.running.domain.running.service.dto.RunningRecordDto
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import com.example.running.domain.running.enums.RunningPointExclusionReason
import com.example.running.domain.running.enums.RunningRecordingMethod
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.shoe.controller.dto.ShoeResponse
import com.example.running.utils.convertToOffsetDateTime
import java.time.OffsetDateTime

class RunningRecordSearchRequest(
    val cursor: Long? = null,
    val size: Int = 10,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
) {
    fun getStartDateTime(): OffsetDateTime? {
        return this.startTimestamp?.let {
            convertToOffsetDateTime(it)
        }
    }

    fun getEndDateTime(): OffsetDateTime? {
        return this.endTimestamp?.let {
            convertToOffsetDateTime(it)
        }
    }
}

class RunningRecordSearchResponse(
    val id: Long,
    val distance: Int,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val calorie: Int,
    val isUserInput: Boolean,
    val startTimestamp: Long,
    val source: RunningRecordSource = RunningRecordSource.LIVE,
    val sourceAppId: String? = null,
    val sourceAppName: String? = null,
    val recordingMethod: RunningRecordingMethod? = null,
    val pointExclusionReason: RunningPointExclusionReason? = null,
    val acceptanceStatus: RunningAcceptanceStatus = RunningAcceptanceStatus.PENDING,
    val pointEligible: Boolean = true,
    val pointAwarded: Boolean = false,
) {
    constructor(runningRecordDto: RunningRecordDto) : this(
        id = runningRecordDto.id,
        distance = runningRecordDto.distance,
        durationSec = runningRecordDto.durationSec,
        cadence = runningRecordDto.cadence,
        heartRate = runningRecordDto.heartRate,
        calorie = runningRecordDto.calorie,
        isUserInput = runningRecordDto.isUserInput,
        startTimestamp = runningRecordDto.startDatetime.toEpochSecond(),
        source = runningRecordDto.source,
        sourceAppId = runningRecordDto.sourceAppId,
        sourceAppName = runningRecordDto.sourceAppName,
        recordingMethod = runningRecordDto.recordingMethod,
        pointExclusionReason = runningRecordDto.pointExclusionReason,
        acceptanceStatus = runningRecordDto.acceptanceStatus,
        pointEligible = runningRecordDto.pointEligible,
        pointAwarded = runningRecordDto.pointAwarded,
    )
}

class RunningRecordDetailResponse(
    val id: Long,
    val distance: Int,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val calorie: Int,
    val isUserInput: Boolean,
    val startTimestamp: Long,
    val shoeId: Long?,
    val connectedShoe: ShoeResponse?,
    val source: RunningRecordSource = RunningRecordSource.LIVE,
    val sourceAppId: String? = null,
    val sourceAppName: String? = null,
    val recordingMethod: RunningRecordingMethod? = null,
    val pointExclusionReason: RunningPointExclusionReason? = null,
    val acceptanceStatus: RunningAcceptanceStatus = RunningAcceptanceStatus.PENDING,
    val pointEligible: Boolean = true,
    val pointAwarded: Boolean = false,
) {
    constructor(runningRecordDetailDto: RunningRecordDetailDto) : this(
        id = runningRecordDetailDto.id,
        distance = runningRecordDetailDto.distance,
        durationSec = runningRecordDetailDto.durationSec,
        cadence = runningRecordDetailDto.cadence,
        heartRate = runningRecordDetailDto.heartRate,
        calorie = runningRecordDetailDto.calorie,
        isUserInput = runningRecordDetailDto.isUserInput,
        startTimestamp = runningRecordDetailDto.startDatetime.toEpochSecond(),
        shoeId = runningRecordDetailDto.shoeId,
        connectedShoe = runningRecordDetailDto.connectedShoe?.let { ShoeResponse(it) },
        source = runningRecordDetailDto.source,
        sourceAppId = runningRecordDetailDto.sourceAppId,
        sourceAppName = runningRecordDetailDto.sourceAppName,
        recordingMethod = runningRecordDetailDto.recordingMethod,
        pointExclusionReason = runningRecordDetailDto.pointExclusionReason,
        acceptanceStatus = runningRecordDetailDto.acceptanceStatus,
        pointEligible = runningRecordDetailDto.pointEligible,
        pointAwarded = runningRecordDetailDto.pointAwarded,
    )
}

class UpdateRunningRecordShoeRequest(
    val shoeId: Long
)
