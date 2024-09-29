package com.example.running.domain.running.controller.dto

import jakarta.validation.constraints.Min
import org.jetbrains.annotations.NotNull


class PostRequest(

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
)