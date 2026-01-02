package com.example.running.domain.running.service.dto

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningStatisticType
import java.time.LocalDate
import java.time.OffsetDateTime

class RunningStatistics(
    runningRecords: List<RunningRecord>, statisticType: RunningStatisticType
) {
    val statisticType: RunningStatisticType
    val chartData: List<RunningChartDto>
    val totalDistance: Int
    val totalDurationSec: Long
    val averageDistance: Int
    val averagePaceSec: Double
    val runningCount: Int

    init {
        this.statisticType = statisticType
        this.totalDistance = runningRecords.sumOf { it.distance }
        this.totalDurationSec = runningRecords.sumOf { it.durationSec }
        this.averageDistance = if (runningRecords.isNotEmpty()) {
            (this.totalDistance / runningRecords.size).toInt()
        } else 0
        this.averagePaceSec = (this.totalDurationSec.toDouble() / this.totalDistance.toDouble())
        this.runningCount = runningRecords.size
        this.chartData = makeChartData(runningRecords)
    }

    private fun makeChartData(runningRecords: List<RunningRecord>): List<RunningChartDto> {
        return runningRecords.groupBy { groupKeyByStatisticType(it.startDatetime) }
            .map { entry ->
                entry.value.fold(RunningChartDto(entry.key.toString())) { dto, record ->
                    dto.addData(record.distance, record.durationSec)
                }
            }
    }

    fun groupKeyByStatisticType(dateTime: OffsetDateTime): LocalDate {

        return when (this.statisticType) {
            RunningStatisticType.WEEKLY, RunningStatisticType.MONTHLY -> dateTime.toLocalDate()
            RunningStatisticType.YEARLY -> dateTime.toLocalDate().withDayOfMonth(1)
        }
    }
}

data class RunningChartDto(
    var datetime: String,
    var paceSec: Double = 0.0,
    var distance: Int = 0,
    var durationSec: Long = 0
) {
    fun addData(distance: Int, durationSec: Long): RunningChartDto {
        this.distance += distance
        this.durationSec += durationSec
        this.paceSec = this.durationSec.toDouble() / (distance / 1000.0)
        return this
    }
}