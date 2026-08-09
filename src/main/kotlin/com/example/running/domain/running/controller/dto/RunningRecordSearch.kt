package com.example.running.domain.running.controller.dto

import com.example.running.domain.running.service.dto.RunningRecordDetailDto
import com.example.running.domain.running.service.dto.RunningRecordDto
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
    )
}

class UpdateRunningRecordShoeRequest(
    val shoeId: Long
)
