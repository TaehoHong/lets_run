package com.example.running.domain.running.service.dto

import java.time.OffsetDateTime

data class RunningRecordItemGpsPointDto(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val speed: Double,
    val altitude: Double,
    val accuracy: Double?,
)

class RunningRecordItemDto (
    val distance: Int,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val minHeartRate: Short,
    val maxHeartRate: Short,
    val orderIndex: Short,
    val startDateTime: OffsetDateTime,
    val endDateTime: OffsetDateTime,
    val gpsPoints: List<RunningRecordItemGpsPointDto> = emptyList(),
)
