package com.example.running.domain.running.service

import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.notification.service.NotificationService
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningAcceptanceStatus
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.service.UserConfigurationService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime

class RunningAcceptanceServiceTest {
    private val records = mock(RunningRecordRepository::class.java)
    private val items = mock(RunningRecordItemRepository::class.java)
    private val configurations = mock(UserConfigurationService::class.java)
    private val points = mock(UserPointService::class.java)
    private val league = mock(LeagueService::class.java)
    private val notifications = mock(NotificationService::class.java)
    private val recordService = mock(RunningRecordService::class.java)
    private val service = RunningAcceptanceService(records, items, configurations, points, league,
        notifications, recordService, RunningValidationService())
    private val start = OffsetDateTime.parse("2026-10-01T00:00:00Z")
    private fun record(id: Long, distance: Int = 2000, from: OffsetDateTime = start) =
        RunningRecord(id = id, user = User(7), distance = distance, durationSec = 600,
            startDatetime = from, endDatetime = from.plusMinutes(10))
    private fun accept(record: RunningRecord, recorded: Boolean = true) =
        service.accept(7, record, recorded, true, start.plusHours(1).toInstant())

    @Test
    fun `any positive accepted overlap wins even when distances are completely different`() {
        val paid = record(1).apply { finishAcceptance(RunningAcceptanceStatus.ACCEPTED, true) }
        val personal = record(2).apply { finishAcceptance(RunningAcceptanceStatus.PERSONAL_ONLY, false) }
        val incoming = record(3, distance = 6000, from = start.plusMinutes(9))
        `when`(records.findOverlapCandidates(7, incoming.startDatetime, incoming.endDatetime!!)).thenReturn(listOf(personal, paid))
        assertEquals(0, accept(incoming).awardedPoint)
        assertEquals(RunningAcceptanceStatus.DUPLICATE, incoming.acceptanceStatus)
        assertEquals(RunningAcceptanceStatus.PERSONAL_ONLY, personal.acceptanceStatus)
        assertFalse(incoming.isStatisticIncluded)
        verifyNoInteractions(points, league)
    }

    @Test
    fun `an adjacent interval is accepted and duplicate retries cannot top up points`() {
        val previous = record(1).apply { finishAcceptance(RunningAcceptanceStatus.ACCEPTED, true) }
        val incoming = record(3, from = start.plusMinutes(10))
        `when`(records.findOverlapCandidates(7, incoming.startDatetime, incoming.endDatetime!!)).thenReturn(listOf(previous))
        `when`(records.markPointAwardedIfEligible(7, 3)).thenReturn(true)
        assertEquals(20, accept(incoming).awardedPoint)
        val originalEnd = incoming.originalEndDatetime
        incoming.distance = 5000
        incoming.endDatetime = start.plusMinutes(30)
        assertEquals(0, accept(incoming).awardedPoint)
        assertEquals(2000, incoming.leagueDistanceOverride)
        assertEquals(originalEnd, incoming.originalEndDatetime)
        assertEquals(originalEnd, incoming.leagueEndDatetimeOverride)
        assertEquals(1, mockingDetails(points).invocations.count { it.method.name == "updatePoint" })
    }

    @Test
    fun `recorded activity replaces overlapping personal records and recomputes their shoes`() {
        val personal = record(2).apply { finishAcceptance(RunningAcceptanceStatus.PERSONAL_ONLY, false) }
        val incoming = record(3)
        `when`(records.findOverlapCandidates(7, incoming.startDatetime, incoming.endDatetime!!)).thenReturn(listOf(personal))
        accept(incoming)
        assertEquals(RunningAcceptanceStatus.ACCEPTED, incoming.acceptanceStatus)
        assertEquals(RunningAcceptanceStatus.DUPLICATE, personal.acceptanceStatus)
        assertFalse(personal.isStatisticIncluded)
        verify(recordService).refreshShoeTotals(listOf(personal))
    }

    @Test
    fun `personal records retain stats and cannot be promoted by a later generic completion`() {
        val personal = record(2)
        accept(personal, recorded = false)
        accept(personal, recorded = true)
        assertEquals(RunningAcceptanceStatus.PERSONAL_ONLY, personal.acceptanceStatus)
        assertTrue(personal.isStatisticIncluded)
        assertEquals(0, personal.leagueDistanceOverride)
        verifyNoInteractions(points, league)
    }
}
