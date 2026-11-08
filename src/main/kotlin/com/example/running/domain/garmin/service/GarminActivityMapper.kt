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
                items = intervalItems(samples, points, start, end, distance, duration).ifEmpty { if (points.isEmpty()) emptyList() else listOf(
                    ImportRunningRecordItemRequest(
                        distance,
                        duration,
                        0,
                        0,
                        0,
                        0,
                        0,
                        start,
                        end,
                        points,
                    )
                ) },
            )
        )
    }

    private fun intervalItems(samples: List<JsonNode>, points: List<RunningRecordItemGpsPointDto>,
        start: Long, end: Long, distance: Int, duration: Long): List<ImportRunningRecordItemRequest> {
        if (samples.isEmpty() || samples.any { !it.path("totalDistanceInMeters").isNumber || !it.path("timerDurationInSeconds").isNumber }) return emptyList()
        val result = mutableListOf<ImportRunningRecordItemRequest>()
        var previousDistance = 0; var previousDuration = 0L; var previousTime = start
        var itemDistance = 0; var itemDuration = 0L; var itemStart = start
        var heartSum = 0.0; var heartWeight = 0L; var cadenceSum = 0.0; var cadenceWeight = 0L
        var routeIndex = 0
        fun append(meters: Int, active: Long, time: Long, heart: Short, cadence: Short) {
            val route = mutableListOf<RunningRecordItemGpsPointDto>()
            while (routeIndex < points.size && (points[routeIndex].timestampMs < time * 1000 || time == end)) {
                route.add(points[routeIndex++])
            }
            result.add(ImportRunningRecordItemRequest(meters, active, cadence, heart, orderIndex = result.size.toShort(),
                startTimestamp = itemStart, endTimestamp = time, gpsPoints = route))
            itemStart = time
        }
        samples.forEach { sample ->
            val time = seconds(sample, "startTimeInSeconds")
            val active = metric(sample, "timerDurationInSeconds").toLong()
            val meters = metric(sample, "totalDistanceInMeters").roundToInt()
            if (time < previousTime || time !in start..end || active < previousDuration || active > duration ||
                meters < previousDistance || meters > distance || active - previousDuration > time - previousTime) invalid()
            val delta = active - previousDuration
            val heart = shortMetric(sample, "heartRate").toInt()
            val cadence = shortMetric(sample, "stepsPerMinute").toInt()
            if (heart > 0) { heartSum += heart * delta.toDouble(); heartWeight += delta }
            if (cadence > 0) { cadenceSum += cadence * delta.toDouble(); cadenceWeight += delta }
            itemDistance += meters - previousDistance; itemDuration += delta
            previousDistance = meters; previousDuration = active; previousTime = time
            if (itemDuration >= 10 && itemDistance > 0) {
                append(itemDistance, itemDuration, time,
                    (if (heartWeight > 0) (heartSum / heartWeight).roundToInt() else 0).toShort(),
                    (if (cadenceWeight > 0) (cadenceSum / cadenceWeight).roundToInt() else 0).toShort())
                itemDistance = 0; itemDuration = 0
                heartSum = 0.0; heartWeight = 0; cadenceSum = 0.0; cadenceWeight = 0
            }
        }
        itemDistance += distance - previousDistance; itemDuration += duration - previousDuration
        if (itemDuration > 0) {
            append(itemDistance, itemDuration, end,
                (if (heartWeight > 0 && duration == previousDuration) (heartSum / heartWeight).roundToInt() else 0).toShort(),
                (if (cadenceWeight > 0 && duration == previousDuration) (cadenceSum / cadenceWeight).roundToInt() else 0).toShort())
        } else if (itemDistance > 0) {
            // A distance-only tail has no sensor interval that can be assigned honestly.
            return emptyList()
        }
        return result
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
