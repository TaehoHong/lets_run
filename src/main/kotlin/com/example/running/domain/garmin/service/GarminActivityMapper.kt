package com.example.running.domain.garmin.service

import com.example.running.domain.garmin.service.dto.GarminActivity
import com.example.running.domain.running.controller.dto.*
import com.example.running.exception.GarminRequestException
import com.example.running.exception.RunningValidationException
import com.example.running.exception.RunningValidationReason
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

@Component
class GarminActivityMapper {
    fun map(activity: JsonNode): GarminActivity? {
        val summary = activity.path("summary").takeIf { it.isObject } ?: activity
        if (summary.path("activityType").asText() !in runningTypes) return null
        val userId = activity.path("userId").asText()
        val id = activity.path("activityId")
        if (userId.isBlank() || userId.length > 255 ||
            !(id.isTextual || id.isIntegralNumber) || id.asText().isBlank() || id.asText().length > 191) invalid()
        val start = seconds(summary, "startTimeInSeconds")
        val duration = seconds(summary, "durationInSeconds")
        if (start <= 0 || duration <= 0 || duration > 86400) invalid()
        val sampleNode = activity.path("samples")
        if (sampleNode.isArray && sampleNode.size() > GarminInputLimits.MAX_SAMPLES) invalid()
        val samples = sampleNode.takeIf { it.isArray }?.toList().orEmpty()
        val end = maxOf(Math.addExact(start, duration), samples.maxOfOrNull { seconds(it, "startTimeInSeconds") } ?: 0)
        if (end - start > 86400) invalid()
        val distance = metric(summary, "distanceInMeters").roundToInt()
        val heartRate = shortMetric(summary, "averageHeartRateInBeatsPerMinute")
        val cadence = shortMetric(summary, "averageRunCadenceInStepsPerMinute")
        val points = try {
            samples.mapNotNull { sample ->
                val latitude = sample.path("latitudeInDegree")
                val longitude = sample.path("longitudeInDegree")
                if (latitude.isMissingNode && longitude.isMissingNode) return@mapNotNull null
                if (!latitude.isNumber || !longitude.isNumber) invalid()
                val lat = latitude.asDouble()
                val lon = longitude.asDouble()
                if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) invalid()
                val timestamp = seconds(sample, "startTimeInSeconds")
                if (timestamp < start || timestamp > end) invalid()
                RunningRecordItemGpsPointDto(
                    lat,
                    lon,
                    Math.multiplyExact(timestamp, 1000),
                    metric(sample, "speedMetersPerSecond"),
                    metric(sample, "elevationInMeters", signed = true),
                    null,
                )
            }
        } catch (_: GarminRequestException) {
            throw RunningValidationException(RunningValidationReason.RUNNING_INVALID_GPS)
        } catch (_: ArithmeticException) {
            throw RunningValidationException(RunningValidationReason.RUNNING_INVALID_GPS)
        }
        val manual = summary.path("manual")
        if (!manual.isMissingNode && !manual.isBoolean) invalid()
        return GarminActivity(
            userId,
            manual.asBoolean(false),
            ImportRunningRecordRequest(
                externalId = id.asText(),
                distance = distance,
                durationSec = duration,
                cadence = cadence,
                heartRate = heartRate,
                calorie = metric(summary, "activeKilocalories").roundToInt(),
                startTimestamp = start,
                endTimestamp = end,
                items = if (points.isEmpty()) emptyList() else listOf(
                    ImportRunningRecordItemRequest(
                        distance,
                        duration,
                        cadence,
                        heartRate,
                        shortMetric(summary, "minHeartRateInBeatsPerMinute"),
                        shortMetric(summary, "maxHeartRateInBeatsPerMinute"),
                        0,
                        start,
                        end,
                        points,
                    )
                ),
            )
        )
    }

    private fun seconds(node: JsonNode, field: String): Long {
        val value = node.path(field)
        if (!value.isIntegralNumber || !value.canConvertToLong() || value.asLong() < 0) invalid()
        return value.asLong()
    }

    private fun metric(node: JsonNode, field: String, signed: Boolean = false): Double {
        val value = node.path(field)
        if (value.isMissingNode || value.isNull) return 0.0
        val number = value.asDouble(Double.NaN)
        if (!value.isNumber || !number.isFinite() || (!signed && number < 0) || number > Int.MAX_VALUE || number < Int.MIN_VALUE) invalid()
        return number
    }

    private fun shortMetric(node: JsonNode, field: String): Short {
        val value = metric(node, field).roundToInt()
        if (value > Short.MAX_VALUE) invalid()
        return value.toShort()
    }

    private fun invalid(): Nothing = throw GarminRequestException(400)

    private val runningTypes = setOf(
        "RUNNING",
        "INDOOR_RUNNING",
        "OBSTACLE_RUN",
        "STREET_RUNNING",
        "TRACK_RUNNING",
        "TRAIL_RUNNING",
        "TREADMILL_RUNNING",
        "ULTRA_RUN",
        "VIRTUAL_RUN",
    )
}
