package com.example.running.domain.running.service

import com.example.running.domain.running.controller.dto.CompletedRunningRequest
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.service.dto.RunningRecordItemGpsPointDto
import com.example.running.domain.user.service.UserConfigurationService
import com.example.running.domain.shoe.service.ShoeService
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.point.entity.UserPoint
import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.notification.service.NotificationService
import com.example.running.exception.ApiException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyList

class CompletedRunningServiceTest {
    private val records = mock(RunningRecordRepository::class.java)
    private val itemRepository = mock(RunningRecordItemRepository::class.java)
    private val configurations = mock(UserConfigurationService::class.java)
    private val points = mock(UserPointService::class.java)
    private val league = mock(LeagueService::class.java)
    private val notifications = mock(NotificationService::class.java)
    private val validation = RunningValidationService()
    private val recordService = RunningRecordService(records, mock(ShoeService::class.java), configurations, validation)
    private val acceptance = RunningAcceptanceService(records, itemRepository, configurations, points, league, notifications, recordService, validation)
    private val service = CompletedRunningService(records, configurations,
        RunningRecordItemService(itemRepository, records, configurations, validation),
        RunningEndService(recordService, points, records, configurations, acceptance), validation)
    private val request = CompletedRunningRequest(1, "b1fbb3c0-80d3-4d0d-b24d-2338e64aabf3", 1,
        "APPLE_WATCH", 1790812800, 1790813400, 1500, 550, 170, 140, 100,
        listOf(RunningRecordItemGpsPointDto(37.0, 127.0, 1790813400999, 2.0, 10.0, null)))

    @Test
    fun `completed route retains workout time and retry cannot replace edited data or award again`() {
        val savedItems = mutableListOf<com.example.running.domain.running.entity.RunningRecordItem>()
        doAnswer { call -> savedItems.addAll(call.getArgument<List<com.example.running.domain.running.entity.RunningRecordItem>>(0)); null }
            .`when`(itemRepository).saveInBatch(anyList())
        val record = RunningRecord(id = 10, user = com.example.running.domain.user.entity.User(1))
        `when`(records.saveAndFlush(org.mockito.ArgumentMatchers.any(RunningRecord::class.java))).thenAnswer { call ->
            val incoming = call.getArgument<RunningRecord>(0)
            record.source = incoming.source; record.externalId = incoming.externalId
            record.startDatetime = incoming.startDatetime; record.endDatetime = incoming.endDatetime
            record
        }
        `when`(records.findOwnedForUpdate(10, 1)).thenReturn(record)
        `when`(itemRepository.findAllByRunningRecord_IdOrderByOrderIndexAsc(10)).thenReturn(emptyList())
        `when`(records.markPointAwardedIfEligible(1, 10)).thenReturn(true)
        `when`(points.getOrCreateByUserId(1)).thenReturn(UserPoint(userId = 1))
        assertEquals("CREATED", service.complete(1, request).status)
        assertEquals(1790813400, record.endDatetime!!.toEpochSecond())
        assertEquals(1500, record.distance)
        assertTrue(record.isEnd)
        assertNull(record.shoe)
        val segment = savedItems.single()
        assertEquals(1790813400999, RunningRecordItemGpsCodec.decode(segment.gpsPointsJson).single().timestampMs)
        record.distance = 2000
        `when`(records.findByUserIdAndSourceAndExternalId(1, RunningRecordSource.APPLE_WATCH, request.sessionId)).thenReturn(record)
        assertEquals("DUPLICATE", service.complete(1, request.copy(distance = 5000)).status)
        assertEquals(2000, record.distance)
        val awards = org.mockito.Mockito.mockingDetails(points).invocations.filter { it.method.name == "updatePoint" }
        assertEquals(1, awards.size)
        val award = awards.single().arguments[0] as com.example.running.domain.point.service.dto.PointUsageDto
        assertEquals(15, award.point)
        assertEquals(1L, award.userId)
        assertEquals(10L, award.runningRecordId)
        verify(league, times(1)).raiseCurrentLeagueDistance(1)
        verify(records, never()).updateIsEndById(anyBoolean(), anyLong())
    }

    @Test
    fun `unsigned tinyint bounds and zero elapsed workouts remain uploadable`() {
        val record = RunningRecord(id = 10, user = com.example.running.domain.user.entity.User(1))
        `when`(records.findByUserIdAndSourceAndExternalId(1, RunningRecordSource.APPLE_WATCH, request.sessionId)).thenReturn(record)
        assertEquals("DUPLICATE", service.complete(1, request.copy(cadence = 255, heartRate = 255)).status)
        assertEquals("DUPLICATE", service.complete(1, request.copy(distance = 0, endTimestamp = request.startTimestamp, durationSec = 0, gpsPoints = emptyList())).status)
        assertThrows(com.example.running.exception.RunningValidationException::class.java) { service.complete(1, request.copy(cadence = 256)) }
        assertThrows(com.example.running.exception.RunningValidationException::class.java) { service.complete(1, request.copy(heartRate = 256)) }
    }

    @Test
    fun `different owner cannot read duplicate or persist anything`() {
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
            assertThrows(ApiException::class.java) { service.complete(2, request) }.status)
        verifyNoInteractions(records, configurations, itemRepository, points)
    }

    @Test
    fun `malformed session nonfinite coordinates and active duration beyond elapsed reject without writes`() {
        assertThrows(com.example.running.exception.RunningValidationException::class.java) { service.complete(1, request.copy(sessionId = "1-1-1-1-1")) }
        assertThrows(com.example.running.exception.RunningValidationException::class.java) { service.complete(1, request.copy(durationSec = 601)) }
        assertThrows(com.example.running.exception.RunningValidationException::class.java) { service.complete(1, request.copy(source = "HEALTH")) }
        assertThrows(com.example.running.exception.RunningValidationException::class.java) { service.complete(1, request.copy(gpsPoints = listOf(request.gpsPoints[0].copy(latitude = Double.NaN)))) }
        assertThrows(com.example.running.exception.RunningValidationException::class.java) { service.complete(1, request.copy(gpsPoints = listOf(request.gpsPoints[0].copy(timestampMs = 1790813401000)))) }
        verifyNoInteractions(records, configurations, itemRepository, points)
    }
}
