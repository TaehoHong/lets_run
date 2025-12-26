package com.example.running.domain.running.controller.dto

import com.example.running.utils.convertToOffsetDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class CreationRunningRecord(
    val startTimestamp: Long? = null,
) {
    fun getStartDatetime(): OffsetDateTime {
        return startTimestamp
            ?.let { convertToOffsetDateTime(it) }
            ?: OffsetDateTime.now(ZoneOffset.UTC)
    }
}