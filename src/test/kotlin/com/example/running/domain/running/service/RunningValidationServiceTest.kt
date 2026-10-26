package com.example.running.domain.running.service

import com.example.running.domain.running.service.dto.RunningRecordItemGpsPointDto
import com.example.running.exception.RunningValidationException
import com.example.running.exception.RunningValidationReason
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class RunningValidationServiceTest {
    private val service = RunningValidationService()
    private val start = OffsetDateTime.parse("2026-10-01T00:00:00Z")
    private val end = start.plusSeconds(3600)

    @Test
    fun `timestamp conversion rejects storage overflow and unknown timezone with safe metrics reason`() {
        listOf(-1L, Long.MAX_VALUE).forEach { value ->
            assertEquals(RunningValidationReason.RUNNING_INVALID_METRICS,
                assertThrows(RunningValidationException::class.java) { service.timestamp(value) }.reason)
        }
        assertEquals(RunningValidationReason.RUNNING_INVALID_METRICS,
            assertThrows(RunningValidationException::class.java) { service.timestamp(1, "Invalid/Timezone") }.reason)
    }

    @Test
    fun `end equal to ingress and exactly 40 km per hour are accepted`() {
        service.validate(start, end, 40000, 3600, 0, 0, 0, end.toInstant())
    }

    @Test
    fun `end one second beyond original receipt rejects even if processing is later`() {
        val failure = assertThrows(RunningValidationException::class.java) {
            service.validate(start, end, 1000, 3600, 0, 0, 0, end.minusSeconds(1).toInstant())
        }
        assertEquals(RunningValidationReason.RUNNING_FUTURE_END_TIME, failure.reason)
    }

    @Test
    fun `one meter above speed boundary rejects and large duration cannot overflow comparison`() {
        assertEquals(RunningValidationReason.RUNNING_EXCESSIVE_AVERAGE_SPEED,
            assertThrows(RunningValidationException::class.java) {
                service.validate(start, end, 40001, 3600, 0, 0, 0, end.toInstant())
            }.reason)
        assertEquals(RunningValidationReason.RUNNING_INVALID_METRICS,
            assertThrows(RunningValidationException::class.java) {
                service.validate(start, end, Int.MAX_VALUE, Long.MAX_VALUE, 0, 0, 0, end.toInstant())
            }.reason)
    }

    @Test
    fun `positive distance needs positive duration while an empty watch workout remains valid`() {
        assertThrows(RunningValidationException::class.java) {
            service.validate(start, end, 1, 0, 0, 0, 0, end.toInstant())
        }
        assertThrows(RunningValidationException::class.java) {
            service.validate(start, end, 1000, 3601, 0, 0, 0, end.toInstant())
        }
        service.validate(start, start, 0, 0, 255, 255, 0, start.toInstant())
    }

    @Test
    fun `optional GPS must be finite ordered and inside the workout`() {
        val first = RunningRecordItemGpsPointDto(37.0, 127.0, start.toInstant().toEpochMilli(), 2.0, 10.0, null)
        listOf(
            listOf(first.copy(latitude = Double.NaN)),
            listOf(first.copy(longitude = 181.0)),
            listOf(first.copy(speed = Double.POSITIVE_INFINITY)),
            listOf(first.copy(accuracy = -1.0)),
            listOf(first.copy(timestampMs = first.timestampMs - 1)),
            listOf(first.copy(timestampMs = first.timestampMs + 2), first),
        ).forEach { points ->
            assertEquals(RunningValidationReason.RUNNING_INVALID_GPS,
                assertThrows(RunningValidationException::class.java) {
                    service.validate(start, end, 1000, 3600, 0, 0, 0, end.toInstant(), points)
                }.reason)
        }
        service.validate(start, end, 1000, 3600, 0, 0, 0, end.toInstant(), emptyList())
    }
}
