package com.example.running.domain.notification.service

import com.example.running.domain.notification.repository.*
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class NotificationPushWorker(
    private val queue: NotificationPushRepository,
    private val repository: NotificationRepository,
    private val provider: ExpoPushClient,
) {
    // No @Transactional: each CAS commits before the external request.
    fun process(candidate: PushCandidate, now: Instant = Instant.now()) {
        val lease = UUID.randomUUID().toString()
        if (!queue.claim(candidate, lease, now)) return
        val job = queue.claimed(candidate.id, lease, now) ?: return
        if (job.ticketId != null) {
            val result = provider.receipt(job.ticketId)
            when {
                result.pending && now < job.created.plusSeconds(48 * 3600) ->
                    queue.finish(job.id, lease, "RECEIPT_PENDING", now.plusSeconds(900))
                result.pending -> queue.finish(job.id, lease, "FAILED", now, "RECEIPT_UNAVAILABLE")
                result.error == null -> queue.finish(job.id, lease, "PROVIDER_ACCEPTED", now)
                result.error in setOf("PROVIDER_UNAVAILABLE", "MessageRateExceeded", "INTERRUPTED") && now < job.created.plusSeconds(48 * 3600) ->
                    queue.finish(job.id, lease, "RECEIPT_PENDING", now.plusSeconds(900), result.error)
                else -> fail(job, lease, result.error, now)
            }
            return
        }
        if (job.attemptCount >= 5) {
            queue.finish(job.id, lease, "FAILED", now, "ATTEMPTS_EXHAUSTED")
            return
        }
        val configuration = repository.configuration(job.userId)
        val anchor = if (job.type == NotificationType.RUNNING_REMINDER) repository.reminderAnchor(job.userId) else null
        if (!job.validBinding || job.token == null || !configuration.allows(job.type) ||
            (job.type == NotificationType.RUNNING_REMINDER && (anchor == null || !NotificationPolicy.isCurrentReminder(job.sourceKey, anchor)))) {
            queue.finish(job.id, lease, "CANCELLED", now)
            return
        }
        val content = repository.inbox(job.userId, job.language, null, 1, now, job.notificationId).firstOrNull()
        if (content == null) {
            queue.finish(job.id, lease, "CANCELLED", now)
            return
        }
        if (!queue.beginSend(job.id, lease, anchor)) {
            // Re-evaluate changed binding, settings or anchor on the next poll.
            queue.finish(job.id, lease, "PENDING", now.plusSeconds(1))
            return
        }
        val result = provider.send(job.token, content.title, content.body, job.notificationId)
        when {
            result.ticket != null -> queue.finish(job.id, lease, "RECEIPT_PENDING", now.plusSeconds(900), ticket = result.ticket)
            result.error in setOf("PROVIDER_UNAVAILABLE", "PROVIDER_RESPONSE", "MessageRateExceeded", "INTERRUPTED") && job.attemptCount + 1 < 5 ->
                queue.finish(job.id, lease, "PENDING", now.plusSeconds(30L shl job.attemptCount), result.error)
            else -> fail(job, lease, result.error, now)
        }
    }

    private fun fail(job: PushJob, lease: String, error: String?, now: Instant) {
        if (queue.finish(job.id, lease, "FAILED", now, error ?: "PROVIDER_RESPONSE") && error == "DeviceNotRegistered") {
            repository.disableDevice(job.deviceId, job.version)
        }
    }
}
