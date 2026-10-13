package com.example.running.domain.running.service

import com.example.running.domain.league.service.LeagueService
import com.example.running.domain.point.service.UserPointService
import com.example.running.domain.running.controller.dto.ImportRunningBatchRequest
import com.example.running.domain.running.controller.dto.ImportRunningRecordItemRequest
import com.example.running.domain.running.controller.dto.ImportRunningRecordRequest
import com.example.running.domain.running.controller.dto.RunningRecordItemGpsPointDto
import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.entity.RunningRecordItem
import com.example.running.domain.running.enums.RunningRecordSource
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.user.entity.UserConfiguration
import com.example.running.domain.user.entity.User
import com.example.running.domain.user.service.UserConfigurationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime

class RunningImportServiceGpsTest {
    @Test
    fun `an imported workout retains all 5001 GPS points in their original order`() {
        val records = mock(RunningRecordRepository::class.java)
        val items = mock(RunningRecordItemRepository::class.java)
        val configuration = mock(UserConfigurationService::class.java)
        val league = mock(LeagueService::class.java)
        val service = RunningImportService(records, items, mock(UserPointService::class.java), configuration, league)
        val existing = RunningRecord(id = 10, user = User(id = 1), source = RunningRecordSource.HEALTH)
        `when`(configuration.getOrCreateForUpdate(1)).thenReturn(UserConfiguration(1, true))
        `when`(records.findByUserIdAndSourceAndExternalId(1, RunningRecordSource.HEALTH, "workout")).thenReturn(existing)
        val start = OffsetDateTime.parse("2026-10-01T00:00:00Z").toInstant().toEpochMilli()
        val end = start + 600_000
        val points = (0..5000).map { index ->
            RunningRecordItemGpsPointDto(37.0 + index * 0.000001, 127.0, start + index, 3.0, 0.0, null)
        }
        var captured: List<RunningRecordItem> = emptyList()
        doAnswer { call -> captured = call.getArgument(0); null }.`when`(items).saveInBatch(anyList())

        val response = service.importBatch(1, ImportRunningBatchRequest(listOf(
            ImportRunningRecordRequest("workout", 2000, 600, startTimestamp = start, endTimestamp = end,
                items = listOf(ImportRunningRecordItemRequest(2000, 600, orderIndex = 0,
                    startTimestamp = start, endTimestamp = end, gpsPoints = points)))
        )))

        val decoded = RunningRecordItemGpsCodec.decode(captured.single().gpsPointsJson)
        assertEquals(1, response.updatedCount)
        assertEquals(0, response.awardedPoint)
        assertEquals(5001, decoded.size)
        assertEquals(points.map { it.timestampMs }, decoded.map { it.timestampMs })
        assertEquals(points.map { it.latitude }, decoded.map { it.latitude })
    }
}
