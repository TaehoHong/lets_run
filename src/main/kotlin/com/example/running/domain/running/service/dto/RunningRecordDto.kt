package com.example.running.domain.running.service.dto

import com.example.running.domain.running.entity.RunningRecord

class RunningRecordDto(
    val id: Long,
    val distance: Long,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val calorie: Int,
    val isUserInput: Boolean,
) {
    constructor(runningRecord: RunningRecord): this(
        id = runningRecord.id,
        distance = runningRecord.distance,
        durationSec = runningRecord.durationSec,
        cadence = runningRecord.cadence,
        heartRate = runningRecord.heartRate,
        calorie = runningRecord.calorie,
        isUserInput = runningRecord.isUserInput
    )
}