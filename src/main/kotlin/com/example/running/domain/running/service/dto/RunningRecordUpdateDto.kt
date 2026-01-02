package com.example.running.domain.running.service.dto

import java.time.OffsetDateTime

class RunningRecordUpdateDto(
    val userId: Long,
    val runningRecordId: Long,
    val shoeId: Long? = null,
    val distance: Int? = null,
    val durationSec: Long? = null,
    val cadence: Short? = null,
    val heartRate: Short? = null,
    val calorie: Int? = null,
    val startDatetime: OffsetDateTime? = null,
    val endDatetime: OffsetDateTime? = null
) {
}