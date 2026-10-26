package com.example.running.domain.garmin.service.dto

import com.example.running.domain.running.controller.dto.ImportRunningRecordRequest

class GarminActivity(
    val garminUserId: String,
    val manual: Boolean,
    val record: ImportRunningRecordRequest,
)
