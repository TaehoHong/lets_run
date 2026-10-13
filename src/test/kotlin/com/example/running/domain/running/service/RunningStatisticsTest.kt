package com.example.running.domain.running.service

import com.example.running.domain.running.entity.RunningRecord
import com.example.running.domain.running.enums.RunningStatisticType
import com.example.running.domain.running.service.dto.RunningStatistics
import com.example.running.domain.user.entity.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneId

class RunningStatisticsTest {
    @Test
    fun `bucket and summary pace use seconds per cumulative meter independently of order`() {
        val first = RunningRecord(user = User(7), distance = 1000, durationSec = 300, startDatetime = OffsetDateTime.parse("2026-01-01T00:00:00Z"))
        val second = RunningRecord(user = User(7), distance = 2000, durationSec = 600, startDatetime = first.startDatetime)
        val statistics = RunningStatistics(listOf(first, second), RunningStatisticType.MONTHLY)
        val reversed = RunningStatistics(listOf(second, first), RunningStatisticType.MONTHLY)
        assertEquals(0.3, statistics.averagePaceSec)
        assertEquals(0.3, statistics.chartData.single().paceSec)
        assertEquals(0.3, reversed.chartData.single().paceSec)
    }

    @Test
    fun `time zone changes start-date buckets without moving the record instant`() {
        val record = RunningRecord(user = User(7), distance = 1000, durationSec = 300,
            startDatetime = OffsetDateTime.parse("2026-01-01T20:00:00Z"),
            endDatetime = OffsetDateTime.parse("2026-01-02T03:00:00Z"))
        assertEquals("2026-01-01", RunningStatistics(listOf(record), RunningStatisticType.MONTHLY).chartData.single().datetime)
        assertEquals("2026-01-02", RunningStatistics(listOf(record), RunningStatisticType.MONTHLY, ZoneId.of("Asia/Kolkata")).chartData.single().datetime)
        assertEquals("2026-01-01", RunningStatistics(listOf(record), RunningStatisticType.MONTHLY, ZoneId.of("America/New_York")).chartData.single().datetime)
        assertEquals(OffsetDateTime.parse("2026-01-01T20:00:00Z"), record.startDatetime)
    }

    @Test
    fun `DST transition instants stay on the local start day`() {
        val first = RunningRecord(user = User(7), startDatetime = OffsetDateTime.parse("2026-03-08T06:30:00Z"))
        val second = RunningRecord(user = User(7), startDatetime = OffsetDateTime.parse("2026-03-08T07:30:00Z"))
        val statistics = RunningStatistics(listOf(first, second), RunningStatisticType.MONTHLY, ZoneId.of("America/New_York"))
        assertEquals("2026-03-08", statistics.chartData.single().datetime)
        assertEquals(0.0, statistics.chartData.single().paceSec)
    }

    @Test
    fun `empty statistics return finite zero pace`() {
        assertEquals(0.0, RunningStatistics(emptyList(), RunningStatisticType.MONTHLY).averagePaceSec)
    }
}
