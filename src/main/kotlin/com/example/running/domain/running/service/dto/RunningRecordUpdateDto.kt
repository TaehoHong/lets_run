package com.example.running.domain.running.service.dto

import java.time.OffsetDateTime

class RunningRecordUpdateDto(
    val userId: Long,
    val runningRecordId: Long,
    val distance: Long,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val calorie: Int,
    val endDateTime: OffsetDateTime
) {
}