package com.example.running.domain.running.service

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.entity.RunningRecordItem
import com.example.running.domain.running.repository.RunningRecordItemRepository
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.RunningRecordItemDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.*
import com.example.running.exception.ApiException
import java.time.OffsetDateTime

class RunningRecordItemServiceTest {
    private val records = mock(RunningRecordRepository::class.java)
    private val items = mock(RunningRecordItemRepository::class.java)
    private val service = RunningRecordItemService(items, records)
    private val stored = mutableListOf<RunningRecordItem>()
    private val timestamp = OffsetDateTime.parse("2026-10-01T00:00:00Z")

    private fun item(distance: Int = 10) = RunningRecordItemDto(
        distance, 5, 170, 0, 0, 0, 0, timestamp, timestamp.plusSeconds(5)
    )

    private fun allowOwner() {
        `when`(records.findOwnedForUpdate(10, 1)).thenReturn(RunningRecord(id = 10))
        `when`(items.findAllByRunningRecord_IdOrderByOrderIndexAsc(10)).thenAnswer { stored.toList() }
        doAnswer { call -> stored.addAll(call.getArgument<List<RunningRecordItem>>(0)); null }
            .`when`(items).saveInBatch(anyList())
    }

    @Test
    fun `replaying an acknowledged or response-lost batch preserves one stored segment`() {
        allowOwner()
        service.save(1, 10, listOf(item()))
        service.save(1, 10, listOf(item()))
        assertEquals(1, stored.size)
        assertEquals(10, stored.single().distance)
    }

    @Test
    fun `same order with changed payload keeps existing append semantics`() {
        allowOwner()
        service.save(1, 10, listOf(item()))
        service.save(1, 10, listOf(item(11)))
        assertEquals(listOf(10, 11), stored.map { it.distance })
    }

    @Test
    fun `retry compares stored second precision and instant across offsets`() {
        allowOwner()
        stored.add(RunningRecordItem(
            runningRecord = RunningRecord(id = 10), distance = 10, durationSec = 5,
            cadence = 170, orderIndex = 0, startDatetime = timestamp,
            endDatetime = timestamp.plusSeconds(5),
        ))
        service.save(1, 10, listOf(RunningRecordItemDto(
            10, 5, 170, 0, 0, 0, 0,
            timestamp.withOffsetSameInstant(java.time.ZoneOffset.ofHours(9)).plusNanos(123000000),
            timestamp.plusSeconds(5).withOffsetSameInstant(java.time.ZoneOffset.ofHours(9)).plusNanos(123000000),
        )))
        assertEquals(1, stored.size)
    }

    @Test
    fun `foreign record cannot insert segments`() {
        assertThrows(ApiException::class.java) { service.save(2, 10, listOf(item())) }
        verifyNoInteractions(items)
    }
}
