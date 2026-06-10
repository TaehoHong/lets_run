package com.example.running.domain.running.controller.dto

class ImportRunningBatchRequest(
    val records: List<ImportRunningRecordRequest> = emptyList()
)

class ImportRunningRecordRequest(
    val externalId: String,
    val distance: Int,
    val durationSec: Long,
    val cadence: Short = 0,
    val heartRate: Short = 0,
    val calorie: Int = 0,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val items: List<ImportRunningRecordItemRequest> = emptyList(),
)

class ImportRunningRecordItemRequest(
    val distance: Int,
    val durationSec: Long,
    val cadence: Short = 0,
    val heartRate: Short = 0,
    val minHeartRate: Short = 0,
    val maxHeartRate: Short = 0,
    val orderIndex: Short,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val gpsPoints: List<RunningRecordItemGpsPointDto> = emptyList(),
)

enum class ImportRunningRecordStatus {
    CREATED,
    UPDATED,
    DUPLICATE
}

class ImportRunningBatchResponse(
    val createdCount: Int,
    val updatedCount: Int,
    val duplicateCount: Int,
    val awardedPoint: Int,
    val leagueDistanceChanged: Boolean = false,
)
