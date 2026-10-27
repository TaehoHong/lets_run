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
    // No @Transactional: claim and final eligibility gate commit before the external requests.
    fun process(candidates: List<PushCandidate>, now: Instant = Instant.now()) {
        if (candidates.isEmpty()) return
        require(candidates.size <= 100)
        val lease = UUID.randomUUID().toString()
        queue.claim(candidates, lease, now)
        val jobs = queue.claimed(candidates.map { it.id }, lease, now)
        if (jobs.isEmpty()) return
        val outcomes = mutableListOf<PushOutcome>()
        val sendJobs = jobs.filter { it.ticketId == null }
        val content = repository.pushContent(sendJobs, now)
        val prepared = sendJobs.filter { job ->
            when {
                job.attemptCount >= 5 -> {
                    outcomes += PushOutcome(job.id, lease, "FAILED", now, "ATTEMPTS_EXHAUSTED")
                    false
                }
                !job.validBinding || job.token == null || !job.configuration.allows(job.type) ||
                    (job.type == NotificationType.RUNNING_REMINDER && (job.anchor == null ||
                        !NotificationPolicy.isCurrentReminder(job.sourceKey, job.anchor))) || content[job.id] == null -> {
                    outcomes += PushOutcome(job.id, lease, "CANCELLED", now)
                    false
                }
                else -> true
            }
        }
        val receiptJobs = jobs.filter { it.ticketId != null }
        val deliveryLease = UUID.randomUUID().toString()
        val deliveryJobs = prepared + receiptJobs
        val winners = if (deliveryJobs.isEmpty()) emptySet() else queue.beginDelivery(deliveryJobs, lease, deliveryLease)
        val sending = prepared.filter { it.id in winners }
        prepared.filter { it.id !in winners }.forEach {
            outcomes += PushOutcome(it.id, lease, "PENDING", Instant.now().plusSeconds(1))
        }
        if (sending.isNotEmpty()) {
            val results = provider.send(sending.map { job ->
                val text = content.getValue(job.id)
                PushMessage(job.token!!, text.title, text.body, job.notificationId)
            })
            val completed = Instant.now()
            sending.zip(results).forEach { (job, result) ->
                outcomes += when {
                    result.ticket != null -> PushOutcome(job.id, deliveryLease, "RECEIPT_PENDING", completed.plusSeconds(900), ticket = result.ticket)
                    result.error in sendRetryErrors && job.attemptCount + 1 < 5 ->
                        PushOutcome(job.id, deliveryLease, "PENDING", completed.plusSeconds(30L shl job.attemptCount), result.error)
                    else -> PushOutcome(job.id, deliveryLease, "FAILED", completed, result.error ?: "PROVIDER_RESPONSE")
                }
            }
        }
        val polling = receiptJobs.filter { it.id in winners }
        if (polling.isNotEmpty()) {
            val results = provider.receipts(polling.map { it.ticketId!! }.distinct())
            val completed = Instant.now()
            polling.forEach { job ->
                val result = results.getValue(job.ticketId!!)
                outcomes += when {
                    result.pending && completed < job.created.plusSeconds(48 * 3600) ->
                        PushOutcome(job.id, deliveryLease, "RECEIPT_PENDING", completed.plusSeconds(900))
                    result.pending -> PushOutcome(job.id, deliveryLease, "FAILED", completed, "RECEIPT_UNAVAILABLE")
                    result.error == null -> PushOutcome(job.id, deliveryLease, "PROVIDER_ACCEPTED", completed)
                    result.error in receiptRetryErrors && completed < job.created.plusSeconds(48 * 3600) ->
                        PushOutcome(job.id, deliveryLease, "RECEIPT_PENDING", completed.plusSeconds(900), result.error)
                    else -> PushOutcome(job.id, deliveryLease, "FAILED", completed, result.error)
                }
            }
        }
        queue.finish(outcomes)
    }

    private val sendRetryErrors = setOf("PROVIDER_UNAVAILABLE", "PROVIDER_RESPONSE", "MessageRateExceeded", "INTERRUPTED")
    private val receiptRetryErrors = setOf("PROVIDER_UNAVAILABLE", "MessageRateExceeded", "INTERRUPTED")
}
