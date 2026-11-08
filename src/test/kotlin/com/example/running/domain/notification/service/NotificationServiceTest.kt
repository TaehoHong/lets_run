package com.example.running.domain.notification.service

import com.example.running.domain.notification.repository.*
import com.example.running.exception.ApiException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotificationServiceTest {
    private val repository = mock(NotificationRepository::class.java)
    private val service = NotificationService(repository)
    private val anchor = Instant.parse("2026-10-10T03:00:00Z")
    private val first = ReminderState(7, anchor, Instant.parse("2026-10-12T03:00:00Z"))
    private val second = Instant.parse("2026-10-13T03:00:00Z")

    @Test
    fun `a losing generator creates no inbox or push job`() {
        `when`(repository.advanceReminder(first, second)).thenReturn(false)
        assertFalse(service.generateReminder(first, first.next))
        verify(repository).advanceReminder(first, second)
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun `push opt out still stores the reminder in the inbox`() {
        `when`(repository.advanceReminder(first, second)).thenReturn(true)
        `when`(repository.isActive(7)).thenReturn(true)
        `when`(repository.configuration(7)).thenReturn(NotificationConfiguration())
        `when`(repository.languageId("ko")).thenReturn(1)
        `when`(repository.languageId("ja")).thenReturn(2)
        val key = NotificationPolicy.reminderKey(anchor, first.next)
        `when`(repository.createNotification(7, NotificationType.RUNNING_REMINDER, key, null, null, first.next)).thenReturn(99)

        assertTrue(service.generateReminder(first, first.next))
        verify(repository).createNotification(7, NotificationType.RUNNING_REMINDER, key, null, null, first.next)
        verify(repository, never()).devices(anyLong())
    }

    @Test
    fun `second reminder clears schedule even when scheduler is late`() {
        val state = first.copy(next = second)
        `when`(repository.advanceReminder(state, null)).thenReturn(true)
        assertTrue(service.generateReminder(state, second.plusSeconds(3600)))
        verify(repository).advanceReminder(state, null)
    }

    @Test
    fun `nothing is claimed before the scheduled date`() {
        assertFalse(service.generateReminder(first, first.next.minusSeconds(1)))
        verifyNoInteractions(repository)
    }

    @Test
    fun `enabling push needs a valid zone`() {
        `when`(repository.isActive(7, forUpdate = true)).thenReturn(true)
        assertFailsWith<ApiException> { service.configure(7, NotificationConfiguration(pushEnabled = true)) }
        assertFailsWith<ApiException> { service.configure(7, NotificationConfiguration(timezone = "invalid/zone")) }
        verify(repository, never()).saveConfiguration(anyLong(), any(NotificationConfiguration::class.java) ?: NotificationConfiguration())
    }

    @Test
    fun `a partial preference change returns the merged configuration without replacing other preferences`() {
        val change = NotificationConfigurationPatch(leaguePushEnabled = false)
        val merged = NotificationConfiguration(pushEnabled = true, leaguePushEnabled = false,
            reminderPushEnabled = false, timezone = "Asia/Seoul")
        `when`(repository.isActive(7, forUpdate = true)).thenReturn(true)
        `when`(repository.configuration(7)).thenReturn(merged)

        assertEquals(merged, service.patchConfiguration(7, change))

        verify(repository).patchConfiguration(7, change)
        verify(repository, never()).saveConfiguration(anyLong(), any(NotificationConfiguration::class.java) ?: NotificationConfiguration())
    }

    @Test
    fun `a partial update rejects an invalid timezone before changing preferences`() {
        `when`(repository.isActive(7, forUpdate = true)).thenReturn(true)
        assertEquals(400, assertFailsWith<ApiException> {
            service.patchConfiguration(7, NotificationConfigurationPatch(pushEnabled = true, timezone = "invalid/zone"))
        }.status.value())
        verify(repository, never()).patchConfiguration(anyLong(), any(NotificationConfigurationPatch::class.java) ?: NotificationConfigurationPatch())
    }

    @Test
    fun `a partial update cannot enable push without a saved timezone`() {
        val change = NotificationConfigurationPatch(pushEnabled = true)
        `when`(repository.isActive(7, forUpdate = true)).thenReturn(true)
        `when`(repository.configuration(7)).thenReturn(NotificationConfiguration(pushEnabled = true))

        assertEquals(400, assertFailsWith<ApiException> { service.patchConfiguration(7, change) }.status.value())
    }

    @Test
    fun `non admins cannot author or publish notices`() {
        assertEquals(403, assertFailsWith<ApiException> { service.createNotice(7, emptyList()) }.status.value())
        assertEquals(403, assertFailsWith<ApiException> { service.publishNotice(7, 1) }.status.value())
        verify(repository, never()).createNotice()
    }

    @Test
    fun `repeating publication does not fan out twice`() {
        `when`(repository.isAdmin(7)).thenReturn(true)
        `when`(repository.noticeStatus(1)).thenReturn("PUBLISHED")
        service.publishNotice(7, 1)
        verify(repository, never()).activeUserIds(anyLong())
    }
}
