package com.example.running.domain.garmin.service

import com.example.running.domain.garmin.service.dto.GarminActivity
import com.example.running.domain.garmin.service.dto.GarminActivityDetails
import com.example.running.domain.garmin.service.dto.GarminActivitySummary
import com.example.running.domain.garmin.service.dto.GarminSample
import com.example.running.domain.running.controller.dto.*
import com.example.running.exception.GarminRequestException
import com.example.running.exception.RunningValidationException
import com.example.running.exception.RunningValidationReason
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

@Component
class GarminActivityMapper {
    fun map(activity: GarminActivitySummary): GarminActivity? =
        map(activity.userId, activity.activityId, activity, activity.samples)

    fun map(activity: GarminActivityDetails): GarminActivity? =
        map(activity.userId, activity.activityId, activity.summary, activity.samples)

    private fun map(
        userId: String?,
        id: String?,
        summary: GarminActivitySummary,
        samples: List<GarminSample>,
    ): GarminActivity? {
        if (summary.activityType !in runningTypes) return null
        if (userId.isNullOrBlank() || userId.length > 255 || id.isNullOrBlank() || id.length > 191) invalid()
        val start = seconds(summary.startTimeInSeconds)
        val duration = seconds(summary.durationInSeconds)
        if (start <= 0 || duration <= 0 || duration > 86400) invalid()
        if (samples.size > GarminInputLimits.MAX_SAMPLES) invalid()
        val end = endTimestamp(samples, start, duration)
        val distance = metric(summary.distanceInMeters).roundToInt()
        val heartRate = shortMetric(summary.averageHeartRateInBeatsPerMinute)
        val cadence = shortMetric(summary.averageRunCadenceInStepsPerMinute)
        val points = mapGpsPoints(samples, start, end)
        val items = mapItems(samples, points, start, end, distance, duration)
        return GarminActivity(
            garminUserId = userId,
            manual = summary.manual,
            record = ImportRunningRecordRequest(
                externalId = id,
                distance = distance,
                durationSec = duration,
                cadence = cadence,
                heartRate = heartRate,
                calorie = metric(summary.activeKilocalories).roundToInt(),
                startTimestamp = start,
                endTimestamp = end,
                items = items,
            )
        )
    }

    private fun endTimestamp(samples: List<GarminSample>, start: Long, duration: Long): Long {
        val lastSample = samples.lastOrNull()
        val remainingDuration = lastSample?.timerDurationInSeconds
            ?.let { (duration - metric(it).toLong()).coerceAtLeast(0) } ?: 0
        // A sample timestamp marks its instant, not the end of unreported active time.
        val end = maxOf(
            Math.addExact(start, duration),
            samples.maxOfOrNull { seconds(it.startTimeInSeconds) } ?: 0,
            lastSample?.let { Math.addExact(seconds(it.startTimeInSeconds), remainingDuration) } ?: 0,
        )
        if (end - start > 86400) invalid()
        return end
    }

    private fun mapGpsPoints(samples: List<GarminSample>, start: Long, end: Long): List<RunningRecordItemGpsPointDto> = try {
        samples.mapNotNull { sample ->
            val lat = sample.latitudeInDegree
            val lon = sample.longitudeInDegree
            if (lat == null && lon == null) return@mapNotNull null
            if (lat == null || lon == null) invalid()
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) invalid()
            val timestamp = seconds(sample.startTimeInSeconds)
            if (timestamp < start || timestamp > end) invalid()
            RunningRecordItemGpsPointDto(
                latitude = lat,
                longitude = lon,
                timestampMs = Math.multiplyExact(timestamp, 1000),
                speed = metric(sample.speedMetersPerSecond),
                altitude = metric(sample.elevationInMeters, signed = true),
                accuracy = null,
            )
        }
    } catch (_: GarminRequestException) {
        throw RunningValidationException(RunningValidationReason.RUNNING_INVALID_GPS)
    } catch (_: ArithmeticException) {
        throw RunningValidationException(RunningValidationReason.RUNNING_INVALID_GPS)
    }

    private fun mapItems(
        samples: List<GarminSample>,
        points: List<RunningRecordItemGpsPointDto>,
        start: Long,
        end: Long,
        distance: Int,
        duration: Long,
    ): List<ImportRunningRecordItemRequest> {
        if (samples.isEmpty() || samples.any { it.totalDistanceInMeters == null || it.timerDurationInSeconds == null }) {
            return summaryItems(points, start, end, distance, duration)
        }
        val intervals = measuredIntervals(samples, start, end, distance, duration)
            ?: return summaryItems(points, start, end, distance, duration)
        return assignGpsPoints(intervals, points, end)
    }

    private fun summaryItems(
        points: List<RunningRecordItemGpsPointDto>, start: Long, end: Long, distance: Int, duration: Long,
    ): List<ImportRunningRecordItemRequest> = if (points.isEmpty()) emptyList() else listOf(
        ImportRunningRecordItemRequest(
            distance = distance,
            durationSec = duration,
            orderIndex = 0,
            startTimestamp = start,
            endTimestamp = end,
            gpsPoints = points,
        )
    )

    private fun assignGpsPoints(
        items: List<ImportRunningRecordItemRequest>, points: List<RunningRecordItemGpsPointDto>, end: Long,
    ): List<ImportRunningRecordItemRequest> {
        var routeIndex = 0
        return items.mapIndexed { index, item ->
            val isLast = index == items.lastIndex
            val itemEnd = if (isLast) end else item.endTimestamp
            val routeStart = routeIndex
            while (routeIndex < points.size && (isLast || points[routeIndex].timestampMs < itemEnd * 1000)) {
                routeIndex++
            }
            ImportRunningRecordItemRequest(
                distance = item.distance,
                durationSec = item.durationSec,
                cadence = item.cadence,
                heartRate = item.heartRate,
                minHeartRate = item.minHeartRate,
                maxHeartRate = item.maxHeartRate,
                orderIndex = item.orderIndex,
                startTimestamp = item.startTimestamp,
                endTimestamp = itemEnd,
                gpsPoints = points.subList(routeStart, routeIndex),
            )
        }
    }

    private fun measuredIntervals(
        samples: List<GarminSample>, start: Long, end: Long, distance: Int, duration: Long,
    ): List<ImportRunningRecordItemRequest>? {
        val result = mutableListOf<ImportRunningRecordItemRequest>()
        var previousDistance = 0
        var previousDuration = 0L
        var previousTime = start
        var interval = IntervalAccumulator(start)
        samples.forEach { sample ->
            val time = seconds(sample.startTimeInSeconds)
            val active = metric(sample.timerDurationInSeconds).toLong()
            val meters = metric(sample.totalDistanceInMeters).roundToInt()
            if (time < previousTime || time !in start..end || active < previousDuration || active > duration ||
                meters < previousDistance || meters > distance || active - previousDuration > time - previousTime) invalid()
            interval.add(meters - previousDistance, active - previousDuration,
                shortMetric(sample.heartRate), shortMetric(sample.stepsPerMinute))
            previousDistance = meters
            previousDuration = active
            previousTime = time
            if (interval.canClose) {
                result.add(interval.toItem(time, result.size.toShort()))
                interval = IntervalAccumulator(time)
            }
        }
        val remainingDistance = distance - previousDistance
        val remainingDuration = duration - previousDuration
        if (interval.durationSec + remainingDuration > 0) {
            result.add(interval.toItem(end, result.size.toShort(), remainingDistance, remainingDuration))
        } else if (interval.distance + remainingDistance > 0) {
            // A distance-only tail has no sensor interval that can be assigned honestly.
            return null
        }
        return result
    }

    private class IntervalAccumulator(private val start: Long) {
        var distance = 0
            private set
        var durationSec = 0L
            private set
        private var heartSum = 0.0
        private var heartWeight = 0L
        private var cadenceSum = 0.0
        private var cadenceWeight = 0L

        val canClose: Boolean get() = durationSec >= 10 && distance > 0

        fun add(meters: Int, activeSeconds: Long, heartRate: Short, cadence: Short) {
            distance += meters
            durationSec += activeSeconds
            if (heartRate > 0) {
                heartSum += heartRate * activeSeconds.toDouble()
                heartWeight += activeSeconds
            }
            if (cadence > 0) {
                cadenceSum += cadence * activeSeconds.toDouble()
                cadenceWeight += activeSeconds
            }
        }

        fun toItem(end: Long, orderIndex: Short, remainingDistance: Int = 0, remainingDuration: Long = 0) =
            ImportRunningRecordItemRequest(
                distance = distance + remainingDistance,
                durationSec = durationSec + remainingDuration,
                cadence = if (remainingDuration == 0L) weightedAverage(cadenceSum, cadenceWeight) else 0,
                heartRate = if (remainingDuration == 0L) weightedAverage(heartSum, heartWeight) else 0,
                orderIndex = orderIndex,
                startTimestamp = start,
                endTimestamp = end,
            )

        private fun weightedAverage(sum: Double, weight: Long): Short =
            if (weight > 0) (sum / weight).roundToInt().toShort() else 0
    }

    private fun seconds(value: Long?): Long = value?.takeIf { it >= 0 } ?: invalid()

    private fun metric(value: Double?, signed: Boolean = false): Double {
        if (value == null) return 0.0
        if (!value.isFinite() || (!signed && value < 0) || value > Int.MAX_VALUE || value < Int.MIN_VALUE) invalid()
        return value
    }

    private fun shortMetric(value: Double?): Short {
        val rounded = metric(value).roundToInt()
        if (rounded > Short.MAX_VALUE) invalid()
        return rounded.toShort()
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
