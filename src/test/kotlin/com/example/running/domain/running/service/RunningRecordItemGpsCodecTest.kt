package com.example.running.domain.running.service

import com.example.running.domain.running.service.dto.RunningRecordItemGpsPointDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RunningRecordItemGpsCodecTest {

    @Test
    fun `encode returns null when points are empty`() {
        val encoded = RunningRecordItemGpsCodec.encode(emptyList())
        assertNull(encoded)
    }

    @Test
    fun `encode and decode preserves gps points`() {
        val points = listOf(
            RunningRecordItemGpsPointDto(
                latitude = 37.5665,
                longitude = 126.9780,
                timestampMs = 1739600000000,
                speed = 2.1,
                altitude = 40.0,
                accuracy = 5.0,
            ),
            RunningRecordItemGpsPointDto(
                latitude = 37.5670,
                longitude = 126.9788,
                timestampMs = 1739600002500,
                speed = 2.3,
                altitude = 40.5,
                accuracy = null,
            ),
        )

        val encoded = RunningRecordItemGpsCodec.encode(points)
        val decoded = RunningRecordItemGpsCodec.decode(encoded)

        assertEquals(points, decoded)
    }

    @Test
    fun `decode invalid json returns empty list`() {
        val decoded = RunningRecordItemGpsCodec.decode("{invalid json")
        assertEquals(emptyList<RunningRecordItemGpsPointDto>(), decoded)
    }
}
