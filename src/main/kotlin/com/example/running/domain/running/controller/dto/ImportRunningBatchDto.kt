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
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = OptionalRunningStringDeserializer::class)
    val recordingMethod: String? = null,
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = OptionalRunningStringDeserializer::class)
    val sourceBundleId: String? = null,
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
    val personalOnlyCount: Int = 0,
)

/** Prevent Jackson string coercion from accepting numeric or boolean provider metadata. */
class OptionalRunningStringDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<String>() {
    override fun deserialize(parser: com.fasterxml.jackson.core.JsonParser, context: com.fasterxml.jackson.databind.DeserializationContext): String {
        if (parser.currentToken != com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
            throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "Invalid running metadata")
        }
        return parser.text
    }
}
