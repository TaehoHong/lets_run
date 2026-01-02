package com.example.running.domain.running.service.dto

import com.example.running.domain.running.entity.RunningRecord

class EndRunningDto(
    val userId: Long,
    val shoeId: Long?,
    val runningRecordId: Long,
    val distance: Int,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val calorie: Int,
    val point: Int,
) {
    constructor(record: RunningRecord, point: Int): this(
        userId = record.user.id,
        runningRecordId = record.user.id,
        shoeId = record.shoe?.id,
        distance = record.distance,
        durationSec = record.durationSec,
        cadence = record.cadence,
        heartRate = record.heartRate,
        calorie = record.calorie,
        point = point
    )
}