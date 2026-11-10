package com.example.running.domain.garmin.service

import com.example.running.domain.garmin.service.dto.GarminActivity
import com.example.running.domain.garmin.service.dto.GarminActivityDetails
import com.example.running.domain.running.service.RunningValidationService
import com.example.running.exception.GarminRequestException
import com.example.running.exception.RunningValidationException
import com.example.running.exception.RunningValidationReason
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

class GarminActivityMapperTest {
    @Test
    fun `stationary samples after the final measured interval retain all GPS points`() {
        val activity = json.readTree("""{
          "userId":"test-user", "activityId":789,
          "summary":{"activityType":"RUNNING", "startTimeInSeconds":1792800000,
            "durationInSeconds":20, "distanceInMeters":60},
          "samples":[
            {"startTimeInSeconds":1792800000,"timerDurationInSeconds":0,"totalDistanceInMeters":0,
             "latitudeInDegree":37.1,"longitudeInDegree":127.1},
            {"startTimeInSeconds":1792800010,"timerDurationInSeconds":10,"totalDistanceInMeters":30,
             "heartRate":130,"stepsPerMinute":160,"latitudeInDegree":37.10027,"longitudeInDegree":127.1},
            {"startTimeInSeconds":1792800020,"timerDurationInSeconds":20,"totalDistanceInMeters":60,
             "heartRate":140,"stepsPerMinute":170,"latitudeInDegree":37.10054,"longitudeInDegree":127.1},
            {"startTimeInSeconds":1792800021,"timerDurationInSeconds":20,"totalDistanceInMeters":60,
             "latitudeInDegree":37.10054,"longitudeInDegree":127.1}
          ]
        }""")

        val record = mapActivity(activity)!!.record

        assertEquals(listOf(1792800000000L, 1792800010000L, 1792800020000L, 1792800021000L),
            record.items.flatMap { it.gpsPoints }.map { it.timestampMs })
        assertEquals(listOf(30, 30), record.items.map { it.distance })
        assertEquals(listOf(10L, 10L), record.items.map { it.durationSec })
        assertEquals(listOf<Short>(130, 140), record.items.map { it.heartRate })
        assertEquals(listOf<Short>(160, 170), record.items.map { it.cadence })
        assertEquals(1792800021L, record.items.last().endTimestamp)
        assertEquals(1792800021L, record.endTimestamp)
    }

    @Test
    fun `distance without remaining active time uses the summary route without invented interval sensors`() {
        val activity = json.readTree("""{
          "userId":"test-user", "activityId":790,
          "summary":{"activityType":"RUNNING", "startTimeInSeconds":1792800000,
            "durationInSeconds":20, "distanceInMeters":61,"averageHeartRateInBeatsPerMinute":145},
          "samples":[
            {"startTimeInSeconds":1792800000,"timerDurationInSeconds":0,"totalDistanceInMeters":0,
             "latitudeInDegree":37.1,"longitudeInDegree":127.1},
            {"startTimeInSeconds":1792800010,"timerDurationInSeconds":10,"totalDistanceInMeters":30,
             "heartRate":130,"stepsPerMinute":160,"latitudeInDegree":37.10027,"longitudeInDegree":127.1},
            {"startTimeInSeconds":1792800020,"timerDurationInSeconds":20,"totalDistanceInMeters":60,
             "heartRate":140,"stepsPerMinute":170,"latitudeInDegree":37.10054,"longitudeInDegree":127.1}
          ]
        }""")

        val record = mapActivity(activity)!!.record
        val item = record.items.single()

        assertEquals(61, item.distance)
        assertEquals(20L, item.durationSec)
        assertEquals(0.toShort(), item.heartRate)
        assertEquals(0.toShort(), item.cadence)
        assertEquals(145.toShort(), record.heartRate)
        assertEquals(listOf(1792800000000L, 1792800010000L, 1792800020000L), item.gpsPoints.map { it.timestampMs })
    }

    @Test
    fun `remaining active second after the last sample keeps every interval valid`() {
        val activity = json.readTree("""{
          "userId":"test-user", "activityId":456,
          "summary":{"activityType":"RUNNING", "startTimeInSeconds":1792800000,
            "durationInSeconds":14, "distanceInMeters":38},
          "samples":[
            {"startTimeInSeconds":1792800000,"timerDurationInSeconds":0,"totalDistanceInMeters":0},
            {"startTimeInSeconds":1792800020,"timerDurationInSeconds":10,"totalDistanceInMeters":30,
             "heartRate":130,"stepsPerMinute":160},
            {"startTimeInSeconds":1792800023,"timerDurationInSeconds":13,"totalDistanceInMeters":38,
             "heartRate":150,"stepsPerMinute":180}
          ]
        }""")

        val record = mapActivity(activity)!!.record
        val validation = RunningValidationService()
        record.items.forEach { item ->
            validation.validate(
                Instant.ofEpochSecond(item.startTimestamp).atOffset(ZoneOffset.UTC),
                Instant.ofEpochSecond(item.endTimestamp).atOffset(ZoneOffset.UTC),
                item.distance, item.durationSec, item.cadence, item.heartRate, 0,
                Instant.ofEpochSecond(1792800100),
            )
        }
        assertEquals(1792800024L, record.endTimestamp)
        assertEquals(listOf(30, 8), record.items.map { it.distance })
        assertEquals(listOf(10L, 4L), record.items.map { it.durationSec })
        assertEquals(38, record.distance)
        assertEquals(14L, record.durationSec)
    }

    @Test
    fun `detail retains interval heart rate and cadence instead of summary averages`() {
        val activity = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        activity.set<com.fasterxml.jackson.databind.JsonNode>("samples", json.readTree("""[
          {"startTimeInSeconds":1792800000,"timerDurationInSeconds":0,"totalDistanceInMeters":0},
          {"startTimeInSeconds":1792800300,"timerDurationInSeconds":300,"totalDistanceInMeters":1000,
           "heartRate":130,"stepsPerMinute":160},
          {"startTimeInSeconds":1792800600,"timerDurationInSeconds":600,"totalDistanceInMeters":2000,
           "heartRate":150,"stepsPerMinute":180}
        ]"""))
        val record = mapActivity(activity)!!.record
        assertEquals(listOf<Short>(130, 150), record.items.map { it.heartRate })
        assertEquals(listOf<Short>(160, 180), record.items.map { it.cadence })
        assertEquals(listOf(1000, 1000), record.items.map { it.distance })
        assertEquals(145.toShort(), record.heartRate)
    }

    private fun mapActivity(activity: JsonNode): GarminActivity? {
        val detail = activity.has("summary")
        val envelope = json.createObjectNode()
        envelope.putArray(if (detail) "activityDetails" else "activities").add(activity)
        val webhook = GarminWebhookPayload.parse(json, envelope.toString())
        return if (detail) mapper.map(webhook.activityDetails.single()) else mapper.map(webhook.activities.single())
    }

    private val json = jacksonObjectMapper()
    private val mapper = GarminActivityMapper()
    private fun detail(type: String = "RUNNING", manual: Boolean = false) = json.readTree("""{
      "userId":"test-user", "summaryId":"123-detail", "activityId":123,
      "summary":{"activityType":"$type", "manual":$manual, "startTimeInSeconds":1792800000,
        "startTimeOffsetInSeconds":32400, "durationInSeconds":600, "distanceInMeters":2000.4,
        "averageHeartRateInBeatsPerMinute":145, "averageRunCadenceInStepsPerMinute":84.0},
      "samples":[{"startTimeInSeconds":1792800001, "latitudeInDegree":37.1, "longitudeInDegree":127.1,
        "speedMetersPerSecond":3.1, "elevationInMeters":-2.0}]
    }""")

    @Test
    fun `detail keeps record timestamps in UTC seconds and GPS timestamps in milliseconds without doubling cadence`() {
        val result = checkNotNull(mapActivity(detail()))
        assertEquals("123", result.record.externalId)
        assertEquals(1792800000L, result.record.startTimestamp)
        assertEquals(1792800600L, result.record.endTimestamp)
        assertEquals(84.toShort(), result.record.cadence)
        val point = result.record.items.single().gpsPoints.single()
        assertEquals(1792800001000, point.timestampMs)
        assertEquals(-2.0, point.altitude)
        assertNull(point.accuracy)
    }

    @Test
    fun `all official running subtypes map and cycling is ignored`() {
        listOf("RUNNING", "INDOOR_RUNNING", "OBSTACLE_RUN", "STREET_RUNNING", "TRACK_RUNNING", "TRAIL_RUNNING",
            "TREADMILL_RUNNING", "ULTRA_RUN", "VIRTUAL_RUN").forEach { assertNotNull(mapActivity(detail(it))) }
        assertNull(mapActivity(detail("CYCLING")))
    }

    @Test
    fun `manual flag is retained for the importing policy`() { assertTrue(mapActivity(detail(manual = true))!!.manual) }

    @Test
    fun `malformed manual provenance and oversized samples fail closed`() {
        val malformed = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        (malformed.path("summary") as com.fasterxml.jackson.databind.node.ObjectNode).put("manual", "false")
        assertFailsWith<GarminRequestException> { mapActivity(malformed) }
        val oversized = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        val samples = json.createArrayNode()
        repeat(GarminInputLimits.MAX_SAMPLES + 1) { samples.addObject() }
        oversized.set<com.fasterxml.jackson.databind.JsonNode>("samples", samples)
        assertFailsWith<GarminRequestException> {
            mapper.map(json.treeToValue(oversized, GarminActivityDetails::class.java))
        }
    }

    @Test
    fun `a full day of one second samples including both endpoints is accepted`() {
        val activity = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        (activity.path("summary") as com.fasterxml.jackson.databind.node.ObjectNode).put("durationInSeconds", 86400)
        val samples = json.createArrayNode()
        repeat(GarminInputLimits.MAX_SAMPLES) { offset ->
            samples.addObject().put("startTimeInSeconds", 1792800000L + offset)
        }
        activity.set<com.fasterxml.jackson.databind.JsonNode>("samples", samples)
        assertEquals(86400L, mapActivity(activity)?.record?.durationSec)
    }

    @Test
    fun `flat summaries and detail use the same activity identity`() {
        val detail = detail()
        val flat = (detail.path("summary").deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>())
            .put("userId", "test-user").put("activityId", "123").put("summaryId", "123-summary")
        assertEquals(mapActivity(detail)!!.record.externalId, mapActivity(flat)!!.record.externalId)
        assertTrue(mapActivity(flat)!!.record.items.isEmpty())
    }

    @Test
    fun `one missing coordinate rejects while absent telemetry coordinates stay optional`() {
        val activity = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        val sample = activity.path("samples")[0] as com.fasterxml.jackson.databind.node.ObjectNode
        sample.remove("latitudeInDegree")
        assertEquals(RunningValidationReason.RUNNING_INVALID_GPS,
            assertFailsWith<RunningValidationException> { mapActivity(activity) }.reason)
        sample.remove("longitudeInDegree")
        assertTrue(mapActivity(activity)!!.record.items.isEmpty())
    }

    @Test
    fun `mapper preserves provider GPS order so shared validation can reject backwards timestamps`() {
        val activity = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        val samples = activity.path("samples") as com.fasterxml.jackson.databind.node.ArrayNode
        samples.add(samples[0].deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("startTimeInSeconds", 1792800000L))
        val points = mapActivity(activity)!!.record.items.single().gpsPoints
        assertEquals(listOf(1792800001000L, 1792800000000L), points.map { it.timestampMs })
    }

    @Test
    fun `invalid present coordinates reject permanently and malformed summary metrics fail closed`() {
        val invalid = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        (invalid.path("samples")[0] as com.fasterxml.jackson.databind.node.ObjectNode).put("latitudeInDegree", 999)
        assertEquals(RunningValidationReason.RUNNING_INVALID_GPS, assertFailsWith<RunningValidationException> { mapActivity(invalid) }.reason)
        (invalid.path("summary") as com.fasterxml.jackson.databind.node.ObjectNode).put("distanceInMeters", "invalid-private-data")
        val failure = assertFailsWith<GarminRequestException> { mapActivity(invalid) }
        assertFalse(failure.message.orEmpty().contains("private"))
    }
}
