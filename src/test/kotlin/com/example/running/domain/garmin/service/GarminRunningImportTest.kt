package com.example.running.domain.garmin.service

import com.example.running.domain.running.service.*
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import com.example.running.domain.running.enums.RunningPointExclusionReason
import com.example.running.domain.running.enums.RunningRecordingMethod
import com.example.running.domain.shoe.service.ShoeService
import com.example.running.domain.running.controller.dto.*
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.entity.RunningRecordItem
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.running.repository.*
import com.example.running.domain.shoe.entity.Shoe
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.notification.service.NotificationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
    private val shoes = mock(ShoeService::class.java)
    private val validation = RunningValidationService()
    private val recordService = RunningRecordService(records, shoes, configuration, validation)
    private val acceptance = RunningAcceptanceService(records, items, configuration, points, league, notifications, recordService, validation)
    private val service = RunningImportService(records, items, configuration, acceptance, validation, recordService)
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

    @ParameterizedTest
    @CsvSource(
        "com.garmin.android.apps.connectmobile, health-id",
        "NULL, health-connect:com.garmin.android.apps.connectmobile:health-id",
        nullValues = ["NULL"],
    )
    fun `Garmin corrects its accepted Health mirror without losing identity route or reward history`(sourceAppId: String?, healthId: String) {
        val start = connected.plusHours(1)
        val end = start.plusMinutes(10)
        val existing = RunningRecord(id = 10, user = User(7), shoe = Shoe(55), source = RunningRecordSource.HEALTH,
            externalId = healthId, sourceAppId = sourceAppId, sourceAppName = "Garmin Connect",
            recordingMethod = RunningRecordingMethod.RECORDED, distance = 1500, durationSec = 600,
            pointAwarded = true, startDatetime = start, endDatetime = end).apply {
                finishAcceptance(RunningAcceptanceStatus.ACCEPTED, true)
                // An edited display timestamp must not replace the original matching identity.
                startDatetime = start.plusSeconds(30)
            }
        `when`(records.findOverlapCandidates(7, start, end)).thenReturn(listOf(existing))
        `when`(records.findAllDistanceByShoeId(55)).thenAnswer { listOf(existing.distance, 1000) }
        `when`(records.saveAndFlush(org.mockito.ArgumentMatchers.any(RunningRecord::class.java))).thenAnswer { it.getArgument(0) }
        var storedItems: List<RunningRecordItem> = emptyList()
        doAnswer { storedItems = it.getArgument(0); null }.`when`(items).saveInBatch(org.mockito.ArgumentMatchers.anyList())
        val gps = listOf(
            RunningRecordItemGpsPointDto(37.0, 127.0, start.toInstant().toEpochMilli(), 3.0, 0.0, null),
            RunningRecordItemGpsPointDto(37.001, 127.001, start.plusSeconds(1).toInstant().toEpochMilli(), 3.0, 0.0, null),
        )
        val detailed = ImportRunningRecordRequest("123", 2000, 600,
            startTimestamp = request.startTimestamp, endTimestamp = request.endTimestamp,
            items = listOf(ImportRunningRecordItemRequest(2000, 600, orderIndex = 0,
                startTimestamp = request.startTimestamp, endTimestamp = request.endTimestamp, gpsPoints = gps)))

        val result = service.importGarmin(7, ImportRunningBatchRequest(listOf(detailed)), connected)

        assertEquals(1, result.updatedCount)
        assertEquals(0, result.createdCount)
        assertEquals(0, result.duplicateCount)
        assertEquals(0, result.awardedPoint)
        assertEquals(10, existing.id)
        assertEquals(RunningRecordSource.GARMIN, existing.source)
        assertEquals("123", existing.externalId)
        assertNull(existing.sourceAppId)
        assertNull(existing.sourceAppName)
        assertEquals(2000, existing.distance)
        assertEquals(start, existing.startDatetime)
        assertEquals(RunningAcceptanceStatus.ACCEPTED, existing.acceptanceStatus)
        assertTrue(existing.isStatisticIncluded)
        assertTrue(existing.pointAwarded)
        assertTrue(existing.pointEligible)
        assertEquals(1500, existing.leagueDistanceOverride)
        assertEquals(end, existing.leagueEndDatetimeOverride)
        assertEquals(start, existing.originalStartDatetime)
        assertEquals(end, existing.originalEndDatetime)
        assertEquals(55, existing.shoe?.id)
        assertEquals(10, storedItems.single().runningRecord.id)
        assertEquals(gps.map { it.timestampMs }, RunningRecordItemGpsCodec.decode(storedItems.single().gpsPointsJson).map { it.timestampMs })
        assertEquals(gps.map { it.latitude }, RunningRecordItemGpsCodec.decode(storedItems.single().gpsPointsJson).map { it.latitude })
        verify(shoes).updateTotalDistance(55, 3000)
        verify(records, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(RunningRecord::class.java))

        `when`(records.findByUserIdAndSourceAndExternalId(7, RunningRecordSource.GARMIN, "123")).thenReturn(existing)
        val summary = service.importGarmin(7, ImportRunningBatchRequest(listOf(request)), connected)
        assertEquals(1, summary.updatedCount)
        verify(items, times(1)).deleteAllByRunningRecord_Id(10)
        verify(items, times(1)).saveInBatch(org.mockito.ArgumentMatchers.anyList())

        userConfig.healthImportEnabled = true
        val replay = ImportRunningRecordRequest(healthId, 3000, 600,
            startTimestamp = request.startTimestamp, endTimestamp = request.endTimestamp,
            sourceBundleId = "com.garmin.android.apps.connectmobile", recordingMethod = "RECORDED")
        val replayResult = service.importBatch(7, ImportRunningBatchRequest(listOf(replay)))
        assertEquals(1, replayResult.duplicateCount)
        assertEquals(0, replayResult.awardedPoint)
        assertEquals(2000, existing.distance)
        assertEquals(RunningRecordSource.GARMIN, existing.source)
        verifyNoInteractions(points, league)
    }

    @ParameterizedTest
    @CsvSource(
        "HEALTH, com.sec.android.app.shealth, other-id, 0",
        "HEALTH, com.sec.android.app.shealth, health-connect:com.garmin.android.apps.connectmobile:conflict, 0",
        "LIVE, NULL, NULL, 0",
        "HEALTH, com.garmin.android.apps.connectmobile, health-id, -1",
        nullValues = ["NULL"],
    )
    fun `Garmin does not replace another source or a different original start`(source: RunningRecordSource, sourceAppId: String?, externalId: String?, offset: Long) {
        val start = connected.plusHours(1)
        val end = start.plusMinutes(10)
        val existing = RunningRecord(id = 10, user = User(7), source = source,
            sourceAppId = sourceAppId, externalId = externalId, distance = 1500, durationSec = 600,
            pointAwarded = true, startDatetime = start.plusSeconds(offset), endDatetime = end).apply {
                finishAcceptance(RunningAcceptanceStatus.ACCEPTED, true)
            }
        `when`(records.findOverlapCandidates(7, start, end)).thenReturn(listOf(existing))
        `when`(records.saveAndFlush(org.mockito.ArgumentMatchers.any(RunningRecord::class.java))).thenAnswer { it.getArgument(0) }

        val result = service.importGarmin(7, ImportRunningBatchRequest(listOf(request)), connected)

        assertEquals(1, result.duplicateCount)
        assertEquals(0, result.updatedCount)
        assertEquals(source, existing.source)
        assertEquals(externalId, existing.externalId)
        assertEquals(1500, existing.distance)
        verifyNoInteractions(points, league)
    }

    @Test
    fun `ambiguous Garmin Health mirrors are not selected for correction`() {
        val start = connected.plusHours(1)
        val end = start.plusMinutes(10)
        val first = RunningRecord(id = 10, user = User(7), source = RunningRecordSource.HEALTH,
            sourceAppId = "com.garmin.android.apps.connectmobile", externalId = "first",
            distance = 1500, durationSec = 600, startDatetime = start, endDatetime = end).apply {
                finishAcceptance(RunningAcceptanceStatus.ACCEPTED, false)
            }
        val second = RunningRecord(id = 11, user = User(7), source = RunningRecordSource.HEALTH,
            sourceAppId = "com.garmin.android.apps.connectmobile", externalId = "second",
            distance = 1800, durationSec = 600, startDatetime = start, endDatetime = end).apply {
                finishAcceptance(RunningAcceptanceStatus.ACCEPTED, false)
            }
        `when`(records.findOverlapCandidates(7, start, end)).thenReturn(listOf(first, second))
        `when`(records.saveAndFlush(org.mockito.ArgumentMatchers.any(RunningRecord::class.java))).thenAnswer { it.getArgument(0) }

        val result = service.importGarmin(7, ImportRunningBatchRequest(listOf(request)), connected)

        assertEquals(1, result.duplicateCount)
        assertEquals(0, result.updatedCount)
        assertEquals(RunningRecordSource.HEALTH, first.source)
        assertEquals(RunningRecordSource.HEALTH, second.source)
        assertEquals(1500, first.distance)
        assertEquals(1800, second.distance)
        verifyNoInteractions(points, league)
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
