package com.example.running.domain.notification.service

import java.time.Instant
import java.time.temporal.ChronoUnit

object NotificationPolicy {
    fun firstReminderAt(anchor: Instant): Instant = anchor.plus(48, ChronoUnit.HOURS)

    fun followingReminderAt(anchor: Instant, due: Instant): Instant? =
        if (due == firstReminderAt(anchor)) anchor.plus(72, ChronoUnit.HOURS) else null

    fun reminderKey(anchor: Instant, due: Instant): String {
        val hours = ChronoUnit.HOURS.between(anchor, due)
        require(hours in listOf(48L, 72L) && due == anchor.plus(hours, ChronoUnit.HOURS))
        return "RUNNING_REMINDER:${anchor.epochSecond}:$hours"
    }

    fun isCurrentReminder(sourceKey: String, anchor: Instant): Boolean =
        listOf(48L, 72L).any { sourceKey == reminderKey(anchor, anchor.plus(it, ChronoUnit.HOURS)) }
}
