package com.example.running.domain.running.service.dto

import com.example.running.domain.running.entity.RunningRecord
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
    )
}
