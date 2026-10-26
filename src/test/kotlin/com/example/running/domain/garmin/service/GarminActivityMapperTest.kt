package com.example.running.domain.garmin.service

import com.example.running.exception.GarminRequestException
import com.example.running.exception.RunningValidationException
import com.example.running.exception.RunningValidationReason
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.*

class GarminActivityMapperTest {
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
        val result = checkNotNull(mapper.map(detail()))
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
            "TREADMILL_RUNNING", "ULTRA_RUN", "VIRTUAL_RUN").forEach { assertNotNull(mapper.map(detail(it))) }
        assertNull(mapper.map(detail("CYCLING")))
    }

    @Test
    fun `manual flag is retained for the importing policy`() { assertTrue(mapper.map(detail(manual = true))!!.manual) }

    @Test
    fun `malformed manual provenance and oversized samples fail closed`() {
        val malformed = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        (malformed.path("summary") as com.fasterxml.jackson.databind.node.ObjectNode).put("manual", "false")
        assertFailsWith<GarminRequestException> { mapper.map(malformed) }
        val oversized = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        val samples = json.createArrayNode()
        repeat(GarminInputLimits.MAX_SAMPLES + 1) { samples.addObject() }
        oversized.set<com.fasterxml.jackson.databind.JsonNode>("samples", samples)
        assertFailsWith<GarminRequestException> { mapper.map(oversized) }
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
        assertEquals(86400L, mapper.map(activity)?.record?.durationSec)
    }

    @Test
    fun `flat summaries and detail use the same activity identity`() {
        val detail = detail()
        val flat = (detail.path("summary").deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>())
            .put("userId", "test-user").put("activityId", "123").put("summaryId", "123-summary")
        assertEquals(mapper.map(detail)!!.record.externalId, mapper.map(flat)!!.record.externalId)
        assertTrue(mapper.map(flat)!!.record.items.isEmpty())
    }

    @Test
    fun `one missing coordinate rejects while absent telemetry coordinates stay optional`() {
        val activity = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        val sample = activity.path("samples")[0] as com.fasterxml.jackson.databind.node.ObjectNode
        sample.remove("latitudeInDegree")
        assertEquals(RunningValidationReason.RUNNING_INVALID_GPS,
            assertFailsWith<RunningValidationException> { mapper.map(activity) }.reason)
        sample.remove("longitudeInDegree")
        assertTrue(mapper.map(activity)!!.record.items.isEmpty())
    }

    @Test
    fun `mapper preserves provider GPS order so shared validation can reject backwards timestamps`() {
        val activity = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        val samples = activity.path("samples") as com.fasterxml.jackson.databind.node.ArrayNode
        samples.add(samples[0].deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("startTimeInSeconds", 1792800000L))
        val points = mapper.map(activity)!!.record.items.single().gpsPoints
        assertEquals(listOf(1792800001000L, 1792800000000L), points.map { it.timestampMs })
    }

    @Test
    fun `invalid present coordinates reject permanently and malformed summary metrics fail closed`() {
        val invalid = detail() as com.fasterxml.jackson.databind.node.ObjectNode
        (invalid.path("samples")[0] as com.fasterxml.jackson.databind.node.ObjectNode).put("latitudeInDegree", 999)
        assertEquals(RunningValidationReason.RUNNING_INVALID_GPS, assertFailsWith<RunningValidationException> { mapper.map(invalid) }.reason)
        (invalid.path("summary") as com.fasterxml.jackson.databind.node.ObjectNode).put("distanceInMeters", "invalid-private-data")
        val failure = assertFailsWith<GarminRequestException> { mapper.map(invalid) }
        assertFalse(failure.message.orEmpty().contains("private"))
    }
}
