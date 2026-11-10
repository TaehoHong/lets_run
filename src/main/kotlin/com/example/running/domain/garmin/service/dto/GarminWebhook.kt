package com.example.running.domain.garmin.service.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

class GarminWebhook(
    val activities: List<GarminActivitySummary> = emptyList(),
    val activityDetails: List<GarminActivityDetails> = emptyList(),
    val manuallyUpdatedActivities: List<GarminActivitySummary> = emptyList(),
    val deregistrations: List<GarminUserEvent> = emptyList(),
    val userPermissionsChange: List<GarminUserEvent> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
class GarminActivitySummary(
    val userId: String? = null,
    @JsonDeserialize(using = GarminActivityIdDeserializer::class)
    val activityId: String? = null,
    val activityType: String? = null,
    val startTimeInSeconds: Long? = null,
    val durationInSeconds: Long? = null,
    val distanceInMeters: Double? = null,
    val averageHeartRateInBeatsPerMinute: Double? = null,
    val averageRunCadenceInStepsPerMinute: Double? = null,
    val activeKilocalories: Double? = null,
    val manual: Boolean = false,
    val samples: List<GarminSample> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
class GarminActivityDetails(
    val userId: String,
    @JsonDeserialize(using = GarminActivityIdDeserializer::class)
    val activityId: String,
    val summary: GarminActivitySummary,
    val samples: List<GarminSample> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
class GarminSample(
    val startTimeInSeconds: Long? = null,
    @JsonSetter(nulls = Nulls.FAIL)
    val latitudeInDegree: Double? = null,
    @JsonSetter(nulls = Nulls.FAIL)
    val longitudeInDegree: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val elevationInMeters: Double? = null,
    val totalDistanceInMeters: Double? = null,
    val timerDurationInSeconds: Double? = null,
    val heartRate: Double? = null,
    val stepsPerMinute: Double? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class GarminUserEvent(val userId: String)

class GarminActivityIdDeserializer : JsonDeserializer<String>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): String {
        if (parser.currentToken != JsonToken.VALUE_STRING && parser.currentToken != JsonToken.VALUE_NUMBER_INT) {
            throw JsonMappingException.from(parser, "Invalid Garmin activity identity")
        }
        return parser.text
    }
}
