package com.example.running.domain.notification.service

import com.example.running.domain.notification.repository.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.time.Instant

class NotificationPushWorkerTest {
    private fun <T> equal(value: T): T { eq(value); return value }
    private fun anyInstant(): Instant = any(Instant::class.java) ?: Instant.EPOCH
    private val queue = mock(NotificationPushRepository::class.java)
    private val repository = mock(NotificationRepository::class.java)
    private val provider = mock(ExpoPushClient::class.java)
    private val worker = NotificationPushWorker(queue, repository, provider)
    private val candidate = PushCandidate(1, "PENDING")
    private val daytime = Instant.parse("2026-10-13T12:00:00Z")
    private val job = PushJob(1, 2, 7, 3, 4, "RUNNING_REMINDER:0:48", NotificationType.RUNNING_REMINDER,
        "ExpoPushToken[test]", "ko", null, 0, Instant.now(), true)

    @BeforeEach
    fun setup() {
        `when`(queue.claim(equal(candidate), anyString(), anyInstant())).thenReturn(true)
        `when`(queue.claimed(equal(1L), anyString(), anyInstant())).thenReturn(job)
        `when`(repository.configuration(7)).thenReturn(NotificationConfiguration(pushEnabled = true, timezone = "UTC"))
    }

    @Test
    fun `claim loser never contacts provider`() {
        `when`(queue.claim(equal(candidate), anyString(), anyInstant())).thenReturn(false)
        worker.process(candidate)
        verifyNoInteractions(provider, repository)
    }

    @Test
    fun `new completed run cancels old queued reminder`() {
        `when`(repository.reminderAnchor(7)).thenReturn(Instant.now())
        worker.process(candidate)
        verifyNoInteractions(provider)
        verify(queue).finish(equal(1L), anyString(), equal("CANCELLED"), anyInstant(), isNull(), isNull())
        verify(queue, never()).beginSend(anyLong(), anyString(), any())
    }

    @Test
    fun `rebound device cancels queued push without using a send attempt`() {
        `when`(queue.claimed(equal(1L), anyString(), anyInstant())).thenReturn(job.copy(validBinding = false, type = NotificationType.NOTICE))
        worker.process(candidate)
        verifyNoInteractions(provider)
        verify(queue, never()).beginSend(anyLong(), anyString(), any())
    }

    @Test
    fun `receipt acceptance is recorded without resending`() {
        val receiptJob = job.copy(ticketId = "ticket")
        `when`(queue.claimed(equal(1L), anyString(), anyInstant())).thenReturn(receiptJob)
        `when`(provider.receipt("ticket")).thenReturn(PushProviderResult())
        worker.process(candidate)
        verify(queue).finish(equal(1L), anyString(), equal("PROVIDER_ACCEPTED"), anyInstant(), isNull(), isNull())
        verify(provider, never()).send(anyString(), anyString(), anyString(), anyLong())
    }

    @Test
    fun `lost lease before send never contacts provider`() {
        noticeJob()
        `when`(queue.beginSend(equal(1L), anyString(), any())).thenReturn(false)
        worker.process(candidate, daytime)
        verifyNoInteractions(provider)
    }

    @Test
    fun `transient provider failure retries before fifth actual send`() {
        noticeJob(attempts = 3)
        `when`(provider.send(anyString(), anyString(), anyString(), anyLong()))
            .thenReturn(PushProviderResult(error = "PROVIDER_UNAVAILABLE"))
        worker.process(candidate, daytime)
        verify(provider).send("ExpoPushToken[test]", "Notice", "Body", 2L)
        verify(queue).finish(equal(1L), anyString(), equal("PENDING"),
            equal(Instant.parse("2026-10-13T12:04:00Z")), equal("PROVIDER_UNAVAILABLE"), isNull())
    }

    @Test
    fun `fifth actual send failure is terminal`() {
        noticeJob(attempts = 4)
        `when`(provider.send(anyString(), anyString(), anyString(), anyLong()))
            .thenReturn(PushProviderResult(error = "PROVIDER_UNAVAILABLE"))
        worker.process(candidate, daytime)
        verify(provider).send("ExpoPushToken[test]", "Notice", "Body", 2L)
        verify(queue).finish(equal(1L), anyString(), equal("FAILED"), equal(daytime),
            equal("PROVIDER_UNAVAILABLE"), isNull())
    }

    @Test
    fun `exhausted attempts never cause a sixth send`() {
        noticeJob(attempts = 5)
        worker.process(candidate, daytime)
        verifyNoInteractions(provider)
        verify(queue, never()).beginSend(anyLong(), anyString(), any())
        verify(queue).finish(equal(1L), anyString(), equal("FAILED"), equal(daytime),
            equal("ATTEMPTS_EXHAUSTED"), isNull())
    }

    @Test
    fun `unregistered device is disabled only after valid lease finishes failure`() {
        noticeJob()
        `when`(provider.send(anyString(), anyString(), anyString(), anyLong()))
            .thenReturn(PushProviderResult(error = "DeviceNotRegistered"))
        `when`(queue.finish(equal(1L), anyString(), equal("FAILED"), equal(daytime),
            equal("DeviceNotRegistered"), isNull())).thenReturn(true)
        worker.process(candidate, daytime)
        verify(repository).disableDevice(3L, 4L)
    }

    @Test
    fun `expired worker cannot disable device after unregistered response`() {
        noticeJob()
        `when`(provider.send(anyString(), anyString(), anyString(), anyLong()))
            .thenReturn(PushProviderResult(error = "DeviceNotRegistered"))
        worker.process(candidate, daytime)
        verify(repository, never()).disableDevice(anyLong(), anyLong())
    }

    private fun noticeJob(attempts: Int = 0) {
        `when`(queue.claimed(equal(1L), anyString(), anyInstant()))
            .thenReturn(job.copy(type = NotificationType.NOTICE, attemptCount = attempts))
        `when`(repository.inbox(7L, "ko", null, 1, daytime, 2L))
            .thenReturn(listOf(InboxNotification(2L, NotificationType.NOTICE, "Notice", "Body", null, null, daytime)))
        `when`(queue.beginSend(equal(1L), anyString(), any())).thenReturn(true)
    }
}
