package com.example.running.domain.running.controller

import com.example.running.domain.running.controller.dto.RunningRecordDetailResponse
import com.example.running.domain.running.controller.dto.RunningRecordSearchResponse
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import com.example.running.domain.running.enums.RunningPointExclusionReason
import com.example.running.domain.running.enums.RunningRecordingMethod
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.running.service.dto.RunningRecordDetailDto
import com.example.running.domain.running.service.dto.RunningRecordDto
import com.example.running.domain.user.entity.User
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.test.*

class RunningRecordProvenanceResponseTest {
    private val mapper = jacksonObjectMapper()
    private val start = OffsetDateTime.parse("2026-10-01T00:00:00Z")

    @Test
    fun `list and detail serialize the saved first import provenance and exclusion reason`() {
        val record = RunningRecord(id = 7, user = User(7), distance = 2000, durationSec = 600,
            source = RunningRecordSource.HEALTH, startDatetime = start, endDatetime = start.plusMinutes(10),
            sourceAppId = "test.manual.app", sourceAppName = "Manual app",
            recordingMethod = RunningRecordingMethod.MANUAL).apply {
            finishAcceptance(RunningAcceptanceStatus.PERSONAL_ONLY, false, RunningPointExclusionReason.MANUAL_ENTRY)
        }

        val list = mapper.valueToTree<JsonNode>(RunningRecordSearchResponse(RunningRecordDto(record)))
        val detail = mapper.valueToTree<JsonNode>(RunningRecordDetailResponse(RunningRecordDetailDto(record)))

        assertManualProvenance(list)
        assertManualProvenance(detail)
        assertEquals(2000, list["distance"].intValue())
        assertEquals(start.toEpochSecond(), detail["startTimestamp"].longValue())
    }

    @Test
    fun `legacy paid history exposes saved flags without inferring missing provenance`() {
        val record = RunningRecord(id = 7, user = User(7), distance = 2000, durationSec = 600,
            source = RunningRecordSource.HEALTH, pointAwarded = true,
            startDatetime = start, endDatetime = start.plusMinutes(10)).apply {
            finishAcceptance(RunningAcceptanceStatus.ACCEPTED, true)
        }

        val list = mapper.valueToTree<JsonNode>(RunningRecordSearchResponse(RunningRecordDto(record)))
        val detail = mapper.valueToTree<JsonNode>(RunningRecordDetailResponse(RunningRecordDetailDto(record)))

        assertLegacyProvenance(list)
        assertLegacyProvenance(detail)
    }

    private fun assertManualProvenance(json: JsonNode) {
        assertEquals("HEALTH", json["source"].textValue())
        assertEquals("test.manual.app", json["sourceAppId"].textValue())
        assertEquals("Manual app", json["sourceAppName"].textValue())
        assertEquals("MANUAL", json["recordingMethod"].textValue())
        assertEquals("MANUAL_ENTRY", json["pointExclusionReason"].textValue())
        assertEquals("PERSONAL_ONLY", json["acceptanceStatus"].textValue())
        assertFalse(json["pointEligible"].booleanValue())
        assertFalse(json["pointAwarded"].booleanValue())
    }

    private fun assertLegacyProvenance(json: JsonNode) {
        assertEquals("HEALTH", json["source"].textValue())
        assertTrue(json["sourceAppId"].isNull)
        assertTrue(json["sourceAppName"].isNull)
        assertTrue(json["recordingMethod"].isNull)
        assertTrue(json["pointExclusionReason"].isNull)
        assertEquals("ACCEPTED", json["acceptanceStatus"].textValue())
        assertTrue(json["pointEligible"].booleanValue())
        assertTrue(json["pointAwarded"].booleanValue())
    }
}
