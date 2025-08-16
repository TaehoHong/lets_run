package com.example.running.domain.running.controller.dto

class EndRunningRequest(
    val distance: Long,
    val durationSec: Long,
    val cadence: Short,
    val heartRate: Short,
    val calorie: Int
)