package com.example.running.domain.running.controller.dto

import com.example.running.domain.running.service.dto.RunningRecordDto

class RunningRecordSearch {

    class Response(
        val id: Long,
        val distance: Long,
        val durationSec: Long,
        val cadence: Short,
        val heartRate: Short,
        val calorie: Int,
        val isUserInput: Boolean,
    ) {
        constructor(runningRecordDto: RunningRecordDto): this(
            id = runningRecordDto.id,
            distance = runningRecordDto.distance,
            durationSec = runningRecordDto.durationSec,
            cadence = runningRecordDto.cadence,
            heartRate = runningRecordDto.heartRate,
            calorie = runningRecordDto.calorie,
            isUserInput = runningRecordDto.isUserInput
        )
    }

}