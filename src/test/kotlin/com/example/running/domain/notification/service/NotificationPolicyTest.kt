package com.example.running.domain.notification.service

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPolicyTest {
    @Test
    fun `completion schedules 48 hours and first generation schedules 72 hours from the same run`() {
        val anchor = Instant.parse("2026-10-10T03:00:00Z")
        val first = NotificationPolicy.firstReminderAt(anchor)
        assertEquals(Instant.parse("2026-10-12T03:00:00Z"), first)
        assertEquals(Instant.parse("2026-10-13T03:00:00Z"), NotificationPolicy.followingReminderAt(anchor, first))
    }

    @Test
    fun `second generation clears the next date`() {
        val anchor = Instant.parse("2026-10-10T03:00:00Z")
        assertEquals(null, NotificationPolicy.followingReminderAt(anchor, Instant.parse("2026-10-13T03:00:00Z")))
    }

    @Test
    fun `new run invalidates both queued reminders from an older run`() {
        val anchor = Instant.parse("2026-10-10T03:00:00Z")
        val firstKey = NotificationPolicy.reminderKey(anchor, Instant.parse("2026-10-12T03:00:00Z"))
        val secondKey = NotificationPolicy.reminderKey(anchor, Instant.parse("2026-10-13T03:00:00Z"))
        assertTrue(NotificationPolicy.isCurrentReminder(firstKey, anchor))
        assertTrue(NotificationPolicy.isCurrentReminder(secondKey, anchor))
        val newRun = Instant.parse("2026-10-12T15:00:00Z")
        assertFalse(NotificationPolicy.isCurrentReminder(firstKey, newRun))
        assertFalse(NotificationPolicy.isCurrentReminder(secondKey, newRun))
    }
}
