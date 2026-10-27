package com.example.running.domain.notification.service

import com.example.running.domain.notification.repository.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.time.Instant
import kotlin.test.*

class NotificationPushWorkerTest {
    private fun anyInstant(): Instant = any(Instant::class.java) ?: Instant.EPOCH
    private val queue = mock(NotificationPushRepository::class.java)
    private val repository = mock(NotificationRepository::class.java)
    private val provider = mock(ExpoPushClient::class.java)
    private val worker = NotificationPushWorker(queue, repository, provider)
    private val candidates = listOf(PushCandidate(1, "PENDING"))
    private val daytime = Instant.parse("2026-10-13T12:00:00Z")
    private val job = PushJob(1, 2, 7, 3, 4, "RUNNING_REMINDER:0:48", NotificationType.RUNNING_REMINDER,
        "ExpoPushToken[test]", "ko", null, 0, Instant.now(), true,
        NotificationConfiguration(pushEnabled = true, timezone = "UTC"), Instant.EPOCH)
    private var finished = emptyList<PushOutcome>()

    @BeforeEach
    fun setup() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(listOf(job))
        `when`(queue.beginDelivery(anyList(), anyString(), anyString())).thenReturn(setOf(1L))
        doAnswer { call -> finished = call.getArgument(0); null }.`when`(queue).finish(anyList())
    }

    @Test
    fun `claim loser never contacts provider`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(emptyList())
        worker.process(candidates)
        verifyNoInteractions(provider, repository)
    }

    @Test
    fun `new completed run cancels old queued reminder`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(listOf(job.copy(anchor = Instant.now())))
        worker.process(candidates)
        verifyNoInteractions(provider)
        assertEquals("CANCELLED", finished.single().status)
        verify(queue, never()).beginDelivery(anyList(), anyString(), anyString())
    }

    @Test
    fun `rebound device cancels queued push without using a send attempt`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant()))
            .thenReturn(listOf(job.copy(validBinding = false, type = NotificationType.NOTICE)))
        worker.process(candidates)
        verifyNoInteractions(provider)
        verify(queue, never()).beginDelivery(anyList(), anyString(), anyString())
    }

    @Test
    fun `receipt acceptance is recorded without resending`() {
        `when`(queue.claimed(anyList(), anyString(), anyInstant())).thenReturn(listOf(job.copy(ticketId = "ticket")))
        `when`(provider.receipts(listOf("ticket"))).thenReturn(mapOf("ticket" to PushProviderResult()))
        worker.process(candidates)
        assertEquals("PROVIDER_ACCEPTED", finished.single().status)
        verify(provider, never()).send(anyList())
    }

    @Test
    fun `lost lease before send never contacts provider`() {
        noticeJob()
        `when`(queue.beginDelivery(anyList(), anyString(), anyString())).thenReturn(emptySet())
        worker.process(candidates, daytime)
        verifyNoInteractions(provider)
    }

    @Test
    fun `transient provider failure retries before fifth actual send`() {
        noticeJob(attempts = 3)
        `when`(provider.send(anyList())).thenReturn(listOf(PushProviderResult(error = "PROVIDER_UNAVAILABLE")))
        val before = Instant.now()
        worker.process(candidates, daytime)
        verify(provider).send(listOf(PushMessage("ExpoPushToken[test]", "Notice", "Body", 2L)))
        assertEquals("PENDING", finished.single().status)
        assertEquals("PROVIDER_UNAVAILABLE", finished.single().error)
        assertTrue(finished.single().next >= before.plusSeconds(240))
        assertTrue(finished.single().next <= Instant.now().plusSeconds(240))
    }

    @Test
    fun `fifth actual send failure is terminal`() {
        noticeJob(attempts = 4)
        `when`(provider.send(anyList())).thenReturn(listOf(PushProviderResult(error = "PROVIDER_UNAVAILABLE")))
        worker.process(candidates, daytime)
        verify(provider).send(listOf(PushMessage("ExpoPushToken[test]", "Notice", "Body", 2L)))
        assertEquals("FAILED", finished.single().status)
        assertEquals("PROVIDER_UNAVAILABLE", finished.single().error)
    }

    @Test
    fun `exhausted attempts never cause a sixth send`() {
        noticeJob(attempts = 5)
        worker.process(candidates, daytime)
        verifyNoInteractions(provider)
        verify(queue, never()).beginDelivery(anyList(), anyString(), anyString())
        assertEquals("FAILED", finished.single().status)
        assertEquals("ATTEMPTS_EXHAUSTED", finished.single().error)
    }

    @Test
    fun `unregistered response is delegated to atomic queue finish`() {
        noticeJob()
        `when`(provider.send(anyList())).thenReturn(listOf(PushProviderResult(error = "DeviceNotRegistered")))
        worker.process(candidates, daytime)
        assertEquals("FAILED", finished.single().status)
        assertEquals("DeviceNotRegistered", finished.single().error)
        verify(repository, never()).disableDevice(anyLong(), anyLong())
    }

    private fun noticeJob(attempts: Int = 0) {
        `when`(queue.claimed(anyList(), anyString(), anyInstant()))
            .thenReturn(listOf(job.copy(type = NotificationType.NOTICE, attemptCount = attempts)))
        `when`(repository.pushContent(anyList(), anyInstant()))
            .thenReturn(mapOf(1L to NotificationText("ko", "Notice", "Body")))
    }
}
