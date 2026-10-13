package com.example.running.domain.point.service

import com.example.running.domain.point.entity.PointType
import com.example.running.domain.point.entity.UserPointHistory
import com.example.running.domain.point.repository.UserPointHistoryRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime

class UserPointHistoryServicePaginationTest {
    private val repository = mock(UserPointHistoryRepository::class.java)
    private val service = UserPointHistoryService(repository)

    @Test
    fun `hasNext uses the last returned id with the same filter and time boundary`() {
        val start = OffsetDateTime.parse("2026-01-01T00:00:00Z")
        val last = UserPointHistory(id = 90, userId = 7, pointType = PointType(1), point = 10)
        `when`(repository.findAll(7, 100, true, start, 30)).thenReturn(listOf(last))
        `when`(repository.existsBy(7, 90, true, start)).thenReturn(false)
        `when`(repository.existsBy(7, 100, true, start)).thenReturn(true)
        val result = service.search(7, true, 100, 30, start)
        assertEquals(90L, result.cursor)
        assertFalse(result.hasNext)
    }

    @Test
    fun `empty page has no next page`() {
        `when`(repository.findAll(7, null, null, null, 30)).thenReturn(emptyList())
        val result = service.search(7, null, null, 30, null)
        assertNull(result.cursor)
        assertFalse(result.hasNext)
    }
}
