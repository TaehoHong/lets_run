package com.example.running.domain.running.controller.dto

import com.example.running.domain.running.service.dto.RunningRecordItemGpsPointDto
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

data class CompletedRunningRequest(
    @JsonSetter(nulls = Nulls.FAIL) val schemaVersion: Int,
    @JsonSetter(nulls = Nulls.FAIL) val sessionId: String,
    @JsonSetter(nulls = Nulls.FAIL) val ownerUserId: Long,
    @JsonSetter(nulls = Nulls.FAIL) val source: String,
    @JsonSetter(nulls = Nulls.FAIL) val startTimestamp: Long,
    @JsonSetter(nulls = Nulls.FAIL) val endTimestamp: Long,
    @JsonSetter(nulls = Nulls.FAIL) val distance: Int,
    @JsonSetter(nulls = Nulls.FAIL) val durationSec: Long,
    @JsonSetter(nulls = Nulls.FAIL) val cadence: Int,
    @JsonSetter(nulls = Nulls.FAIL) val heartRate: Int,
    @JsonSetter(nulls = Nulls.FAIL) val calorie: Int,
    @JsonSetter(nulls = Nulls.FAIL) val gpsPoints: List<RunningRecordItemGpsPointDto>,
)

data class CompletedRunningResponse(val sessionId: String, val runningRecordId: Long, val status: String)
