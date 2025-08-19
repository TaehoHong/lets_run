package com.example.running.domain.running.service.dto

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningStatisticType

class RunningStatistics(
    runningRecords: List<RunningRecord>, statisticType: RunningStatisticType
){
    val statisticType: RunningStatisticType
    val totalDistance: Long
    val totalDurationSec: Long
    val averageDistance: Int
    val averagePaceSec: Double
    val runningCount: Int

    init {

        this.statisticType = statisticType;
        this.totalDistance = runningRecords.sumOf { it.distance }
        this.totalDurationSec = runningRecords.sumOf { it.durationSec }
        this.averageDistance = (this.totalDistance / runningRecords.size).toInt()
        this.averagePaceSec = (this.totalDurationSec.toDouble() / this.totalDistance.toDouble())
        this.runningCount = runningRecords.size
    }



}