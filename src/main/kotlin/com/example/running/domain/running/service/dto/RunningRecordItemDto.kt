package com.example.running.domain.running.service.dto

import java.time.OffsetDateTime

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
)