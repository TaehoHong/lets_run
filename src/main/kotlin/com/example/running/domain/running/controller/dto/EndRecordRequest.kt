package com.example.running.domain.running.controller.dto

import java.time.OffsetDateTime

class EndRecordRequest(
    val distance: Long,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val calorie: Int,
    val endTimestamp: Long = OffsetDateTime.now().toEpochSecond(),
) {
}