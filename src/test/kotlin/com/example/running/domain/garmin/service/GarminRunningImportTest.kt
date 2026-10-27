package com.example.running.domain.garmin.service

import com.example.running.domain.running.service.*
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import com.example.running.domain.running.enums.RunningPointExclusionReason
import com.example.running.domain.running.enums.RunningRecordingMethod
import com.example.running.domain.shoe.service.ShoeService
import com.example.running.domain.running.controller.dto.*
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.running.repository.*
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.notification.service.NotificationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import kotlin.test.*

class GarminRunningImportTest {
    private val records = mock(RunningRecordRepository::class.java)
    private val items = mock(RunningRecordItemRepository::class.java)
    private val points = mock(UserPointService::class.java)
    private val configuration = mock(UserConfigurationService::class.java)
    private val league = mock(LeagueService::class.java)
    private val notifications = mock(NotificationService::class.java)
    private val validation = RunningValidationService()
    private val recordService = RunningRecordService(records, mock(ShoeService::class.java), configuration, validation)
    private val acceptance = RunningAcceptanceService(records, items, configuration, points, league, notifications, recordService, validation)
    private val service = RunningImportService(records, items, configuration, acceptance, validation)
    private val connected = OffsetDateTime.parse("2026-10-24T00:00:00Z")
    private val userConfig = UserConfiguration(7, false)
    private val request = ImportRunningRecordRequest("123", 2000, 600,
        startTimestamp = connected.plusHours(1).toEpochSecond(),
        endTimestamp = connected.plusHours(1).plusSeconds(600).toEpochSecond(),
        recordingMethod = "UNKNOWN", sourceBundleId = "test.ignored.app", sourceAppName = "Ignored app")

    @BeforeEach
    fun setup() { `when`(configuration.getOrCreateForUpdate(7)).thenReturn(userConfig) }

    @Test
    fun `Garmin uses the shared reward path when Health import is disabled without changing the Health cursor`() {
        val capture = ArgumentCaptor.forClass(RunningRecord::class.java)
        `when`(records.saveAndFlush(capture.capture())).thenAnswer { it.getArgument(0) }
        `when`(records.markPointAwardedIfEligible(7, 0)).thenReturn(true)
        val result = service.importGarmin(7, ImportRunningBatchRequest(listOf(request)), connected)
        assertEquals(1, result.createdCount)
        assertEquals(20, result.awardedPoint)
        assertEquals(RunningRecordSource.GARMIN, capture.value.source)
        assertEquals(RunningRecordingMethod.RECORDED, capture.value.recordingMethod)
        assertNull(capture.value.sourceAppId)
        assertNull(capture.value.sourceAppName)
        assertNull(capture.value.pointExclusionReason)
        assertTrue(capture.value.pointEligible)
        assertEquals(connected.plusHours(1), capture.value.startDatetime)
        assertFalse(userConfig.healthImportEnabled)
        assertNull(userConfig.healthImportLastSyncedAt)
        assertEquals(1, mockingDetails(points).invocations.count { it.method.name == "updatePoint" })
        verify(league).raiseCurrentLeagueDistance(7)
    }

    @Test
    fun `manual Garmin activity remains in personal statistics without points or league eligibility`() {
        val capture = ArgumentCaptor.forClass(RunningRecord::class.java)
        `when`(records.saveAndFlush(capture.capture())).thenAnswer { it.getArgument(0) }
        val result = service.importGarmin(7, ImportRunningBatchRequest(listOf(request)), connected, manual = true)
        assertEquals(1, result.createdCount)
        assertEquals(0, result.awardedPoint)
        assertTrue(capture.value.isStatisticIncluded)
        assertFalse(capture.value.pointEligible)
        assertEquals(0, capture.value.leagueDistanceOverride)
        assertEquals(RunningRecordingMethod.MANUAL, capture.value.recordingMethod)
        assertEquals(RunningPointExclusionReason.MANUAL_ENTRY, capture.value.pointExclusionReason)
    }

    @Test
    fun `manual edit preserves previously counted distance date and points across later device updates`() {
        val originalEnd = connected.plusHours(1)
        val existing = RunningRecord(id = 10, user = User(7), source = RunningRecordSource.GARMIN,
            distance = 1500, durationSec = 600, pointAwarded = true, startDatetime = originalEnd.minusMinutes(10), endDatetime = originalEnd).apply {
                finishAcceptance(RunningAcceptanceStatus.ACCEPTED, true)
            }
        `when`(records.findByUserIdAndSourceAndExternalId(7, RunningRecordSource.GARMIN, "123")).thenReturn(existing)
        service.importGarmin(7, ImportRunningBatchRequest(listOf(request)), connected, manual = true)
        assertEquals(2000, existing.distance)
        assertEquals(1500, existing.leagueDistanceOverride)
        assertEquals(originalEnd, existing.leagueEndDatetimeOverride)
        assertTrue(existing.pointAwarded)
        assertTrue(existing.pointEligible)
        service.importGarmin(7, ImportRunningBatchRequest(listOf(request)), connected)
        assertEquals(1500, existing.leagueDistanceOverride)
        assertEquals(originalEnd, existing.leagueEndDatetimeOverride)
        verifyNoInteractions(points)
    }

    @Test
    fun `overlap stores a hidden Garmin identity without another reward`() {
        val duplicate = RunningRecord(id = 10, user = User(7), distance = request.distance,
            source = RunningRecordSource.HEALTH,
            startDatetime = connected.plusHours(1), endDatetime = connected.plusHours(1).plusSeconds(600)).apply {
                finishAcceptance(RunningAcceptanceStatus.ACCEPTED, true)
            }
        `when`(records.findOverlapCandidates(7, duplicate.startDatetime, duplicate.endDatetime!!)).thenReturn(listOf(duplicate))
        `when`(records.saveAndFlush(org.mockito.ArgumentMatchers.any(RunningRecord::class.java))).thenAnswer { it.getArgument(0) }
        val result = service.importGarmin(7, ImportRunningBatchRequest(listOf(request)), connected)
        assertEquals(1, result.duplicateCount)
        assertEquals(0, result.awardedPoint)
        verifyNoInteractions(points, league)
        verify(records).saveAndFlush(org.mockito.ArgumentMatchers.any(RunningRecord::class.java))
    }

    @Test
    fun `redelivery updates the same activity but cannot repeat its reward or replace a route with an empty summary`() {
        val existing = RunningRecord(id = 10, user = User(7), source = RunningRecordSource.GARMIN, pointAwarded = true)
        `when`(records.findByUserIdAndSourceAndExternalId(7, RunningRecordSource.GARMIN, "123")).thenReturn(existing)
        val result = service.importGarmin(7, ImportRunningBatchRequest(listOf(request)), connected)
        assertEquals(1, result.updatedCount)
        assertEquals(0, result.awardedPoint)
        verifyNoInteractions(points, items)
    }

    @Test
    fun `records ending before the connection are not eligible for points`() {
        val capture = ArgumentCaptor.forClass(RunningRecord::class.java)
        `when`(records.saveAndFlush(capture.capture())).thenAnswer { it.getArgument(0) }
        val result = service.importGarmin(7, ImportRunningBatchRequest(listOf(request)), connected.plusDays(1))
        assertEquals(0, result.awardedPoint)
        assertFalse(capture.value.pointEligible)
    }
}
