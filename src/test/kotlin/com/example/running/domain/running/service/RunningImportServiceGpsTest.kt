package com.example.running.domain.running.service

import com.example.running.domain.notification.service.NotificationService
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
    fun `health import persists multiple measured intervals without duplicating rewards`() {
        val records = mock(RunningRecordRepository::class.java)
        val items = mock(RunningRecordItemRepository::class.java)
        val configuration = mock(UserConfigurationService::class.java)
        val service = RunningImportService(records, items, configuration, mock(RunningAcceptanceService::class.java),
            RunningValidationService(), mock(RunningRecordService::class.java), mock(NotificationService::class.java))
        `when`(configuration.getOrCreateForUpdate(1)).thenReturn(UserConfiguration(1, true))
        `when`(records.findByUserIdAndSourceAndExternalId(1, RunningRecordSource.HEALTH, "intervals"))
            .thenReturn(RunningRecord(id = 10, user = User(id = 1), source = RunningRecordSource.HEALTH))
        val start = OffsetDateTime.parse("2026-10-01T00:00:00Z").toEpochSecond()
        var saved: List<RunningRecordItem> = emptyList()
        doAnswer { call -> saved = call.getArgument(0); null }.`when`(items).saveInBatch(anyList())
        val result = service.importBatch(1, ImportRunningBatchRequest(listOf(ImportRunningRecordRequest(
            "intervals", 1000, 300, heartRate = 140, cadence = 170, startTimestamp = start, endTimestamp = start + 300,
            items = listOf(
                ImportRunningRecordItemRequest(500, 150, 160, 130, orderIndex = 0, startTimestamp = start, endTimestamp = start + 150),
                ImportRunningRecordItemRequest(500, 150, 180, 150, orderIndex = 1, startTimestamp = start + 150, endTimestamp = start + 300)
            )))))
        assertEquals(listOf<Short>(130, 150), saved.map { it.heartRate })
        assertEquals(listOf<Short>(160, 180), saved.map { it.cadence })
        assertEquals(0, result.awardedPoint)
    }

    @Test
    fun `an imported workout retains all 5001 GPS points in their original order`() {
        val records = mock(RunningRecordRepository::class.java)
        val items = mock(RunningRecordItemRepository::class.java)
        val configuration = mock(UserConfigurationService::class.java)
        val validation = RunningValidationService()
        val acceptance = mock(RunningAcceptanceService::class.java)
        val service = RunningImportService(records, items, configuration, acceptance, validation, mock(RunningRecordService::class.java), mock(NotificationService::class.java))
        val existing = RunningRecord(id = 10, user = User(id = 1), source = RunningRecordSource.HEALTH)
        `when`(configuration.getOrCreateForUpdate(1)).thenReturn(UserConfiguration(1, true))
        `when`(records.findByUserIdAndSourceAndExternalId(1, RunningRecordSource.HEALTH, "workout")).thenReturn(existing)
        val start = OffsetDateTime.parse("2026-10-01T00:00:00Z").toEpochSecond()
        val end = start + 600
        val points = (0..5000).map { index ->
            RunningRecordItemGpsPointDto(37.0 + index * 0.000001, 127.0, start * 1000 + index, 3.0, 0.0, null)
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
