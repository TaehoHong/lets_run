package com.example.running.domain.running.service

import com.example.running.domain.running.service.dto.RunningRecordItemGpsPointDto
import com.example.running.exception.RunningValidationException
import com.example.running.exception.RunningValidationReason
import org.springframework.stereotype.Service
import java.time.DateTimeException
import com.example.running.utils.convertToOffsetDateTime
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

@Service
class RunningValidationService {
    fun timestamp(value: Long, timezone: String = "UTC"): OffsetDateTime {
        metrics(value in 0..253402300799)
        return try {
            convertToOffsetDateTime(value, timezone)
        } catch (_: DateTimeException) {
            fail(RunningValidationReason.RUNNING_INVALID_METRICS)
        }
    }

    fun validate(
        start: OffsetDateTime, end: OffsetDateTime, distance: Int, durationSec: Long,
        cadence: Short, heartRate: Short, calorie: Int, receivedAt: Instant,
        gpsPoints: List<RunningRecordItemGpsPointDto> = emptyList(),
    ) {
        val startSecond = start.toEpochSecond()
        val endSecond = end.toEpochSecond()
        metrics(startSecond in 0..253402300799 && endSecond in 0..253402300799 && !end.isBefore(start))
        if (end.toInstant().isAfter(receivedAt)) fail(RunningValidationReason.RUNNING_FUTURE_END_TIME)
        val elapsed = Duration.between(start, end).seconds
        metrics(distance >= 0 && durationSec >= 0 && durationSec <= elapsed && calorie >= 0)
        metrics(cadence in 0..255 && heartRate in 0..255)
        metrics(distance == 0 || durationSec > 0)
        // Time bounds above make this multiplication safe even for the longest stored interval.
        if (distance.toLong() * 3600 > durationSec * 40000) {
            fail(RunningValidationReason.RUNNING_EXCESSIVE_AVERAGE_SPEED)
        }
        validateGps(gpsPoints, start, end)
    }

    fun validateGps(points: List<RunningRecordItemGpsPointDto>, start: OffsetDateTime, end: OffsetDateTime) {
        var previous = Long.MIN_VALUE
        points.forEach { point ->
            val valid = point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
                point.longitude.isFinite() && point.longitude in -180.0..180.0 &&
                point.speed.isFinite() && point.speed >= 0 && point.altitude.isFinite() &&
                (point.accuracy == null || (point.accuracy.isFinite() && point.accuracy >= 0)) &&
                point.timestampMs >= start.toInstant().toEpochMilli() &&
                point.timestampMs <= end.toInstant().toEpochMilli() + 999 && point.timestampMs >= previous
            if (!valid) fail(RunningValidationReason.RUNNING_INVALID_GPS)
            previous = point.timestampMs
        }
    }

    fun metrics(valid: Boolean) {
        if (!valid) fail(RunningValidationReason.RUNNING_INVALID_METRICS)
    }

    private fun fail(reason: RunningValidationReason): Nothing = throw RunningValidationException(reason)
}
