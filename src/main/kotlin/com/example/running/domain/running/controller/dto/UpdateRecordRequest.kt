package com.example.running.domain.running.controller.dto

class UpdateRecordRequest(
    val shoeId: Long? = null,
    val distance: Long? = null,
    val durationSec: Long? = null,
    val cadence: Short? = null,
    val heartRate: Short? = null,
    val calorie: Int? = null,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
)