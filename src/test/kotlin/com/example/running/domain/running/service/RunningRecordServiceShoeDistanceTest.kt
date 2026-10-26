package com.example.running.domain.running.service

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.repository.RunningRecordRepository
import com.example.running.domain.running.service.dto.RunningRecordUpdateDto
import com.example.running.domain.shoe.entity.Shoe
import com.example.running.domain.shoe.repository.ShoeRepository
import com.example.running.domain.shoe.service.ShoeService
import com.example.running.domain.user.entity.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class RunningRecordServiceShoeDistanceTest {

    private val runningRecordRepository = mock(RunningRecordRepository::class.java)
    private val shoeRepository = mock(ShoeRepository::class.java)
    private val service = RunningRecordService(runningRecordRepository, ShoeService(shoeRepository),
        mock(com.example.running.domain.user.service.UserConfigurationService::class.java), RunningValidationService())
    private val record = RunningRecord(id = 101, user = User(id = 7), shoe = Shoe(id = 11))
    private val persistedDistances = mutableMapOf(11L to 2000, 12L to 1000)

    @BeforeEach
    fun setUp() {
        `when`(runningRecordRepository.findOwnedForUpdate(101, 7)).thenReturn(record)
        // Repository boundaries are replaced; both service implementations run for real.
        doAnswer { invocation ->
            persistedDistances[invocation.getArgument<Long>(0)] = invocation.getArgument<Int>(1)
            1L
        }.`when`(shoeRepository).updateTotalDistanceById(anyLong(), anyInt())
    }

    @Test
    fun `local Garmin edits retain awarded points and freeze the original league contribution`() {
        record.source = com.example.running.domain.running.enums.RunningRecordSource.GARMIN
        record.distance = 1500
        record.endDatetime = java.time.OffsetDateTime.parse("2026-10-01T12:00:00Z")
        record.durationSec = 1800
        record.startDatetime = record.endDatetime!!.minusMinutes(30)
        record.finishAcceptance(com.example.running.domain.running.enums.RunningAcceptanceStatus.ACCEPTED, true)
        record.pointAwarded = true
        record.shoe = null
        service.updateRecord(RunningRecordUpdateDto(userId = 7, runningRecordId = 101, distance = 9000,
            endDatetime = java.time.OffsetDateTime.parse("2026-10-02T12:00:00Z")))
        assertEquals(9000, record.distance)
        assertEquals(1500, record.leagueDistanceOverride)
        assertEquals(java.time.OffsetDateTime.parse("2026-10-01T12:00:00Z"), record.leagueEndDatetimeOverride)
        assertTrue(record.pointAwarded)
        org.junit.jupiter.api.Assertions.assertTrue(record.pointEligible)
    }

    @Test
    fun `saving a draft refreshes its linked shoe distance without completing it`() {
        `when`(runningRecordRepository.findAllDistanceByShoeId(11)).thenReturn(listOf(2000, 5000))

        val updated = service.updateRecord(
            RunningRecordUpdateDto(userId = 7, runningRecordId = 101, distance = 5000)
        )

        org.junit.jupiter.api.Assertions.assertFalse(updated.isEnd)
        assertEquals(5000, updated.distance)
        assertEquals(11L, updated.shoe?.id)
        assertEquals(7000, persistedDistances[11L])
        assertEquals(1000, persistedDistances[12L])
    }

    @Test
    fun `retrying the same end request does not add the distance twice`() {
        `when`(runningRecordRepository.findAllDistanceByShoeId(11)).thenReturn(listOf(2000, 5000))
        val request = RunningRecordUpdateDto(userId = 7, runningRecordId = 101, distance = 5000)

        service.updateRecord(request)
        assertEquals(7000, persistedDistances[11L])
        service.updateRecord(request)

        assertEquals(7000, persistedDistances[11L])
        assertEquals(5000, record.distance)
    }

    @Test
    fun `saving a draft without a linked shoe leaves shoe distances unchanged`() {
        record.shoe = null

        val updated = service.updateRecord(
            RunningRecordUpdateDto(userId = 7, runningRecordId = 101, distance = 5000)
        )

        org.junit.jupiter.api.Assertions.assertFalse(updated.isEnd)
        assertEquals(5000, updated.distance)
        assertNull(updated.shoe)
        assertEquals(mapOf(11L to 2000, 12L to 1000), persistedDistances)
    }

    @Test
    fun `an explicitly supplied shoe id still refreshes the selected shoe distance`() {
        `when`(runningRecordRepository.findAllDistanceByShoeId(12)).thenReturn(listOf(1000, 5000))

        val updated = service.updateRecord(
            RunningRecordUpdateDto(userId = 7, runningRecordId = 101, shoeId = 12, distance = 5000)
        )

        assertEquals(12L, updated.shoe?.id)
        assertEquals(6000, persistedDistances[12L])
        assertEquals(2000, persistedDistances[11L])
    }
}
