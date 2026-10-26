package com.example.running.domain.running.service

import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.notification.service.NotificationService
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.running.controller.dto.ImportRunningBatchRequest
import com.example.running.domain.running.controller.dto.ImportRunningRecordRequest
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.exception.RunningValidationException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import java.time.OffsetDateTime

class RunningImportAcceptanceTest {
    private val records = mock(RunningRecordRepository::class.java)
    private val items = mock(RunningRecordItemRepository::class.java)
    private val configuration = mock(UserConfigurationService::class.java)
    private val points = mock(UserPointService::class.java)
    private val league = mock(LeagueService::class.java)
    private val validation = RunningValidationService()
    private val acceptance = RunningAcceptanceService(records, items, configuration, points, league,
        mock(NotificationService::class.java), mock(RunningRecordService::class.java), validation)
    private val service = RunningImportService(records, items, configuration, acceptance, validation)
    private val start = OffsetDateTime.parse("2026-10-01T00:00:00Z")
    private fun request(method: String?) = ImportRunningRecordRequest("health-id", 2000, 600,
        startTimestamp = start.toEpochSecond(), endTimestamp = start.plusMinutes(10).toEpochSecond(),
        recordingMethod = method)

    @Test
    fun `missing unknown and manual Health methods retain personal stats without rewards`() {
        listOf(null, "UNKNOWN", "MANUAL").forEach { method ->
            reset(records, points, league)
            `when`(configuration.getOrCreateForUpdate(7)).thenReturn(UserConfiguration(7, true))
            var stored: RunningRecord? = null
            `when`(records.saveAndFlush(any(RunningRecord::class.java))).thenAnswer { invocation ->
                invocation.getArgument<RunningRecord>(0).also { stored = it }
            }
            val result = service.importBatch(7, ImportRunningBatchRequest(listOf(request(method))), start.plusHours(1).toInstant())
            assertEquals(1, result.createdCount)
            assertEquals(1, result.personalOnlyCount)
            assertEquals(RunningAcceptanceStatus.PERSONAL_ONLY, stored!!.acceptanceStatus)
            assertTrue(stored!!.isStatisticIncluded)
            assertEquals(0, stored!!.leagueDistanceOverride)
            verifyNoInteractions(points, league)
        }
    }

    @Test
    fun `duplicate external identity remains hidden when a replay moves its interval`() {
        val duplicate = RunningRecord(id = 10, user = com.example.running.domain.user.entity.User(7),
            source = RunningRecordSource.HEALTH, externalId = "health-id", startDatetime = start,
            endDatetime = start.plusMinutes(10)).apply { finishAcceptance(RunningAcceptanceStatus.DUPLICATE, false) }
        val originalEnd = duplicate.originalEndDatetime
        `when`(configuration.getOrCreateForUpdate(7)).thenReturn(UserConfiguration(7, true))
        `when`(records.findByUserIdAndSourceAndExternalId(7, RunningRecordSource.HEALTH, "health-id")).thenReturn(duplicate)
        val moved = ImportRunningRecordRequest("health-id", 2000, 600,
            startTimestamp = start.plusHours(2).toEpochSecond(), endTimestamp = start.plusHours(2).plusMinutes(10).toEpochSecond(),
            recordingMethod = "RECORDED")
        val result = service.importBatch(7, ImportRunningBatchRequest(listOf(moved)), start.plusHours(3).toInstant())
        assertEquals(1, result.duplicateCount)
        assertEquals(RunningAcceptanceStatus.DUPLICATE, duplicate.acceptanceStatus)
        assertFalse(duplicate.isStatisticIncluded)
        assertEquals(originalEnd, duplicate.originalEndDatetime)
        verifyNoInteractions(points, league)
        verify(records, never()).saveAndFlush(any(RunningRecord::class.java))
    }

    @Test
    fun `metadata rejects unexpected type value and length before persistence`() {
        val mapper = jacksonObjectMapper()
        val json = """{"externalId":"id","distance":1,"durationSec":1,"startTimestamp":1,"endTimestamp":2,"recordingMethod":12}"""
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException::class.java) {
            mapper.readValue(json, ImportRunningRecordRequest::class.java)
        }
        assertThrows(RunningValidationException::class.java) {
            service.importBatch(7, ImportRunningBatchRequest(listOf(request("DEVICE"))), start.plusHours(1).toInstant())
        }
        val oversizedMetadata = ImportRunningRecordRequest("id", 2000, 600,
            startTimestamp = start.toEpochSecond(), endTimestamp = start.plusMinutes(10).toEpochSecond(),
            sourceBundleId = "a".repeat(256))
        assertThrows(RunningValidationException::class.java) {
            service.importBatch(7, ImportRunningBatchRequest(listOf(oversizedMetadata)), start.plusHours(1).toInstant())
        }
        verifyNoInteractions(records, configuration)
    }
}
