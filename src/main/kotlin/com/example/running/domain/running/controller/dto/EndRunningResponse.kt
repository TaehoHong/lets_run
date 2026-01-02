package com.example.running.domain.running.controller.dto

import com.example.running.domain.running.service.dto.EndRunningDto

class EndRunningResponse(
    val id: Long,
    val shoeId: Long?,
    val distance: Int,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val calorie: Int,
    val point: Int
) {
    constructor(dto: EndRunningDto) : this(
        id = dto.runningRecordId,
        shoeId = dto.shoeId,
        distance = dto.distance,
        durationSec = dto.durationSec,
        cadence = dto.cadence,
        heartRate = dto.heartRate,
        calorie = dto.calorie,
        point = dto.point
    )
}