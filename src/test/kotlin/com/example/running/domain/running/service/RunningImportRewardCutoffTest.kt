package com.example.running.domain.running.service

import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.notification.service.NotificationService
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.running.controller.dto.ImportRunningBatchRequest
import com.example.running.domain.running.controller.dto.ImportRunningRecordRequest
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.service.UserConfigurationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import kotlin.test.*

class RunningImportRewardCutoffTest {
    private val records = mock(RunningRecordRepository::class.java)
    private val items = mock(RunningRecordItemRepository::class.java)
    private val configurations = mock(UserConfigurationService::class.java)
    private val points = mock(UserPointService::class.java)
    private val validation = RunningValidationService()
    private val acceptance = RunningAcceptanceService(records, items, configurations, points,
        mock(LeagueService::class.java), mock(NotificationService::class.java),
        mock(RunningRecordService::class.java), validation)
    private val service = RunningImportService(records, items, configurations, acceptance, validation)
    private val cutoff = OffsetDateTime.parse("2026-10-01T00:00:00Z")
    private val configuration = UserConfiguration(7, healthImportEnabled = true,
        healthImportLastSyncedAt = cutoff, healthImportFirstEnabledAt = cutoff,
        healthImportRewardCutoffAt = cutoff)
    private val stored = mutableMapOf<String, RunningRecord>()

    @BeforeEach
    fun setup() {
        `when`(configurations.getOrCreateForUpdate(7)).thenReturn(configuration)
        `when`(records.saveAndFlush(any(RunningRecord::class.java) ?: RunningRecord(0))).thenAnswer { invocation ->
            invocation.getArgument<RunningRecord>(0).also { stored[checkNotNull(it.externalId)] = it }
        }
        `when`(records.findByUserIdAndSourceAndExternalId(eq(7L), eq(RunningRecordSource.HEALTH) ?: RunningRecordSource.HEALTH, anyString()))
            .thenAnswer { stored[it.getArgument<String>(2)] }
        `when`(records.markPointAwardedIfEligible(7, 0)).thenReturn(true)
    }

    @Test
    fun `disabled Health batch is unprocessed while enabled empty batch is processed`() {
        configuration.healthImportEnabled = false

        val disabled = service.importBatch(7, batch(), cutoff.plusHours(1).toInstant())
        configuration.healthImportEnabled = true
        val empty = service.importBatch(7, batch(), cutoff.plusHours(2).toInstant())

        assertFalse(disabled.processed)
        assertTrue(empty.processed)
        assertEquals(0, disabled.createdCount)
        assertEquals(0, empty.createdCount)
        verifyNoInteractions(points)
    }

    @Test
    fun `late workout earns points after a newer batch advanced the compatibility cursor`() {
        val newer = service.importBatch(7, batch(record("newer", cutoff.plusHours(4))), cutoff.plusHours(6).toInstant())
        val delayed = service.importBatch(7, batch(record("delayed", cutoff.plusHours(2))), cutoff.plusHours(7).toInstant())

        assertEquals(20, newer.awardedPoint)
        assertEquals(20, delayed.awardedPoint)
        assertTrue(stored.getValue("delayed").pointAwarded)
        assertEquals(cutoff.plusHours(4).plusMinutes(10), configuration.healthImportLastSyncedAt)
        assertEquals(cutoff, configuration.healthImportRewardCutoffAt)
        assertEquals(cutoff, configuration.healthImportFirstEnabledAt)
    }

    @Test
    fun `batch order cannot exclude an older workout after the fixed cutoff`() {
        val response = service.importBatch(7, batch(
            record("newer", cutoff.plusHours(4)), record("older", cutoff.plusHours(2)),
        ), cutoff.plusHours(6).toInstant())

        assertEquals(40, response.awardedPoint)
        assertTrue(stored.getValue("newer").pointEligible)
        assertTrue(stored.getValue("older").pointEligible)
        assertEquals(cutoff, configuration.healthImportRewardCutoffAt)
    }

    @Test
    fun `workouts ending before or exactly at cutoff earn nothing while later workouts earn points`() {
        val response = service.importBatch(7, batch(
            record("before", cutoff.minusMinutes(20)),
            record("equal", cutoff.minusMinutes(10)),
            record("after", cutoff),
        ), cutoff.plusHours(1).toInstant())

        assertEquals(20, response.awardedPoint)
        assertFalse(stored.getValue("before").pointEligible)
        assertFalse(stored.getValue("equal").pointEligible)
        assertTrue(stored.getValue("after").pointAwarded)
    }

    @Test
    fun `legacy cursor is copied once and delayed post-cutoff workouts remain rewardable`() {
        configuration.healthImportFirstEnabledAt = null
        configuration.healthImportRewardCutoffAt = null
        val first = service.importBatch(7, batch(record("newer", cutoff.plusHours(4))), cutoff.plusHours(6).toInstant())
        val delayed = service.importBatch(7, batch(record("delayed", cutoff.plusHours(2))), cutoff.plusHours(7).toInstant())

        assertEquals(20, first.awardedPoint)
        assertEquals(20, delayed.awardedPoint)
        assertNull(configuration.healthImportFirstEnabledAt)
        assertEquals(cutoff, configuration.healthImportRewardCutoffAt)
    }

    @Test
    fun `missing legacy baseline uses server receipt and excludes initial historical import`() {
        configuration.healthImportFirstEnabledAt = null
        configuration.healthImportRewardCutoffAt = null
        configuration.healthImportLastSyncedAt = null
        val receipt = cutoff.plusHours(6)

        val historical = service.importBatch(7, batch(record("historical", cutoff.plusHours(2))), receipt.toInstant())
        val later = service.importBatch(7, batch(record("later", cutoff.plusHours(7))), cutoff.plusHours(8).toInstant())

        assertEquals(0, historical.awardedPoint)
        assertFalse(stored.getValue("historical").pointEligible)
        assertEquals(20, later.awardedPoint)
        assertEquals(receipt, configuration.healthImportRewardCutoffAt)
        assertNull(configuration.healthImportFirstEnabledAt)
    }

    @Test
    fun `repeated batch updates display metrics without topping up points or frozen distance`() {
        val original = record("same", cutoff.plusHours(1))
        val first = service.importBatch(7, batch(original), cutoff.plusHours(2).toInstant())
        val edited = ImportRunningRecordRequest("same", 3000, 600,
            startTimestamp = original.startTimestamp, endTimestamp = original.endTimestamp,
            recordingMethod = "RECORDED")
        val replay = service.importBatch(7, batch(edited), cutoff.plusHours(3).toInstant())

        assertEquals(20, first.awardedPoint)
        assertEquals(0, replay.awardedPoint)
        assertEquals(1, replay.updatedCount)
        assertEquals(3000, stored.getValue("same").distance)
        assertEquals(2000, stored.getValue("same").leagueDistanceOverride)
        assertEquals(cutoff.plusHours(1).plusMinutes(10), stored.getValue("same").originalEndDatetime)
        assertTrue(stored.getValue("same").pointAwarded)
        assertEquals(cutoff, configuration.healthImportRewardCutoffAt)
    }

    @Test
    fun `manual and unknown workouts after cutoff never receive points`() {
        val response = service.importBatch(7, batch(
            record("manual", cutoff.plusHours(1), "MANUAL"),
            record("unknown", cutoff.plusHours(2), "UNKNOWN"),
        ), cutoff.plusHours(3).toInstant())

        assertEquals(0, response.awardedPoint)
        assertEquals(2, response.personalOnlyCount)
        assertFalse(stored.getValue("manual").pointEligible)
        assertFalse(stored.getValue("unknown").pointEligible)
        verifyNoInteractions(points)
    }

    private fun batch(vararg entries: ImportRunningRecordRequest) = ImportRunningBatchRequest(entries.toList())

    private fun record(id: String, start: OffsetDateTime, method: String = "RECORDED") =
        ImportRunningRecordRequest(id, 2000, 600, startTimestamp = start.toEpochSecond(),
            endTimestamp = start.plusMinutes(10).toEpochSecond(), recordingMethod = method)
}
