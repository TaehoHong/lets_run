package com.example.running.domain.running.controller.dto

import com.example.running.utils.convertToOffsetDateTime
import java.time.OffsetDateTime

class CreationRunningRecord(
    val startTimestamp: Long? = null,
) {
    fun getStartDatetime(): OffsetDateTime {
        return startTimestamp
            ?.let { convertToOffsetDateTime(it) }
            ?: OffsetDateTime.now()
    }
}