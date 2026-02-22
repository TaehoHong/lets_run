package com.example.running.domain.running.controller.dto

import com.example.running.domain.running.service.dto.RunningRecordItemDto
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.jetbrains.annotations.NotNull


class PostRequest(
    @Valid
    val items: List<CreationRunningRecordItemDto>
)

class GetRunningRecordItemsResponse(
    val items: List<RunningRecordItemResponseDto>
)

class RunningRecordItemResponseDto(
    val distance: Int,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val minHeartRate: Short,
    val maxHeartRate: Short,
    val orderIndex: Short,
    val startTimeStamp: Long,
    val endTimeStamp: Long,
    val gpsPoints: List<RunningRecordItemGpsPointDto>,
) {
    constructor(dto: RunningRecordItemDto) : this(
        distance = dto.distance,
        durationSec = dto.durationSec,
        cadence = dto.cadence,
        heartRate = dto.heartRate,
        minHeartRate = dto.minHeartRate,
        maxHeartRate = dto.maxHeartRate,
        orderIndex = dto.orderIndex,
        startTimeStamp = dto.startDateTime.toEpochSecond(),
        endTimeStamp = dto.endDateTime.toEpochSecond(),
        gpsPoints = dto.gpsPoints.map {
            RunningRecordItemGpsPointDto(
                latitude = it.latitude,
                longitude = it.longitude,
                timestampMs = it.timestampMs,
                speed = it.speed,
                altitude = it.altitude,
                accuracy = it.accuracy,
            )
        },
    )
}

class RunningRecordItemGpsPointDto(
    @NotNull
    val latitude: Double,
    @NotNull
    val longitude: Double,
    @NotNull
    @Min(0)
    val timestampMs: Long,
    @NotNull
    val speed: Double,
    @NotNull
    val altitude: Double,
    val accuracy: Double?,
)

class CreationRunningRecordItemDto (
    @NotNull
    @Min(0)
    val distance: Int,

    @NotNull
    @Min(0)
    val durationSec: Long,

    @NotNull
    @Min(0)
    val cadence: Short,

    @NotNull
    @Min(0)
    val heartRate: Short,

    @NotNull
    @Min(0)
    val minHeartRate: Short,

    @NotNull
    @Min(0)
    val maxHeartRate: Short,

    @NotNull
    @Min(0)
    val orderIndex: Short,

    @NotNull
    @Min(0)
    val startTimeStamp: Long,

    @NotNull
    @Min(0)
    val endTimeStamp: Long,

    @Valid
    val gpsPoints: List<RunningRecordItemGpsPointDto>? = null,
)
